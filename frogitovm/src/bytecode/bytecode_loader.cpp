#include "bytecode_loader.h"

#include <cstdint>
#include <cstring>
#include <fstream>
#include <stdexcept>
#include <string>

namespace {

void ReadExact(std::istream& in, char* buffer, std::size_t size) {
    in.read(buffer, static_cast<std::streamsize>(size));
    if (!in) {
        throw std::runtime_error("unexpected end of file");
    }
}

std::uint8_t ReadU8(std::istream& in) {
    char c;
    ReadExact(in, &c, 1);
    return static_cast<std::uint8_t>(static_cast<unsigned char>(c));
}

std::uint16_t ReadU16Be(std::istream& in) {
    std::uint8_t buf[2];
    ReadExact(in, reinterpret_cast<char*>(buf), 2);
    return static_cast<std::uint16_t>((buf[0] << 8) | buf[1]);
}

std::uint32_t ReadU32Be(std::istream& in) {
    std::uint8_t buf[4];
    ReadExact(in, reinterpret_cast<char*>(buf), 4);
    return (static_cast<std::uint32_t>(buf[0]) << 24) |
           (static_cast<std::uint32_t>(buf[1]) << 16) |
           (static_cast<std::uint32_t>(buf[2]) << 8) |
           static_cast<std::uint32_t>(buf[3]);
}

std::int32_t ReadI32Be(std::istream& in) {
    return static_cast<std::int32_t>(ReadU32Be(in));
}

double ReadF64Be(std::istream& in) {
    std::uint8_t buf[8];
    ReadExact(in, reinterpret_cast<char*>(buf), 8);
    std::uint64_t v =
        (static_cast<std::uint64_t>(buf[0]) << 56) |
        (static_cast<std::uint64_t>(buf[1]) << 48) |
        (static_cast<std::uint64_t>(buf[2]) << 40) |
        (static_cast<std::uint64_t>(buf[3]) << 32) |
        (static_cast<std::uint64_t>(buf[4]) << 24) |
        (static_cast<std::uint64_t>(buf[5]) << 16) |
        (static_cast<std::uint64_t>(buf[6]) << 8) |
        static_cast<std::uint64_t>(buf[7]);
    double d;
    std::memcpy(&d, &v, sizeof(d));
    return d;
}

std::string ReadStringBe(std::istream& in) {
    std::uint32_t len = ReadU32Be(in);
    std::string s;
    s.resize(len);
    if (len > 0) {
        ReadExact(in, s.data(), len);
    }
    return s;
}

ConstTag ConstTagFromByte(std::uint8_t v) {
    switch (v) {
        case 1:
            return ConstTag::kInt;
        case 2:
            return ConstTag::kFloat;
        case 3:
            return ConstTag::kBool;
        case 4:
            return ConstTag::kString;
        default:
            throw std::runtime_error("invalid const tag");
    }
}

TypeTag TypeTagFromByte(std::uint8_t v) {
    switch (v) {
        case 1:
            return TypeTag::kInt;
        case 2:
            return TypeTag::kFloat;
        case 3:
            return TypeTag::kBool;
        case 4:
            return TypeTag::kString;
        case 5:
            return TypeTag::kVoid;
        case 6:
            return TypeTag::kArray;
        default:
            throw std::runtime_error("invalid type tag");
    }
}

OpCode OpCodeFromByte(std::uint8_t v) {
    if (v > static_cast<std::uint8_t>(OpCode::kPop)) {
        throw std::runtime_error("invalid opcode");
    }
    return static_cast<OpCode>(v);
}

void ValidateModule(const BytecodeModule& m) {
    const std::size_t const_count = m.const_pool.size();
    const std::size_t func_count = m.functions.size();
    const std::size_t code_size = m.code.size();

    for (std::size_t ip = 0; ip < code_size; ++ip) {
        const Instruction& ins = m.code[ip];
        switch (ins.op) {
            case OpCode::kPushConst:
                if (!ins.has_a || ins.a >= const_count) {
                    throw std::runtime_error("invalid constIndex in PUSH_CONST");
                }
                break;
            case OpCode::kLoadGlobal:
            case OpCode::kStoreGlobal:
                if (!ins.has_a || ins.a >= const_count) {
                    throw std::runtime_error("invalid name constIndex in LOAD/STORE_GLOBAL");
                }
                break;
            case OpCode::kCall:
                if (!ins.has_a || !ins.has_b || ins.a >= func_count) {
                    throw std::runtime_error("invalid funcIndex in CALL");
                }
                break;
            case OpCode::kJump:
            case OpCode::kJumpFalse:
                if (!ins.has_a || ins.a >= code_size) {
                    throw std::runtime_error("invalid jump target");
                }
                break;
            default:
                break;
        }
    }

    for (const FunctionInfo& f : m.functions) {
        if (f.name_const_index >= const_count) {
            throw std::runtime_error("function nameConstIndex out of range");
        }
    }
}

}  // namespace

