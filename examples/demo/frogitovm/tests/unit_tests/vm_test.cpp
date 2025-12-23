#include <gtest/gtest.h>

#include <optional>
#include <string>
#include <limits>

#include "vm/vm.h"

namespace {

Constant MakeStringConst(std::string value) {
    return Constant{ConstTag::kString, std::move(value)};
}

Constant MakeIntConst(std::int32_t value) {
    return Constant{ConstTag::kInt, value};
}

Constant MakeFloatConst(double value) {
    return Constant{ConstTag::kFloat, value};
}

Constant MakeBoolConst(bool value) {
    return Constant{ConstTag::kBool, value};
}

Instruction MakeIns(OpCode op,
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

constexpr std::uint32_t kBuiltinEntryIp = std::numeric_limits<std::uint32_t>::max();

FunctionInfo MakeFunctionInfo(std::uint32_t name_index,
                              std::uint16_t param_count,
                              std::uint16_t local_count,
                              std::uint32_t entry_ip,
                              TypeTag return_type) {
    FunctionInfo fn;
    fn.name_const_index = name_index;
    fn.param_count = param_count;
    fn.local_count = local_count;
    fn.entry_ip = entry_ip;
    fn.return_type = return_type;
    fn.param_types.assign(param_count, TypeTag::kInt);
    return fn;
}

FunctionInfo MakeBuiltinFunction(std::uint32_t name_index,
                                 std::uint16_t param_count,
                                 TypeTag return_type) {
    return MakeFunctionInfo(name_index, param_count, 0, kBuiltinEntryIp, return_type);
}

void ExpectRuntimeError(Vm& vm, const std::string& snippet) {
    try {
        vm.run();
        FAIL() << "expected runtime_error";
    } catch (const std::runtime_error& e) {
        EXPECT_NE(std::string(e.what()).find(snippet), std::string::npos) << e.what();
    }
}

std::int64_t AsInt64(const Value& v) {
    std::int64_t out = 0;
    if (!v.AsInt().TryToInt64(out)) {
        throw std::runtime_error("int does not fit int64");
    }
    return out;
}

}  // namespace

TEST(VmTest, StoresAndLoadsGlobal) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("x"),
        MakeIntConst(7),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kStoreGlobal, 0u),
        MakeIns(OpCode::kLoadGlobal, 0u),
        MakeIns(OpCode::kPop),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);

    auto globals = vm.globals();
    ASSERT_EQ(globals.size(), 1u);
    auto it = globals.find("x");
    ASSERT_NE(it, globals.end());
    EXPECT_EQ(AsInt64(it->second), 7);
    EXPECT_TRUE(vm.stack().empty());
}

TEST(VmTest, LoadGlobalMissingThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("missing"),
    };
    module.code = {
        MakeIns(OpCode::kLoadGlobal, 0u),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "unknown global");
}

TEST(VmTest, StoreGlobalRequiresStringName) {
    BytecodeModule module;
    module.const_pool = {
        MakeIntConst(42),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kStoreGlobal, 0u),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "const is not string");
}

TEST(VmTest, GlobalNameConstIndexOutOfRange) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("x"),
        MakeIntConst(5),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kStoreGlobal, 5u),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "const index out of range");
}

TEST(VmTest, PushConstInvalidIndexThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("x"),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 10u),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "const index out of range");
}

TEST(VmTest, PushConstMissingOperandThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeIntConst(1),
    };
    Instruction bad = MakeIns(OpCode::kPushConst);
    bad.has_a = false;
    module.code = {bad};

    Vm vm(module, {});
    ExpectRuntimeError(vm, "PUSH_CONST missing a");
}

TEST(VmTest, PushConstSupportsFloatAndBool) {
    BytecodeModule module;
    module.const_pool = {
        MakeFloatConst(2.5),
        MakeBoolConst(true),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);

    ASSERT_EQ(vm.stack().size(), 2u);
    EXPECT_DOUBLE_EQ(vm.stack()[0].AsFloat(), 2.5);
    EXPECT_TRUE(vm.stack()[1].AsBool());
}

