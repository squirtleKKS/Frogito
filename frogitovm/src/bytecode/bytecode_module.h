#pragma once

#include <cstdint>
#include <string>
#include <variant>
#include <vector>

enum class ConstTag : std::uint8_t {
    kInt = 1,
    kFloat = 2,
    kBool = 3,
    kString = 4
};

enum class TypeTag : std::uint8_t {
    kInt = 1,
    kFloat = 2,
    kBool = 3,
    kString = 4,
    kVoid = 5,
    kArray = 6
};

enum class OpCode : std::uint8_t {
    kPushConst,
    kLoadLocal,
    kStoreLocal,
    kLoadGlobal,
    kStoreGlobal,
    kAdd,
    kSub,
    kMul,
    kDiv,
    kMod,
    kNeg,
    kEq,
    kNeq,
    kLt,
    kLe,
    kGt,
    kGe,
    kAnd,
    kOr,
    kNot,
    kJump,
    kJumpFalse,
    kCall,
    kRet,
    kNewArray,
    kLoadIndex,
    kStoreIndex,
    kPop
};

struct Constant {
    ConstTag tag;
    std::variant<std::int32_t, double, bool, std::string> value;
};

struct FunctionInfo {
    std::uint32_t name_const_index = 0;
    std::uint16_t param_count = 0;
    std::uint16_t local_count = 0;
    std::uint32_t entry_ip = 0;
    TypeTag return_type = TypeTag::kVoid;
    std::vector<TypeTag> param_types;
};

struct Instruction {
    OpCode op = OpCode::kPop;
    std::uint32_t a = 0;
    std::uint16_t b = 0;
    bool has_a = false;
    bool has_b = false;
};

struct BytecodeModule {
    std::vector<Constant> const_pool;
    std::vector<FunctionInfo> functions;
    std::vector<Instruction> code;
};
