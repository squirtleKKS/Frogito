#include <gtest/gtest.h>

#include <cstring>
#include <filesystem>
#include <fstream>
#include <optional>
#include <string>
#include <system_error>
#include <vector>

#include "bytecode/bytecode_loader.h"

namespace {

void AppendU16(std::vector<std::uint8_t>& out, std::uint16_t value) {
    out.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
    out.push_back(static_cast<std::uint8_t>(value & 0xFF));
}

void AppendU32(std::vector<std::uint8_t>& out, std::uint32_t value) {
    out.push_back(static_cast<std::uint8_t>((value >> 24) & 0xFF));
    out.push_back(static_cast<std::uint8_t>((value >> 16) & 0xFF));
    out.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
    out.push_back(static_cast<std::uint8_t>(value & 0xFF));
}

void AppendF64(std::vector<std::uint8_t>& out, double value) {
    std::uint64_t tmp;
    std::memcpy(&tmp, &value, sizeof(tmp));
    for (int shift = 56; shift >= 0; shift -= 8) {
        out.push_back(static_cast<std::uint8_t>((tmp >> shift) & 0xFF));
    }
}

void AppendString(std::vector<std::uint8_t>& out, const std::string& value) {
    AppendU32(out, static_cast<std::uint32_t>(value.size()));
    out.insert(out.end(), value.begin(), value.end());
}

struct EncodedModule {
    std::vector<std::uint8_t> bytes;
    std::size_t const_offset = 0;
    std::size_t func_offset = 0;
    std::size_t code_offset = 0;
};

EncodedModule EncodeModule(const BytecodeModule& module, std::uint16_t version = 1) {
    EncodedModule encoded;
    std::vector<std::uint8_t> out;
    out.insert(out.end(), {'F', 'R', 'O', 'G'});
    AppendU16(out, version);
    AppendU32(out, static_cast<std::uint32_t>(module.const_pool.size()));
    AppendU32(out, static_cast<std::uint32_t>(module.functions.size()));
    AppendU32(out, static_cast<std::uint32_t>(module.code.size()));

    encoded.const_offset = out.size();
    for (const Constant& c : module.const_pool) {
        out.push_back(static_cast<std::uint8_t>(c.tag));
        switch (c.tag) {
            case ConstTag::kInt:
                AppendU32(out, static_cast<std::uint32_t>(std::get<std::int32_t>(c.value)));
                break;
            case ConstTag::kFloat:
                AppendF64(out, std::get<double>(c.value));
                break;
            case ConstTag::kBool:
                out.push_back(static_cast<std::uint8_t>(std::get<bool>(c.value) ? 1 : 0));
                break;
            case ConstTag::kString:
                AppendString(out, std::get<std::string>(c.value));
                break;
        }
    }

    encoded.func_offset = out.size();
    for (const FunctionInfo& f : module.functions) {
        AppendU32(out, f.name_const_index);
        AppendU16(out, f.param_count);
        AppendU16(out, f.local_count);
        AppendU32(out, f.entry_ip);
        out.push_back(static_cast<std::uint8_t>(f.return_type));
        for (TypeTag pt : f.param_types) {
            out.push_back(static_cast<std::uint8_t>(pt));
        }
    }

    encoded.code_offset = out.size();
    for (const Instruction& ins : module.code) {
        out.push_back(static_cast<std::uint8_t>(ins.op));
        std::uint8_t flags = 0;
        if (ins.has_a) {
            flags |= 1;
        }
        if (ins.has_b) {
            flags |= 2;
        }
        out.push_back(flags);
        if (ins.has_a) {
            AppendU32(out, ins.a);
        }
        if (ins.has_b) {
            AppendU16(out, ins.b);
        }
    }

    encoded.bytes = std::move(out);
    return encoded;
}

std::filesystem::path WriteBytesToTemp(const std::vector<std::uint8_t>& bytes,
                                       const std::string& filename) {
    std::filesystem::path path = std::filesystem::path(testing::TempDir()) / filename;
    std::ofstream file(path, std::ios::binary);
    file.write(reinterpret_cast<const char*>(bytes.data()),
               static_cast<std::streamsize>(bytes.size()));
    file.close();
    return path;
}

std::filesystem::path WriteModuleToTemp(const EncodedModule& encoded, const std::string& filename) {
    return WriteBytesToTemp(encoded.bytes, filename);
}

void RemovePath(const std::filesystem::path& path) {
    std::error_code ec;
    std::filesystem::remove(path, ec);
}

Instruction MakeInstruction(OpCode op,
                            std::optional<std::uint32_t> a = std::nullopt,
                            std::optional<std::uint16_t> b = std::nullopt) {
    Instruction ins;
    ins.op = op;
    if (a) {
        ins.has_a = true;
        ins.a = *a;
    }
    if (b) {
        ins.has_b = true;
        ins.b = *b;
    }
    return ins;
}

BytecodeModule MakeBaselineModule() {
    BytecodeModule module;
    module.const_pool = {
        Constant{ConstTag::kString, std::string("main")},
        Constant{ConstTag::kString, std::string("helper")},
        Constant{ConstTag::kString, std::string("msg")},
        Constant{ConstTag::kInt, static_cast<std::int32_t>(42)},
        Constant{ConstTag::kFloat, 3.5},
        Constant{ConstTag::kBool, true},
    };

    FunctionInfo main_func;
    main_func.name_const_index = 0;
    main_func.param_types = {TypeTag::kInt, TypeTag::kBool};
    main_func.param_count = static_cast<std::uint16_t>(main_func.param_types.size());
    main_func.local_count = 2;
    main_func.entry_ip = 0;
    main_func.return_type = TypeTag::kString;

    FunctionInfo helper_func;
    helper_func.name_const_index = 1;
    helper_func.param_types = {TypeTag::kFloat};
    helper_func.param_count = 1;
    helper_func.local_count = 1;
    helper_func.entry_ip = 3;
    helper_func.return_type = TypeTag::kVoid;

    module.functions = {main_func, helper_func};
    module.code = {
        MakeInstruction(OpCode::kPushConst, 3u),
        MakeInstruction(OpCode::kLoadGlobal, 2u),
        MakeInstruction(OpCode::kCall, 1u, static_cast<std::uint16_t>(1)),
        MakeInstruction(OpCode::kJumpFalse, 6u),
        MakeInstruction(OpCode::kStoreGlobal, 2u),
        MakeInstruction(OpCode::kPop),
        MakeInstruction(OpCode::kPushConst, 5u),
    };

    return module;
}

BytecodeModule MakeMinimalModule() {
    BytecodeModule module;
    module.const_pool = {Constant{ConstTag::kString, std::string("main")}};

    FunctionInfo func;
    func.name_const_index = 0;
    func.param_count = 0;
    func.local_count = 0;
    func.entry_ip = 0;
    func.return_type = TypeTag::kVoid;

    module.functions = {func};
    module.code = {MakeInstruction(OpCode::kPop)};
    return module;
}

std::size_t ReturnTypeOffset(const EncodedModule& encoded) {
    // name_index (4) + param_count (2) + local_count (2) + entry_ip (4)
    return encoded.func_offset + 12;
}

}  // namespace