TEST(VmTest, CallsUserFunctionAndHandlesLocals) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("add_one"),
        MakeIntConst(5),
        MakeIntConst(7),
    };
    module.code = {
        MakeIns(OpCode::kJump, 0u),
    };
    const std::uint32_t func_entry = static_cast<std::uint32_t>(module.code.size());
    module.code.push_back(MakeIns(OpCode::kLoadLocal, std::nullopt, static_cast<std::uint16_t>(0)));
    module.code.push_back(MakeIns(OpCode::kPushConst, 2u));
    module.code.push_back(MakeIns(OpCode::kAdd));
    module.code.push_back(MakeIns(OpCode::kStoreLocal, std::nullopt, static_cast<std::uint16_t>(1)));
    module.code.push_back(MakeIns(OpCode::kLoadLocal, std::nullopt, static_cast<std::uint16_t>(1)));
    module.code.push_back(MakeIns(OpCode::kRet));
    const std::uint32_t global_start = static_cast<std::uint32_t>(module.code.size());
    module.code[0].a = global_start;
    module.code.push_back(MakeIns(OpCode::kPushConst, 1u));
    module.code.push_back(MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(1)));

    module.functions.push_back(MakeFunctionInfo(0u, 1, 2, func_entry, TypeTag::kInt));

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 12);
}

TEST(VmTest, CallMissingOperandsThrows) {
    BytecodeModule module;
    module.code = {
        MakeIns(OpCode::kCall, 0u),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "CALL missing operands");
}

TEST(VmTest, CallBadFunctionIndexThrows) {
    BytecodeModule module;
    module.code = {
        MakeIns(OpCode::kCall, 5u, static_cast<std::uint16_t>(0)),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "CALL bad func index");
}

TEST(VmTest, CallArgcMismatchThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("f"),
    };
    module.functions.push_back(MakeFunctionInfo(0u, 1, 1, 0u, TypeTag::kVoid));
    module.code = {
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(0)),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "CALL argc mismatch");
}

TEST(VmTest, AddTwoInts) {
    BytecodeModule module;
    module.const_pool = {
        MakeIntConst(2),
        MakeIntConst(5),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kAdd),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 7);
}

TEST(VmTest, AddStringsConcatenates) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("hello"),
        MakeStringConst("frog"),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kAdd),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(vm.stack()[0].AsString()->value, "hellofrog");
}

TEST(VmTest, SubtractsFloats) {
    BytecodeModule module;
    module.const_pool = {
        MakeFloatConst(5.5),
        MakeFloatConst(1.5),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kSub),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_DOUBLE_EQ(vm.stack()[0].AsFloat(), 4.0);
}

TEST(VmTest, DivisionByZeroThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeIntConst(10),
        MakeIntConst(0),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kDiv),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "division by zero");
}

TEST(VmTest, ModRequiresIntegers) {
    BytecodeModule module;
    module.const_pool = {
        MakeFloatConst(5.0),
        MakeFloatConst(2.0),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kMod),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "MOD requires int");
}

TEST(VmTest, NegatesFloat) {
    BytecodeModule module;
    module.const_pool = {
        MakeFloatConst(3.0),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kNeg),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_DOUBLE_EQ(vm.stack()[0].AsFloat(), -3.0);
}

TEST(VmTest, EqOnStrings) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("a"),
        MakeStringConst("a"),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kEq),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_TRUE(vm.stack()[0].AsBool());
}

TEST(VmTest, LtOnFloats) {
    BytecodeModule module;
    module.const_pool = {
        MakeFloatConst(1.0),
        MakeFloatConst(5.0),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kLt),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_TRUE(vm.stack()[0].AsBool());
}

