#pragma once

#include <array>
#include <cstdint>
#include <span>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include "bytecode/bytecode_module.h"
#include "runtime/errors.h"
#include "runtime/heap.h"
#include "runtime/value.h"

struct VmOptions {
    bool trace = false;
    bool jit_log = false;
    bool gc_log = false;
};

struct CallFrame {
    std::uint32_t func_index = 0;
    std::uint32_t ip = 0;
    std::uint32_t return_ip = 0;
    std::size_t base_stack_size = 0;
    std::vector<Value> locals;
};

class Vm {
public:
    Vm(const BytecodeModule& module, VmOptions options);

    int run();
    const std::vector<Value>& stack() const { return stack_; }
    std::unordered_map<std::string, Value> globals() const;

private:
    class TempRoots {
public:
        TempRoots(Vm& vm, std::span<const Value> values);
        TempRoots(Vm& vm, const Value& v0);
        TempRoots(Vm& vm, const Value& v0, const Value& v1);
        ~TempRoots();

private:
        Vm& vm_;
        std::size_t start_ = 0;
    };

    using Handler = void (*)(Vm&, const Instruction&);

    struct CompiledFunc {
        std::uint32_t func_index = 0;
        std::uint32_t entry_ip = 0;
        std::uint32_t end_ip = 0;
        std::vector<Handler> handlers;
    };

    static constexpr std::uint32_t kBuiltinEntryIp = 0xFFFFFFFFu;
    static constexpr std::uint32_t kGlobalFuncIndex = 0xFFFFFFFFu;
    static constexpr std::uint32_t kHotFuncThreshold = 50;
    static constexpr std::size_t kOpCount = static_cast<std::size_t>(OpCode::kPop) + 1;

    const BytecodeModule& module_;
    VmOptions options_;

    Heap heap_;

    std::vector<Value> const_values_;
    std::vector<Value> stack_;

    std::vector<Value> globals_values_;
    std::vector<std::uint8_t> globals_set_;

    std::vector<CallFrame> frames_;
    std::vector<Value> temp_roots_;

    std::vector<std::uint32_t> func_end_ip_;
    std::vector<std::uint32_t> hot_counters_;
    std::unordered_map<std::uint32_t, CompiledFunc> code_cache_;

    void BuildConstValues();
    void BuildFuncRanges();
    Heap::RootsEnumerator Roots() const;

    static const std::array<Handler, kOpCount>& Dispatch();

    void push(const Value& v);
    Value pop();
    std::span<const Value> peek_args(std::size_t argc) const;

    CallFrame& frame();
    const CallFrame& frame() const;

    void step();
    void run_jit(CompiledFunc& cf);

    void trace(const Instruction& ins, std::uint32_t ip_before) const;

    std::string opcode_name(OpCode op) const;
    std::string value_repr(const Value& v) const;

    std::string_view const_string_view(std::uint32_t const_index) const;
    std::string_view func_name_view(std::uint32_t func_index) const;
    bool is_void_return(std::uint32_t func_index) const;

    Value add_values(const Value& l, const Value& r);
    Value sub_values(const Value& l, const Value& r) const;
    Value mul_values(const Value& l, const Value& r) const;
    Value div_values(const Value& l, const Value& r) const;
    Value mod_values(const Value& l, const Value& r) const;
    Value neg_value(const Value& v) const;

    Value eq_values(const Value& l, const Value& r) const;
    Value neq_values(const Value& l, const Value& r) const;
    Value lt_values(const Value& l, const Value& r) const;
    Value le_values(const Value& l, const Value& r) const;
    Value gt_values(const Value& l, const Value& r) const;
    Value ge_values(const Value& l, const Value& r) const;

    Value and_values(const Value& l, const Value& r) const;
    Value or_values(const Value& l, const Value& r) const;
    Value not_value(const Value& v) const;

    void call_function(std::uint32_t func_index, std::uint16_t argc);
    void ret_from_function();

    void maybe_jit_compile(std::uint32_t func_index);
    bool jit_compile(std::uint32_t func_index);

    Value call_builtin(std::string_view name, std::span<const Value> args, bool has_ret);

    static void H_PushConst(Vm& vm, const Instruction& ins);
    static void H_Pop(Vm& vm, const Instruction& ins);
    static void H_StoreGlobal(Vm& vm, const Instruction& ins);
    static void H_LoadGlobal(Vm& vm, const Instruction& ins);
    static void H_StoreLocal(Vm& vm, const Instruction& ins);
    static void H_LoadLocal(Vm& vm, const Instruction& ins);

    static void H_Add(Vm& vm, const Instruction& ins);
    static void H_Sub(Vm& vm, const Instruction& ins);
    static void H_Mul(Vm& vm, const Instruction& ins);
    static void H_Div(Vm& vm, const Instruction& ins);
    static void H_Mod(Vm& vm, const Instruction& ins);
    static void H_Neg(Vm& vm, const Instruction& ins);

    static void H_Eq(Vm& vm, const Instruction& ins);
    static void H_Neq(Vm& vm, const Instruction& ins);
    static void H_Lt(Vm& vm, const Instruction& ins);
    static void H_Le(Vm& vm, const Instruction& ins);
    static void H_Gt(Vm& vm, const Instruction& ins);
    static void H_Ge(Vm& vm, const Instruction& ins);

    static void H_And(Vm& vm, const Instruction& ins);
    static void H_Or(Vm& vm, const Instruction& ins);
    static void H_Not(Vm& vm, const Instruction& ins);

    static void H_Jump(Vm& vm, const Instruction& ins);
    static void H_JumpFalse(Vm& vm, const Instruction& ins);

    static void H_Call(Vm& vm, const Instruction& ins);
    static void H_Ret(Vm& vm, const Instruction& ins);

    static void H_NewArray(Vm& vm, const Instruction& ins);
    static void H_NewArraySized(Vm& vm, const Instruction& ins);
    static void H_LoadIndex(Vm& vm, const Instruction& ins);
    static void H_StoreIndex(Vm& vm, const Instruction& ins);
};