TEST(BytecodeLoaderTest, LoadsComplexModule) {
    auto module = MakeBaselineModule();
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "complex.frogc");

    BytecodeModule loaded = load_frogc(path);

    ASSERT_EQ(loaded.const_pool.size(), module.const_pool.size());
    EXPECT_EQ(loaded.const_pool[0].tag, ConstTag::kString);
    EXPECT_EQ(std::get<std::string>(loaded.const_pool[0].value), "main");
    EXPECT_EQ(std::get<std::int32_t>(loaded.const_pool[3].value), 42);
    EXPECT_DOUBLE_EQ(std::get<double>(loaded.const_pool[4].value), 3.5);
    EXPECT_EQ(std::get<bool>(loaded.const_pool[5].value), true);

    ASSERT_EQ(loaded.functions.size(), 2u);
    EXPECT_EQ(loaded.functions[0].name_const_index, 0u);
    EXPECT_EQ(loaded.functions[0].param_count, 2u);
    EXPECT_EQ(loaded.functions[0].return_type, TypeTag::kString);
    ASSERT_EQ(loaded.functions[0].param_types.size(), 2u);
    EXPECT_EQ(loaded.functions[0].param_types[0], TypeTag::kInt);
    EXPECT_EQ(loaded.functions[0].param_types[1], TypeTag::kBool);
    EXPECT_EQ(loaded.functions[1].return_type, TypeTag::kVoid);

    ASSERT_EQ(loaded.code.size(), module.code.size());
    EXPECT_EQ(loaded.code[0].op, OpCode::kPushConst);
    EXPECT_EQ(loaded.code[2].op, OpCode::kCall);
    EXPECT_TRUE(loaded.code[2].has_b);
    EXPECT_EQ(loaded.code[2].b, 1u);

    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnBadMagic) {
    auto encoded = EncodeModule(MakeMinimalModule());
    encoded.bytes[0] = 'B';
    auto path = WriteModuleToTemp(encoded, "bad_magic.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnUnsupportedVersion) {
    auto encoded = EncodeModule(MakeMinimalModule(), 2);
    auto path = WriteModuleToTemp(encoded, "bad_version.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidConstTag) {
    auto encoded = EncodeModule(MakeMinimalModule());
    encoded.bytes[encoded.const_offset] = 99;
    auto path = WriteModuleToTemp(encoded, "bad_const_tag.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidTypeTag) {
    auto encoded = EncodeModule(MakeMinimalModule());
    encoded.bytes[ReturnTypeOffset(encoded)] = 99;
    auto path = WriteModuleToTemp(encoded, "bad_type_tag.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidOpcode) {
    auto encoded = EncodeModule(MakeMinimalModule());
    encoded.bytes[encoded.code_offset] = 255;
    auto path = WriteModuleToTemp(encoded, "bad_opcode.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidConstIndex) {
    auto module = MakeBaselineModule();
    module.code[0].a = 99;
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "bad_const_index.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidFunctionNameIndex) {
    auto module = MakeBaselineModule();
    module.functions[0].name_const_index = 50;
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "bad_name_index.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidJumpTarget) {
    auto module = MakeBaselineModule();
    module.code[3].a = 50;
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "bad_jump_target.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsOnInvalidCallTarget) {
    auto module = MakeBaselineModule();
    module.code[2].a = 80;
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "bad_call_target.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}

TEST(BytecodeLoaderTest, ThrowsWhenCallMissingBOperand) {
    auto module = MakeBaselineModule();
    module.code[2].has_b = false;
    auto encoded = EncodeModule(module);
    auto path = WriteModuleToTemp(encoded, "call_missing_b.frogc");
    EXPECT_THROW(load_frogc(path), std::runtime_error);
    RemovePath(path);
}