TEST(VmTest, LogicalOperators) {
    BytecodeModule module;
    module.const_pool = {
        MakeBoolConst(true),
        MakeBoolConst(false),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kAnd),  // true && false -> false
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kOr),   // true || false -> true
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kNot),  // !true -> false
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 3u);
    EXPECT_FALSE(vm.stack()[0].AsBool());
    EXPECT_TRUE(vm.stack()[1].AsBool());
    EXPECT_FALSE(vm.stack()[2].AsBool());
}

TEST(VmTest, PopOnEmptyStackThrows) {
    BytecodeModule module;
    module.code = {
        MakeIns(OpCode::kPop),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "stack underflow");
}

TEST(VmTest, StoreGlobalMissingOperandThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("x"),
        MakeIntConst(1),
    };
    Instruction store = MakeIns(OpCode::kStoreGlobal, 0u);
    store.has_a = false;
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        store,
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "STORE_GLOBAL missing a");
}

TEST(VmTest, LoadGlobalMissingOperandThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("x"),
    };
    Instruction load = MakeIns(OpCode::kLoadGlobal, 0u);
    load.has_a = false;
    module.code = {load};

    Vm vm(module, {});
    ExpectRuntimeError(vm, "LOAD_GLOBAL missing a");
}

TEST(VmTest, JumpSkipsInstructions) {
    BytecodeModule module;
    module.const_pool = {
        MakeIntConst(1),
        MakeIntConst(2),
    };
    module.code = {
        MakeIns(OpCode::kJump, 2u),
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 2);
}

TEST(VmTest, JumpFalseBranchesOnFalse) {
    BytecodeModule module;
    module.const_pool = {
        MakeBoolConst(false),
        MakeIntConst(42),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kJumpFalse, 3u),
        MakeIns(OpCode::kPushConst, 1u),  // skipped
        MakeIns(OpCode::kPushConst, 1u),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 42);
}

TEST(VmTest, JumpFalseDoesNotBranchOnTrue) {
    BytecodeModule module;
    module.const_pool = {
        MakeBoolConst(true),
        MakeIntConst(1),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kJumpFalse, 2u),
        MakeIns(OpCode::kPushConst, 1u),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 1);
}

TEST(VmTest, NewArrayLoadAndStoreIndexWorks) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("arr"),
        MakeIntConst(10),
        MakeIntConst(20),
        MakeIntConst(1),
        MakeIntConst(99),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kPushConst, 2u),
        MakeIns(OpCode::kNewArray, std::nullopt, static_cast<std::uint16_t>(2)),
        MakeIns(OpCode::kStoreGlobal, 0u),
        MakeIns(OpCode::kLoadGlobal, 0u),
        MakeIns(OpCode::kPushConst, 3u),
        MakeIns(OpCode::kLoadIndex),
        MakeIns(OpCode::kLoadGlobal, 0u),
        MakeIns(OpCode::kPushConst, 3u),
        MakeIns(OpCode::kPushConst, 4u),
        MakeIns(OpCode::kStoreIndex),
        MakeIns(OpCode::kLoadGlobal, 0u),
        MakeIns(OpCode::kPushConst, 3u),
        MakeIns(OpCode::kLoadIndex),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 2u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 20);
    EXPECT_EQ(AsInt64(vm.stack()[1]), 99);
}

TEST(VmTest, NewArrayMissingOperandThrows) {
    BytecodeModule module;
    module.code = {
        MakeIns(OpCode::kNewArray),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "NEW_ARRAY missing b");
}

TEST(VmTest, LoadIndexRequiresArray) {
    BytecodeModule module;
    module.const_pool = {
        MakeBoolConst(true),
        MakeIntConst(0),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 0u),
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kLoadIndex),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "LOAD_INDEX expects array");
}

TEST(VmTest, StoreIndexOutOfBoundsThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("arr"),
        MakeIntConst(5),
        MakeIntConst(0),
        MakeIntConst(1),
    };
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kNewArray, std::nullopt, static_cast<std::uint16_t>(1)),
        MakeIns(OpCode::kStoreGlobal, 0u),
        MakeIns(OpCode::kLoadGlobal, 0u),
        MakeIns(OpCode::kPushConst, 3u),
        MakeIns(OpCode::kPushConst, 2u),
        MakeIns(OpCode::kStoreIndex),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "array index out of bounds");
}

