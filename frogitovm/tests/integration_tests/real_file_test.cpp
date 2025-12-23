#include <array>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include "bytecode/bytecode_loader.h"
#include "bytecode/bytecode_module.h"
#include "vm/vm.h"

namespace {

constexpr std::array<std::uint8_t, 41> kHelloModule = {
    0x46, 0x52, 0x4F, 0x47, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x04, 0x00,
    0x00, 0x00, 0x01, 0x78, 0x01, 0x00, 0x00, 0x00, 0x46, 0x00,
    0x01, 0x00, 0x00, 0x00, 0x01, 0x04, 0x01, 0x00, 0x00, 0x00,
    0x00,
};

std::filesystem::path WriteRawFile(const std::array<std::uint8_t, 41>& data) {
    std::filesystem::path path = std::filesystem::path("tests") / "integration_tests" / "hello.frogc";
    std::filesystem::create_directories(path.parent_path());
    std::ofstream out(path, std::ios::binary);
    if (!out) {
        throw std::runtime_error("failed to open file for writing");
    }
    out.write(reinterpret_cast<const char*>(data.data()),
              static_cast<std::streamsize>(data.size()));
    out.flush();
    return path;
}

void WriteU16(std::ostream& out, std::uint16_t v) {
    char buf[2];
    buf[0] = static_cast<char>((v >> 8) & 0xFF);
    buf[1] = static_cast<char>(v & 0xFF);
    out.write(buf, 2);
}

void WriteU32(std::ostream& out, std::uint32_t v) {
    char buf[4];
    buf[0] = static_cast<char>((v >> 24) & 0xFF);
    buf[1] = static_cast<char>((v >> 16) & 0xFF);
    buf[2] = static_cast<char>((v >> 8) & 0xFF);
    buf[3] = static_cast<char>(v & 0xFF);
    out.write(buf, 4);
}

void WriteI32(std::ostream& out, std::int32_t v) {
    WriteU32(out, static_cast<std::uint32_t>(v));
}

void WriteF64(std::ostream& out, double d) {
    std::uint64_t bits;
    std::memcpy(&bits, &d, sizeof(double));
    char buf[8];
    buf[0] = static_cast<char>((bits >> 56) & 0xFF);
    buf[1] = static_cast<char>((bits >> 48) & 0xFF);
    buf[2] = static_cast<char>((bits >> 40) & 0xFF);
    buf[3] = static_cast<char>((bits >> 32) & 0xFF);
    buf[4] = static_cast<char>((bits >> 24) & 0xFF);
    buf[5] = static_cast<char>((bits >> 16) & 0xFF);
    buf[6] = static_cast<char>((bits >> 8) & 0xFF);
    buf[7] = static_cast<char>(bits & 0xFF);
    out.write(buf, 8);
}

std::uint8_t ConstTagByte(ConstTag tag) {
    switch (tag) {
        case ConstTag::kInt: return 1;
        case ConstTag::kFloat: return 2;
        case ConstTag::kBool: return 3;
        case ConstTag::kString: return 4;
    }
    return 0;
}

std::uint8_t TypeTagByte(TypeTag tag) {
    switch (tag) {
        case TypeTag::kInt: return 1;
        case TypeTag::kFloat: return 2;
        case TypeTag::kBool: return 3;
        case TypeTag::kString: return 4;
        case TypeTag::kVoid: return 5;
        case TypeTag::kArray: return 6;
    }
    return 0;
}

std::filesystem::path WriteModuleToFile(const BytecodeModule& module, const std::string& filename) {
    std::filesystem::path path = std::filesystem::path("tests") / "integration_tests" / filename;
    std::filesystem::create_directories(path.parent_path());
    std::ofstream out(path, std::ios::binary);
    if (!out) throw std::runtime_error("failed to open file for writing");

    out.write("FROG", 4);
    WriteU16(out, 1);
    WriteU32(out, static_cast<std::uint32_t>(module.const_pool.size()));
    WriteU32(out, static_cast<std::uint32_t>(module.functions.size()));
    WriteU32(out, static_cast<std::uint32_t>(module.code.size()));

    for (const Constant& c : module.const_pool) {
        out.put(static_cast<char>(ConstTagByte(c.tag)));
        switch (c.tag) {
            case ConstTag::kInt:
                WriteI32(out, std::get<std::int32_t>(c.value));
                break;
            case ConstTag::kFloat:
                WriteF64(out, std::get<double>(c.value));
                break;
            case ConstTag::kBool:
                out.put(std::get<bool>(c.value) ? 1 : 0);
                break;
            case ConstTag::kString: {
                const auto& s = std::get<std::string>(c.value);
                WriteU32(out, static_cast<std::uint32_t>(s.size()));
                out.write(s.data(), static_cast<std::streamsize>(s.size()));
                break;
            }
        }
    }

    for (const FunctionInfo& fn : module.functions) {
        WriteU32(out, fn.name_const_index);
        WriteU16(out, fn.param_count);
        WriteU16(out, fn.local_count);
        WriteU32(out, fn.entry_ip);
        out.put(static_cast<char>(TypeTagByte(fn.return_type)));
        for (TypeTag pt : fn.param_types) {
            out.put(static_cast<char>(TypeTagByte(pt)));
        }
    }

    for (const Instruction& ins : module.code) {
        out.put(static_cast<char>(static_cast<std::uint8_t>(ins.op)));
        std::uint8_t flags = 0;
        if (ins.has_a) flags |= 1;
        if (ins.has_b) flags |= 2;
        out.put(static_cast<char>(flags));
        if (ins.has_a) WriteU32(out, ins.a);
        if (ins.has_b) WriteU16(out, ins.b);
    }

    out.flush();
    return path;
}

Instruction MakeIns(OpCode op) {
    Instruction ins;
    ins.op = op;
    return ins;
}

Instruction MakeInsA(OpCode op, std::uint32_t a) {
    Instruction ins;
    ins.op = op;
    ins.has_a = true;
    ins.a = a;
    return ins;
}

Instruction MakeInsB(OpCode op, std::uint16_t b) {
    Instruction ins;
    ins.op = op;
    ins.has_b = true;
    ins.b = b;
    return ins;
}

Instruction MakeInsAB(OpCode op, std::uint32_t a, std::uint16_t b) {
    Instruction ins;
    ins.op = op;
    ins.has_a = true;
    ins.a = a;
    ins.has_b = true;
    ins.b = b;
    return ins;
}

BytecodeModule BuildFactorialJitModule(int call_count) {
    BytecodeModule module;
    module.const_pool = {
        {ConstTag::kString, std::string("fact")},
        {ConstTag::kString, std::string("result")},
        {ConstTag::kInt, static_cast<std::int32_t>(5)},
        {ConstTag::kInt, static_cast<std::int32_t>(1)},
    };

    std::vector<Instruction> code;
    for (int i = 0; i < call_count; ++i) {
        code.push_back(MakeInsA(OpCode::kPushConst, 2));
        code.push_back(MakeInsAB(OpCode::kCall, 0, 1));
        code.push_back(MakeInsA(OpCode::kStoreGlobal, 1));
    }

    std::size_t exit_jump = code.size();
    code.push_back(MakeInsA(OpCode::kJump, 0));

    std::size_t fact_entry = code.size();
    code.push_back(MakeInsB(OpCode::kLoadLocal, 0));
    code.push_back(MakeInsA(OpCode::kPushConst, 3));
    code.push_back(MakeIns(OpCode::kLe));
    std::size_t jump_false_idx = code.size();
    code.push_back(MakeInsA(OpCode::kJumpFalse, 0));
    code.push_back(MakeInsA(OpCode::kPushConst, 3));
    code.push_back(MakeIns(OpCode::kRet));

    std::size_t else_ip = code.size();
    code[jump_false_idx].a = static_cast<std::uint32_t>(else_ip);

    code.push_back(MakeInsB(OpCode::kLoadLocal, 0));
    code.push_back(MakeInsB(OpCode::kLoadLocal, 0));
    code.push_back(MakeInsA(OpCode::kPushConst, 3));
    code.push_back(MakeIns(OpCode::kSub));
    code.push_back(MakeInsAB(OpCode::kCall, 0, 1));
    code.push_back(MakeIns(OpCode::kMul));
    code.push_back(MakeIns(OpCode::kRet));

    code.push_back(MakeIns(OpCode::kRet));
    code[exit_jump].a = static_cast<std::uint32_t>(code.size() - 1);

    module.code = std::move(code);

    FunctionInfo fact{};
    fact.name_const_index = 0;
    fact.param_count = 1;
    fact.local_count = 1;
    fact.entry_ip = static_cast<std::uint32_t>(fact_entry);
    fact.return_type = TypeTag::kInt;
    fact.param_types = {TypeTag::kInt};
    module.functions = {std::move(fact)};
    return module;
}

BytecodeModule BuildGcStressModule(int alloc_count, std::int32_t length) {
    BytecodeModule module;
    module.const_pool = {
        {ConstTag::kString, std::string("new_array_bool")},
        {ConstTag::kString, std::string("sink")},
        {ConstTag::kInt, length},
        {ConstTag::kBool, true},
    };

    std::vector<Instruction> code;
    for (int i = 0; i < alloc_count; ++i) {
        code.push_back(MakeInsA(OpCode::kPushConst, 2));
        code.push_back(MakeInsA(OpCode::kPushConst, 3));
        code.push_back(MakeInsAB(OpCode::kCall, 0, 2));
        code.push_back(MakeInsA(OpCode::kStoreGlobal, 1));
    }
    module.code = std::move(code);

    FunctionInfo builtin{};
    builtin.name_const_index = 0;
    builtin.param_count = 2;
    builtin.local_count = 0;
    builtin.entry_ip = std::numeric_limits<std::uint32_t>::max();
    builtin.return_type = TypeTag::kArray;
    builtin.param_types = {TypeTag::kInt, TypeTag::kBool};
    module.functions = {std::move(builtin)};
    return module;
}

class CoutRedirect {
public:
    CoutRedirect() : old_buf_(std::cout.rdbuf(buffer_.rdbuf())) {}
    ~CoutRedirect() { std::cout.rdbuf(old_buf_); }
    std::string str() const { return buffer_.str(); }

private:
    std::ostringstream buffer_;
    std::streambuf* old_buf_;
};

}  // namespace