BytecodeModule load_frogc(const std::filesystem::path& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        throw std::runtime_error("cannot open file");
    }

    char magic[4];
    ReadExact(in, magic, 4);
    if (magic[0] != 'F' || magic[1] != 'R' || magic[2] != 'O' || magic[3] != 'G') {
        throw std::runtime_error("bad magic");
    }

    std::uint16_t version = ReadU16Be(in);
    if (version != 1) {
        throw std::runtime_error("unsupported version");
    }

    std::uint32_t const_count = ReadU32Be(in);
    std::uint32_t func_count = ReadU32Be(in);
    std::uint32_t code_size = ReadU32Be(in);

    BytecodeModule m;
    m.const_pool.reserve(const_count);
    m.functions.reserve(func_count);
    m.code.reserve(code_size);

    for (std::uint32_t i = 0; i < const_count; ++i) {
        std::uint8_t tag_byte = ReadU8(in);
        ConstTag tag = ConstTagFromByte(tag_byte);

        Constant c;
        c.tag = tag;

        switch (tag) {
            case ConstTag::kInt:
                c.value = ReadI32Be(in);
                break;
            case ConstTag::kFloat:
                c.value = ReadF64Be(in);
                break;
            case ConstTag::kBool: {
                std::uint8_t b = ReadU8(in);
                c.value = static_cast<bool>(b != 0);
                break;
            }
            case ConstTag::kString:
                c.value = ReadStringBe(in);
                break;
        }

        m.const_pool.push_back(std::move(c));
    }

    for (std::uint32_t i = 0; i < func_count; ++i) {
        FunctionInfo f;
        f.name_const_index = ReadU32Be(in);
        f.param_count = ReadU16Be(in);
        f.local_count = ReadU16Be(in);
        f.entry_ip = ReadU32Be(in);

        std::uint8_t ret_type = ReadU8(in);
        f.return_type = TypeTagFromByte(ret_type);

        f.param_types.reserve(f.param_count);
        for (std::uint16_t pi = 0; pi < f.param_count; ++pi) {
            std::uint8_t pt = ReadU8(in);
            f.param_types.push_back(TypeTagFromByte(pt));
        }

        m.functions.push_back(std::move(f));
    }

    for (std::uint32_t i = 0; i < code_size; ++i) {
        std::uint8_t op_byte = ReadU8(in);
        OpCode op = OpCodeFromByte(op_byte);

        std::uint8_t flags = ReadU8(in);
        bool has_a = (flags & 1) != 0;
        bool has_b = (flags & 2) != 0;

        Instruction ins;
        ins.op = op;
        ins.has_a = has_a;
        ins.has_b = has_b;
        ins.a = 0;
        ins.b = 0;

        if (has_a) {
            ins.a = ReadU32Be(in);
        }
        if (has_b) {
            ins.b = ReadU16Be(in);
        }

        m.code.push_back(ins);
    }

    ValidateModule(m);
    return m;
}