TEST(VmTest, BuiltinLenOnArray) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("len"),
        MakeIntConst(3),
        MakeIntConst(4),
    };
    module.functions.push_back(MakeBuiltinFunction(0u, 1, TypeTag::kInt));
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kPushConst, 2u),
        MakeIns(OpCode::kNewArray, std::nullopt, static_cast<std::uint16_t>(2)),
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(1)),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 1u);
    EXPECT_EQ(AsInt64(vm.stack()[0]), 2);
}

TEST(VmTest, BuiltinLenRequiresArray) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("len"),
        MakeIntConst(1),
    };
    module.functions.push_back(MakeBuiltinFunction(0u, 1, TypeTag::kInt));
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(1)),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "len expects array");
}

TEST(VmTest, BuiltinNewArrayBoolAndPushInt) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("new_array_bool"),
        MakeStringConst("push_int"),
        MakeStringConst("arr"),
        MakeIntConst(2),
        MakeBoolConst(true),
        MakeIntConst(0),
        MakeIntConst(5),
        MakeIntConst(2),
    };
    module.functions.push_back(MakeBuiltinFunction(0u, 2, TypeTag::kArray));
    module.functions.push_back(MakeBuiltinFunction(1u, 2, TypeTag::kArray));
    module.code = {
        MakeIns(OpCode::kPushConst, 3u),
        MakeIns(OpCode::kPushConst, 4u),
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(2)),
        MakeIns(OpCode::kStoreGlobal, 2u),
        MakeIns(OpCode::kLoadGlobal, 2u),
        MakeIns(OpCode::kPushConst, 5u),
        MakeIns(OpCode::kLoadIndex),
        MakeIns(OpCode::kLoadGlobal, 2u),
        MakeIns(OpCode::kPushConst, 6u),
        MakeIns(OpCode::kCall, 1u, static_cast<std::uint16_t>(2)),
        MakeIns(OpCode::kStoreGlobal, 2u),
        MakeIns(OpCode::kLoadGlobal, 2u),
        MakeIns(OpCode::kPushConst, 7u),
        MakeIns(OpCode::kLoadIndex),
    };

    Vm vm(module, {});
    EXPECT_EQ(vm.run(), 0);
    ASSERT_EQ(vm.stack().size(), 2u);
    EXPECT_TRUE(vm.stack()[0].AsBool());
    EXPECT_EQ(AsInt64(vm.stack()[1]), 5);
}

TEST(VmTest, BuiltinNewArrayBoolNegativeSizeThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("new_array_bool"),
        MakeIntConst(-1),
        MakeBoolConst(true),
    };
    module.functions.push_back(MakeBuiltinFunction(0u, 2, TypeTag::kArray));
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kPushConst, 2u),
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(2)),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "negative size");
}

TEST(VmTest, BuiltinPushIntTypeMismatchThrows) {
    BytecodeModule module;
    module.const_pool = {
        MakeStringConst("push_int"),
        MakeBoolConst(true),
        MakeIntConst(1),
    };
    module.functions.push_back(MakeBuiltinFunction(0u, 2, TypeTag::kArray));
    module.code = {
        MakeIns(OpCode::kPushConst, 1u),
        MakeIns(OpCode::kPushConst, 2u),
        MakeIns(OpCode::kCall, 0u, static_cast<std::uint16_t>(2)),
    };

    Vm vm(module, {});
    ExpectRuntimeError(vm, "push_int type mismatch");
}

TEST(VmTest, UnsupportedOpcodeThrows) {
    BytecodeModule module;
    Instruction bad;
    bad.op = static_cast<OpCode>(255);
    module.code = {bad};

    Vm vm(module, {});
    ExpectRuntimeError(vm, "opcode not implemented");
}