TEST(RealFileTest, MatchesHelloFrogcFromJavaFrontend) {
    std::filesystem::path file = WriteRawFile(kHelloModule);
    ASSERT_TRUE(std::filesystem::exists(file));

    BytecodeModule module = load_frogc(file);
    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);

    auto globals = vm.globals();
    auto it = globals.find("x");
    ASSERT_NE(it, globals.end());
    std::int64_t got = 0;
    ASSERT_TRUE(it->second.AsInt().TryToInt64(got));
    EXPECT_EQ(got, 70);
}

TEST(RealFileTest, FactorialTriggersJitCompilation) {
    BytecodeModule module = BuildFactorialJitModule(60);
    VmOptions opts;
    opts.jit_log = true;

    CoutRedirect capture;
    std::filesystem::path path = WriteModuleToFile(module, "factorial.frogc");
    BytecodeModule loaded = load_frogc(path);
    Vm vm(loaded, opts);
    EXPECT_EQ(vm.run(), 0);

    auto globals = vm.globals();
    auto it = globals.find("result");
    ASSERT_NE(it, globals.end());
    std::int64_t got = 0;
    ASSERT_TRUE(it->second.AsInt().TryToInt64(got));
    EXPECT_EQ(got, 120);

    std::string log = capture.str();
    EXPECT_NE(log.find("JIT COMPILED"), std::string::npos);
    EXPECT_NE(log.find("ENTER JIT"), std::string::npos);
}

TEST(RealFileTest, GarbageCollectorProducesLogs) {
    BytecodeModule module = BuildGcStressModule(150, 20000);
    VmOptions opts;
    opts.gc_log = true;

    CoutRedirect capture;
    std::filesystem::path path = WriteModuleToFile(module, "gc_stress.frogc");
    BytecodeModule loaded = load_frogc(path);
    Vm vm(loaded, opts);
    EXPECT_EQ(vm.run(), 0);

    auto globals = vm.globals();
    auto it = globals.find("sink");
    ASSERT_NE(it, globals.end());
    EXPECT_EQ(it->second.tag, ValueTag::kArray);

    std::string log = capture.str();
    EXPECT_NE(log.find("GC START"), std::string::npos);
    EXPECT_NE(log.find("GC SWEPT"), std::string::npos);
}
