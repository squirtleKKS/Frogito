#include "vm/vm.h"

#include <algorithm>
#include <iostream>
#include <sstream>
#include <utility>

Vm::TempRoots::TempRoots(Vm& vm, std::span<const Value> values) : vm_(vm) {
    start_ = vm_.temp_roots_.size();
    vm_.temp_roots_.reserve(vm_.temp_roots_.size() + values.size());
    for (const Value& v : values) {
        vm_.temp_roots_.push_back(v);
    }
}

Vm::TempRoots::TempRoots(Vm& vm, const Value& v0) : vm_(vm) {
    start_ = vm_.temp_roots_.size();
    vm_.temp_roots_.push_back(v0);
}

Vm::TempRoots::TempRoots(Vm& vm, const Value& v0, const Value& v1) : vm_(vm) {
    start_ = vm_.temp_roots_.size();
    vm_.temp_roots_.push_back(v0);
    vm_.temp_roots_.push_back(v1);
}

Vm::TempRoots::~TempRoots() {
    vm_.temp_roots_.resize(start_);
}

Vm::Vm(const BytecodeModule& module, VmOptions options)
    : module_(module), options_(options) {
    BuildConstValues();
    BuildFuncRanges();
    hot_counters_.assign(module_.functions.size(), 0);

    globals_values_.assign(module_.const_pool.size(), Value::Null());
    globals_set_.assign(module_.const_pool.size(), 0);
}

std::unordered_map<std::string, Value> Vm::globals() const {
    std::unordered_map<std::string, Value> out;
    for (std::size_t i = 0; i < globals_values_.size() && i < module_.const_pool.size(); ++i) {
        if (!globals_set_[i]) continue;
        const Constant& c = module_.const_pool[i];
        if (c.tag != ConstTag::kString) continue;
        out.emplace(std::get<std::string>(c.value), globals_values_[i]);
    }
    return out;
}

const std::array<Vm::Handler, Vm::kOpCount>& Vm::Dispatch() {
    static const std::array<Handler, kOpCount> table = [] {
        std::array<Handler, kOpCount> t{};
        t.fill(nullptr);

        t[static_cast<std::size_t>(OpCode::kPushConst)] = &Vm::H_PushConst;
        t[static_cast<std::size_t>(OpCode::kLoadLocal)] = &Vm::H_LoadLocal;
        t[static_cast<std::size_t>(OpCode::kStoreLocal)] = &Vm::H_StoreLocal;
        t[static_cast<std::size_t>(OpCode::kLoadGlobal)] = &Vm::H_LoadGlobal;
        t[static_cast<std::size_t>(OpCode::kStoreGlobal)] = &Vm::H_StoreGlobal;

        t[static_cast<std::size_t>(OpCode::kAdd)] = &Vm::H_Add;
        t[static_cast<std::size_t>(OpCode::kSub)] = &Vm::H_Sub;
        t[static_cast<std::size_t>(OpCode::kMul)] = &Vm::H_Mul;
        t[static_cast<std::size_t>(OpCode::kDiv)] = &Vm::H_Div;
        t[static_cast<std::size_t>(OpCode::kMod)] = &Vm::H_Mod;
        t[static_cast<std::size_t>(OpCode::kNeg)] = &Vm::H_Neg;

        t[static_cast<std::size_t>(OpCode::kEq)] = &Vm::H_Eq;
        t[static_cast<std::size_t>(OpCode::kNeq)] = &Vm::H_Neq;
        t[static_cast<std::size_t>(OpCode::kLt)] = &Vm::H_Lt;
        t[static_cast<std::size_t>(OpCode::kLe)] = &Vm::H_Le;
        t[static_cast<std::size_t>(OpCode::kGt)] = &Vm::H_Gt;
        t[static_cast<std::size_t>(OpCode::kGe)] = &Vm::H_Ge;

        t[static_cast<std::size_t>(OpCode::kAnd)] = &Vm::H_And;
        t[static_cast<std::size_t>(OpCode::kOr)] = &Vm::H_Or;
        t[static_cast<std::size_t>(OpCode::kNot)] = &Vm::H_Not;

        t[static_cast<std::size_t>(OpCode::kJump)] = &Vm::H_Jump;
        t[static_cast<std::size_t>(OpCode::kJumpFalse)] = &Vm::H_JumpFalse;

        t[static_cast<std::size_t>(OpCode::kCall)] = &Vm::H_Call;
        t[static_cast<std::size_t>(OpCode::kRet)] = &Vm::H_Ret;

        t[static_cast<std::size_t>(OpCode::kNewArray)] = &Vm::H_NewArray;
        t[static_cast<std::size_t>(OpCode::kLoadIndex)] = &Vm::H_LoadIndex;
        t[static_cast<std::size_t>(OpCode::kStoreIndex)] = &Vm::H_StoreIndex;

        t[static_cast<std::size_t>(OpCode::kPop)] = &Vm::H_Pop;

        return t;
    }();
    return table;
}

Heap::RootsEnumerator Vm::Roots() const {
    return [this](const Heap::RootVisitor& visit) {
        for (const Value& v : const_values_) visit(v);
        for (const Value& v : stack_) visit(v);
        for (const CallFrame& f : frames_) {
            for (const Value& v : f.locals) visit(v);
        }
        for (std::size_t i = 0; i < globals_values_.size(); ++i) {
            if (globals_set_[i]) visit(globals_values_[i]);
        }
        for (const Value& v : temp_roots_) visit(v);
    };
}

void Vm::BuildConstValues() {
    const_values_.clear();
    const_values_.reserve(module_.const_pool.size());

    for (const Constant& c : module_.const_pool) {
        switch (c.tag) {
            case ConstTag::kInt:
                const_values_.push_back(Value::FromInt(std::get<std::int32_t>(c.value)));
                break;
            case ConstTag::kFloat:
                const_values_.push_back(Value::FromFloat(std::get<double>(c.value)));
                break;
            case ConstTag::kBool:
                const_values_.push_back(Value::FromBool(std::get<bool>(c.value)));
                break;
            case ConstTag::kString: {
                StringObject* s = heap_.AllocateString(std::get<std::string>(c.value), options_.gc_log, Roots());
                const_values_.push_back(Value::FromRaw(s, ValueTag::kString));
                break;
            }
        }
    }
}

void Vm::BuildFuncRanges() {
    func_end_ip_.assign(module_.functions.size(), static_cast<std::uint32_t>(module_.code.size()));

    std::vector<std::pair<std::uint32_t, std::uint32_t>> entries;
    entries.reserve(module_.functions.size());

    for (std::uint32_t i = 0; i < module_.functions.size(); ++i) {
        const FunctionInfo& f = module_.functions[i];
        if (f.entry_ip == kBuiltinEntryIp) continue;
        entries.push_back({static_cast<std::uint32_t>(f.entry_ip), i});
    }

    std::sort(entries.begin(), entries.end(), [](const auto& a, const auto& b) { return a.first < b.first; });

    for (std::size_t i = 0; i < entries.size(); ++i) {
        std::uint32_t entry_ip = entries[i].first;
        std::uint32_t func_index = entries[i].second;
        std::uint32_t end_ip = static_cast<std::uint32_t>(module_.code.size());
        if (i + 1 < entries.size()) end_ip = entries[i + 1].first;
        if (end_ip < entry_ip || end_ip > module_.code.size()) end_ip = static_cast<std::uint32_t>(module_.code.size());
        func_end_ip_[func_index] = end_ip;
    }
}

int Vm::run() {
    stack_.clear();
    frames_.clear();
    temp_roots_.clear();

    std::fill(globals_set_.begin(), globals_set_.end(), 0);
    for (std::size_t i = 0; i < globals_values_.size(); ++i) {
        globals_values_[i] = Value::Null();
    }

    CallFrame global;
    global.func_index = kGlobalFuncIndex;
    global.ip = 0;
    global.return_ip = 0;
    global.base_stack_size = 0;
    frames_.push_back(std::move(global));

    while (!frames_.empty()) {
        CallFrame& f = frame();
        if (f.ip >= module_.code.size()) {
            frames_.pop_back();
            continue;
        }

        auto it = code_cache_.find(f.func_index);
        if (f.func_index != kGlobalFuncIndex && it != code_cache_.end()) {
            run_jit(it->second);
        } else {
            step();
        }
    }

    return 0;
}

void Vm::run_jit(CompiledFunc& cf) {
    if (options_.jit_log) {
        std::cout << "ENTER JIT func " << func_name_view(cf.func_index) << "@" << cf.func_index << "\n";
    }

    while (!frames_.empty()) {
        CallFrame& cur = frame();
        if (cur.func_index != cf.func_index) break;
        if (cur.ip < cf.entry_ip || cur.ip >= cf.end_ip) break;

        std::uint32_t ip_before = cur.ip;
        const Instruction& ins = module_.code[ip_before];

        if (options_.trace) trace(ins, ip_before);

        cur.ip += 1;

        std::uint32_t off = ip_before - cf.entry_ip;
        if (off >= cf.handlers.size()) throw RuntimeError("jit handler out of range");
        Handler h = cf.handlers[off];
        if (!h) throw RuntimeError("jit null handler");
        h(*this, ins);
    }
}

void Vm::push(const Value& v) {
    stack_.push_back(v);
}

Value Vm::pop() {
    if (stack_.empty()) throw RuntimeError("stack underflow");
    Value v = stack_.back();
    stack_.pop_back();
    return v;
}

std::span<const Value> Vm::peek_args(std::size_t argc) const {
    if (argc > stack_.size()) throw RuntimeError("stack underflow");
    const Value* base = &stack_[stack_.size() - argc];
    return std::span<const Value>(base, argc);
}

CallFrame& Vm::frame() {
    if (frames_.empty()) throw RuntimeError("no current frame");
    return frames_.back();
}

const CallFrame& Vm::frame() const {
    if (frames_.empty()) throw RuntimeError("no current frame");
    return frames_.back();
}

std::string Vm::opcode_name(OpCode op) const {
    switch (op) {
        case OpCode::kPushConst: return "PUSH_CONST";
        case OpCode::kLoadLocal: return "LOAD_LOCAL";
        case OpCode::kStoreLocal: return "STORE_LOCAL";
        case OpCode::kLoadGlobal: return "LOAD_GLOBAL";
        case OpCode::kStoreGlobal: return "STORE_GLOBAL";
        case OpCode::kAdd: return "ADD";
        case OpCode::kSub: return "SUB";
        case OpCode::kMul: return "MUL";
        case OpCode::kDiv: return "DIV";
        case OpCode::kMod: return "MOD";
        case OpCode::kNeg: return "NEG";
        case OpCode::kEq: return "EQ";
        case OpCode::kNeq: return "NEQ";
        case OpCode::kLt: return "LT";
        case OpCode::kLe: return "LE";
        case OpCode::kGt: return "GT";
        case OpCode::kGe: return "GE";
        case OpCode::kAnd: return "AND";
        case OpCode::kOr: return "OR";
        case OpCode::kNot: return "NOT";
        case OpCode::kJump: return "JUMP";
        case OpCode::kJumpFalse: return "JUMP_FALSE";
        case OpCode::kCall: return "CALL";
        case OpCode::kRet: return "RET";
        case OpCode::kNewArray: return "NEW_ARRAY";
        case OpCode::kLoadIndex: return "LOAD_INDEX";
        case OpCode::kStoreIndex: return "STORE_INDEX";
        case OpCode::kPop: return "POP";
    }
    return "UNKNOWN";
}

std::string Vm::value_repr(const Value& v) const {
    std::ostringstream out;
    switch (v.tag) {
        case ValueTag::kNull:
            out << "null";
            break;
        case ValueTag::kInt:
            out << v.AsInt();
            break;
        case ValueTag::kFloat:
            out << v.AsFloat();
            break;
        case ValueTag::kBool:
            out << (v.AsBool() ? "true" : "false");
            break;
        case ValueTag::kString:
            out << "\"" << v.AsString()->value << "\"";
            break;
        case ValueTag::kArray:
            out << "array@" << static_cast<const void*>(v.AsArray()) << "(len=" << v.AsArray()->elements.size() << ")";
            break;
    }
    return out.str();
}

void Vm::trace(const Instruction& ins, std::uint32_t ip_before) const {
    std::cout << "ip=" << ip_before << " " << opcode_name(ins.op);
    if (ins.has_a) std::cout << " a=" << ins.a;
    if (ins.has_b) std::cout << " b=" << ins.b;

    std::cout << " | stack=";
    int shown = 0;
    for (std::int32_t i = static_cast<std::int32_t>(stack_.size()) - 1; i >= 0 && shown < 3; --i, ++shown) {
        if (shown > 0) std::cout << ",";
        std::cout << value_repr(stack_[static_cast<std::size_t>(i)]);
    }
    std::cout << "\n";
}

std::string_view Vm::const_string_view(std::uint32_t const_index) const {
    if (const_index >= module_.const_pool.size()) throw RuntimeError("const index out of range");
    const Constant& c = module_.const_pool[const_index];
    if (c.tag != ConstTag::kString) throw RuntimeError("const is not string");
    return std::get<std::string>(c.value);
}

std::string_view Vm::func_name_view(std::uint32_t func_index) const {
    if (func_index >= module_.functions.size()) throw RuntimeError("bad func index");
    return const_string_view(module_.functions[func_index].name_const_index);
}

bool Vm::is_void_return(std::uint32_t func_index) const {
    if (func_index == kGlobalFuncIndex) return true;
    if (func_index >= module_.functions.size()) throw RuntimeError("bad func index");
    return module_.functions[func_index].return_type == TypeTag::kVoid;
}

Value Vm::add_values(const Value& l, const Value& r) {
    if (l.tag == ValueTag::kInt && r.tag == ValueTag::kInt) return Value::FromInt(l.AsInt() + r.AsInt());
    if (l.tag == ValueTag::kFloat && r.tag == ValueTag::kFloat) return Value::FromFloat(l.AsFloat() + r.AsFloat());
    if (l.tag == ValueTag::kString && r.tag == ValueTag::kString) {
        TempRoots guard(*this, l, r);
        std::string s = l.AsString()->value + r.AsString()->value;
        StringObject* obj = heap_.AllocateString(s, options_.gc_log, Roots());
        return Value::FromRaw(obj, ValueTag::kString);
    }
    throw RuntimeError("ADD type mismatch");
}

Value Vm::sub_values(const Value& l, const Value& r) const {
    if (l.tag == ValueTag::kInt && r.tag == ValueTag::kInt) return Value::FromInt(l.AsInt() - r.AsInt());
    if (l.tag == ValueTag::kFloat && r.tag == ValueTag::kFloat) return Value::FromFloat(l.AsFloat() - r.AsFloat());
    throw RuntimeError("SUB type mismatch");
}

Value Vm::mul_values(const Value& l, const Value& r) const {
    if (l.tag == ValueTag::kInt && r.tag == ValueTag::kInt) {
        std::int64_t a = l.AsInt();
        std::int64_t b = r.AsInt();
        
        if (a > 0 && b > 0 && a > INT64_MAX / b) {
            throw RuntimeError("integer overflow");
        }
        if (a < 0 && b < 0 && a < INT64_MAX / b) {
            throw RuntimeError("integer overflow");
        }
        if ((a > 0 && b < 0 || a < 0 && b > 0) && a != 0 && b < INT64_MIN / a) {
            throw RuntimeError("integer overflow");
        }
        
        return Value::FromInt(a * b);
    }
    if (l.tag == ValueTag::kFloat && r.tag == ValueTag::kFloat) return Value::FromFloat(l.AsFloat() * r.AsFloat());
    throw RuntimeError("MUL type mismatch");
}

Value Vm::div_values(const Value& l, const Value& r) const {
    if (l.tag == ValueTag::kInt && r.tag == ValueTag::kInt) {
        std::int64_t d = r.AsInt();
        if (d == 0) throw RuntimeError("division by zero");
        return Value::FromInt(l.AsInt() / d);
    }
    if (l.tag == ValueTag::kFloat && r.tag == ValueTag::kFloat) {
        double d = r.AsFloat();
        if (d == 0.0) throw RuntimeError("division by zero");
        return Value::FromFloat(l.AsFloat() / d);
    }
    throw RuntimeError("DIV type mismatch");
}

Value Vm::mod_values(const Value& l, const Value& r) const {
    if (l.tag == ValueTag::kInt && r.tag == ValueTag::kInt) {
        std::int64_t d = r.AsInt();
        if (d == 0) throw RuntimeError("modulo by zero");
        return Value::FromInt(l.AsInt() % d);
    }
    throw RuntimeError("MOD requires int");
}

Value Vm::neg_value(const Value& v) const {
    if (v.tag == ValueTag::kInt) return Value::FromInt(-v.AsInt());
    if (v.tag == ValueTag::kFloat) return Value::FromFloat(-v.AsFloat());
    throw RuntimeError("NEG type mismatch");
}

Value Vm::eq_values(const Value& l, const Value& r) const {
    if (l.tag != r.tag) throw RuntimeError("EQ type mismatch");
    if (l.tag == ValueTag::kInt) return Value::FromBool(l.AsInt() == r.AsInt());
    if (l.tag == ValueTag::kFloat) return Value::FromBool(l.AsFloat() == r.AsFloat());
    if (l.tag == ValueTag::kBool) return Value::FromBool(l.AsBool() == r.AsBool());
    if (l.tag == ValueTag::kString) return Value::FromBool(l.AsString()->value == r.AsString()->value);
    if (l.tag == ValueTag::kArray) return Value::FromBool(l.AsArray() == r.AsArray());
    return Value::FromBool(true);
}

Value Vm::neq_values(const Value& l, const Value& r) const {
    return Value::FromBool(!eq_values(l, r).AsBool());
}

Value Vm::lt_values(const Value& l, const Value& r) const {
    if (l.tag != r.tag) throw RuntimeError("LT type mismatch");
    if (l.tag == ValueTag::kInt) return Value::FromBool(l.AsInt() < r.AsInt());
    if (l.tag == ValueTag::kFloat) return Value::FromBool(l.AsFloat() < r.AsFloat());
    throw RuntimeError("LT requires numeric");
}

Value Vm::le_values(const Value& l, const Value& r) const {
    if (l.tag != r.tag) throw RuntimeError("LE type mismatch");
    if (l.tag == ValueTag::kInt) return Value::FromBool(l.AsInt() <= r.AsInt());
    if (l.tag == ValueTag::kFloat) return Value::FromBool(l.AsFloat() <= r.AsFloat());
    throw RuntimeError("LE requires numeric");
}

Value Vm::gt_values(const Value& l, const Value& r) const {
    if (l.tag != r.tag) throw RuntimeError("GT type mismatch");
    if (l.tag == ValueTag::kInt) return Value::FromBool(l.AsInt() > r.AsInt());
    if (l.tag == ValueTag::kFloat) return Value::FromBool(l.AsFloat() > r.AsFloat());
    throw RuntimeError("GT requires numeric");
}

Value Vm::ge_values(const Value& l, const Value& r) const {
    if (l.tag != r.tag) throw RuntimeError("GE type mismatch");
    if (l.tag == ValueTag::kInt) return Value::FromBool(l.AsInt() >= r.AsInt());
    if (l.tag == ValueTag::kFloat) return Value::FromBool(l.AsFloat() >= r.AsFloat());
    throw RuntimeError("GE requires numeric");
}

Value Vm::and_values(const Value& l, const Value& r) const {
    if (l.tag != ValueTag::kBool || r.tag != ValueTag::kBool) throw RuntimeError("AND requires bool");
    return Value::FromBool(l.AsBool() && r.AsBool());
}

Value Vm::or_values(const Value& l, const Value& r) const {
    if (l.tag != ValueTag::kBool || r.tag != ValueTag::kBool) throw RuntimeError("OR requires bool");
    return Value::FromBool(l.AsBool() || r.AsBool());
}

Value Vm::not_value(const Value& v) const {
    if (v.tag != ValueTag::kBool) throw RuntimeError("NOT requires bool");
    return Value::FromBool(!v.AsBool());
}

Value Vm::call_builtin(std::string_view name, std::span<const Value> args, bool has_ret) {
    (void)has_ret;

    if (name == "print") {
        if (args.size() != 1) throw RuntimeError("print expects 1 argument");
        const Value& v = args[0];
        if (v.tag == ValueTag::kInt) std::cout << v.AsInt() << "\n";
        else if (v.tag == ValueTag::kFloat) std::cout << v.AsFloat() << "\n";
        else if (v.tag == ValueTag::kBool) std::cout << (v.AsBool() ? "true" : "false") << "\n";
        else if (v.tag == ValueTag::kString) std::cout << v.AsString()->value << "\n";
        else throw RuntimeError("print unsupported type");
        return Value::Null();
    }

    if (name == "len") {
        if (args.size() != 1) throw RuntimeError("len expects 1 argument");
        if (args[0].tag != ValueTag::kArray) throw RuntimeError("len expects array");
        return Value::FromInt(static_cast<std::int64_t>(args[0].AsArray()->elements.size()));
    }

    if (name == "new_array_bool") {
        if (args.size() != 2) throw RuntimeError("new_array_bool expects 2 arguments");
        if (args[0].tag != ValueTag::kInt || args[1].tag != ValueTag::kBool) throw RuntimeError("new_array_bool type mismatch");
        std::int64_t n = args[0].AsInt();
        if (n < 0) throw RuntimeError("new_array_bool negative size");
        bool fill = args[1].AsBool();

        ArrayObject* arr = heap_.AllocateArray(static_cast<std::size_t>(n), options_.gc_log, Roots());
        for (std::size_t i = 0; i < static_cast<std::size_t>(n); ++i) {
            arr->elements[i] = Value::FromBool(fill);
        }
        return Value::FromRaw(arr, ValueTag::kArray);
    }

    if (name == "push_int") {
        if (args.size() != 2) throw RuntimeError("push_int expects 2 arguments");
        if (args[0].tag != ValueTag::kArray || args[1].tag != ValueTag::kInt) throw RuntimeError("push_int type mismatch");

        ArrayObject* old_arr = args[0].AsArray();
        std::size_t old_size = old_arr->elements.size();

        ArrayObject* out = heap_.AllocateArray(old_size + 1, options_.gc_log, Roots());
        for (std::size_t i = 0; i < old_size; ++i) {
            out->elements[i] = old_arr->elements[i];
        }
        out->elements[old_size] = Value::FromInt(args[1].AsInt());
        return Value::FromRaw(out, ValueTag::kArray);
    }

    throw RuntimeError(std::string("unknown builtin: ").append(name));
}

void Vm::maybe_jit_compile(std::uint32_t func_index) {
    if (func_index >= module_.functions.size()) return;
    const FunctionInfo& fn = module_.functions[func_index];
    if (fn.entry_ip == kBuiltinEntryIp) return;

    std::uint32_t c = ++hot_counters_[func_index];
    if (c == kHotFuncThreshold && options_.jit_log) {
        std::cout << "HOT func " << func_name_view(func_index) << "@" << func_index << " count=" << c << "\n";
    }

    if (c >= kHotFuncThreshold && code_cache_.find(func_index) == code_cache_.end()) {
        if (!jit_compile(func_index)) {
            if (options_.jit_log) std::cout << "compile failed\n";
        }
    }
}

bool Vm::jit_compile(std::uint32_t func_index) {
    if (func_index >= module_.functions.size()) return false;
    const FunctionInfo& fn = module_.functions[func_index];
    if (fn.entry_ip == kBuiltinEntryIp) return false;
    if (func_index >= func_end_ip_.size()) return false;

    std::uint32_t entry = static_cast<std::uint32_t>(fn.entry_ip);
    std::uint32_t end = func_end_ip_[func_index];
    if (entry >= module_.code.size() || end > module_.code.size() || end < entry) return false;

    const auto& dt = Dispatch();

    CompiledFunc cf;
    cf.func_index = func_index;
    cf.entry_ip = entry;
    cf.end_ip = end;
    cf.handlers.resize(end - entry, nullptr);

    for (std::uint32_t ip = entry; ip < end; ++ip) {
        const Instruction& ins = module_.code[ip];
        std::size_t idx = static_cast<std::size_t>(ins.op);
        if (idx >= dt.size()) return false;
        Handler h = dt[idx];
        if (!h) return false;
        cf.handlers[ip - entry] = h;
    }

    auto ins_it = code_cache_.emplace(func_index, std::move(cf));
    if (!ins_it.second) return false;

    if (options_.jit_log) {
        const CompiledFunc& stored = ins_it.first->second;
        std::uintptr_t ptr = reinterpret_cast<std::uintptr_t>(stored.handlers.data());
        std::cout << "JIT COMPILED func " << func_name_view(func_index) << "@" << func_index << " entry=" << ptr << "\n";
    }

    return true;
}

void Vm::call_function(std::uint32_t func_index, std::uint16_t argc) {
    if (func_index >= module_.functions.size()) throw RuntimeError("CALL bad func index");
    const FunctionInfo& fn = module_.functions[func_index];
    if (argc != fn.param_count) throw RuntimeError("CALL argc mismatch");

    if (fn.entry_ip == kBuiltinEntryIp) {
        std::span<const Value> args = peek_args(argc);
        TempRoots guard(*this, args);

        bool has_ret = fn.return_type != TypeTag::kVoid;
        Value out = call_builtin(func_name_view(func_index), args, has_ret);

        stack_.resize(stack_.size() - argc);
        if (has_ret) push(out);
        return;
    }

    maybe_jit_compile(func_index);

    CallFrame& caller = frame();

    CallFrame callee;
    callee.func_index = func_index;
    callee.ip = static_cast<std::uint32_t>(fn.entry_ip);
    callee.return_ip = caller.ip;
    callee.base_stack_size = stack_.size() - argc;
    callee.locals.resize(fn.local_count, Value::Null());

    for (std::uint16_t i = argc; i > 0; --i) {
        callee.locals[static_cast<std::size_t>(i - 1)] = pop();
    }

    frames_.push_back(std::move(callee));
}

void Vm::ret_from_function() {
    if (frames_.empty()) throw RuntimeError("RET with no frame");

    CallFrame finished = frame();
    std::uint32_t finished_func = finished.func_index;

    bool has_ret = !is_void_return(finished_func);
    Value ret = Value::Null();
    if (has_ret) ret = pop();

    if (stack_.size() < finished.base_stack_size) throw RuntimeError("stack corrupted on return");
    stack_.resize(finished.base_stack_size);

    frames_.pop_back();
    if (frames_.empty()) return;

    CallFrame& caller = frame();
    caller.ip = finished.return_ip;

    if (has_ret) push(ret);
}

void Vm::step() {
    CallFrame& f = frame();
    if (f.ip >= module_.code.size()) throw RuntimeError("ip out of range");

    std::uint32_t ip_before = f.ip;
    const Instruction& ins = module_.code[ip_before];

    if (options_.trace) trace(ins, ip_before);

    f.ip += 1;

    const auto& dt = Dispatch();
    std::size_t idx = static_cast<std::size_t>(ins.op);
    if (idx >= dt.size() || !dt[idx]) throw RuntimeError("opcode not implemented");
    dt[idx](*this, ins);
}

void Vm::H_PushConst(Vm& vm, const Instruction& ins) {
    if (!ins.has_a) throw RuntimeError("PUSH_CONST missing a");
    if (ins.a >= vm.const_values_.size()) throw RuntimeError("PUSH_CONST const index out of range");
    vm.push(vm.const_values_[ins.a]);
}

void Vm::H_Pop(Vm& vm, const Instruction&) {
    (void)vm.pop();
}

void Vm::H_StoreGlobal(Vm& vm, const Instruction& ins) {
    if (!ins.has_a) throw RuntimeError("STORE_GLOBAL missing a");
    std::uint32_t idx = ins.a;
    if (idx >= vm.globals_values_.size()) throw RuntimeError("STORE_GLOBAL const index out of range");
    std::string_view name = vm.const_string_view(idx);
    vm.globals_values_[idx] = vm.pop();
    vm.globals_set_[idx] = 1;
    (void)name;
}

void Vm::H_LoadGlobal(Vm& vm, const Instruction& ins) {
    if (!ins.has_a) throw RuntimeError("LOAD_GLOBAL missing a");
    std::uint32_t idx = ins.a;
    if (idx >= vm.globals_values_.size()) throw RuntimeError("LOAD_GLOBAL const index out of range");
    if (!vm.globals_set_[idx]) {
        throw RuntimeError(std::string("LOAD_GLOBAL unknown global: ").append(vm.const_string_view(idx)));
    }
    vm.push(vm.globals_values_[idx]);
}

void Vm::H_StoreLocal(Vm& vm, const Instruction& ins) {
    if (!ins.has_b) throw RuntimeError("STORE_LOCAL missing b");
    CallFrame& f = vm.frame();
    std::size_t slot = ins.b;
    if (slot >= f.locals.size()) throw RuntimeError("STORE_LOCAL slot out of range");
    f.locals[slot] = vm.pop();
}

void Vm::H_LoadLocal(Vm& vm, const Instruction& ins) {
    if (!ins.has_b) throw RuntimeError("LOAD_LOCAL missing b");
    CallFrame& f = vm.frame();
    std::size_t slot = ins.b;
    if (slot >= f.locals.size()) throw RuntimeError("LOAD_LOCAL slot out of range");
    vm.push(f.locals[slot]);
}

void Vm::H_Add(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.add_values(l, r));
}

void Vm::H_Sub(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.sub_values(l, r));
}

void Vm::H_Mul(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.mul_values(l, r));
}

void Vm::H_Div(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.div_values(l, r));
}

void Vm::H_Mod(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.mod_values(l, r));
}

void Vm::H_Neg(Vm& vm, const Instruction&) {
    vm.push(vm.neg_value(vm.pop()));
}

void Vm::H_Eq(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.eq_values(l, r));
}

void Vm::H_Neq(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.neq_values(l, r));
}

void Vm::H_Lt(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.lt_values(l, r));
}

void Vm::H_Le(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.le_values(l, r));
}

void Vm::H_Gt(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.gt_values(l, r));
}

void Vm::H_Ge(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.ge_values(l, r));
}

void Vm::H_And(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.and_values(l, r));
}

void Vm::H_Or(Vm& vm, const Instruction&) {
    Value r = vm.pop();
    Value l = vm.pop();
    vm.push(vm.or_values(l, r));
}

void Vm::H_Not(Vm& vm, const Instruction&) {
    vm.push(vm.not_value(vm.pop()));
}

void Vm::H_Jump(Vm& vm, const Instruction& ins) {
    if (!ins.has_a) throw RuntimeError("JUMP missing a");
    CallFrame& f = vm.frame();
    if (ins.a >= vm.module_.code.size()) throw RuntimeError("JUMP target out of range");
    f.ip = ins.a;
}

void Vm::H_JumpFalse(Vm& vm, const Instruction& ins) {
    if (!ins.has_a) throw RuntimeError("JUMP_FALSE missing a");
    CallFrame& f = vm.frame();
    if (ins.a >= vm.module_.code.size()) throw RuntimeError("JUMP_FALSE target out of range");
    Value cond = vm.pop();
    if (cond.tag != ValueTag::kBool) throw RuntimeError("JUMP_FALSE expects bool");
    if (!cond.AsBool()) f.ip = ins.a;
}

void Vm::H_Call(Vm& vm, const Instruction& ins) {
    if (!ins.has_a || !ins.has_b) throw RuntimeError("CALL missing operands");
    vm.call_function(ins.a, ins.b);
}

void Vm::H_Ret(Vm& vm, const Instruction&) {
    vm.ret_from_function();
}

void Vm::H_NewArray(Vm& vm, const Instruction& ins) {
    if (!ins.has_b) throw RuntimeError("NEW_ARRAY missing b");
    std::size_t count = ins.b;

    ArrayObject* arr = vm.heap_.AllocateArray(count, vm.options_.gc_log, vm.Roots());
    for (std::int32_t i = static_cast<std::int32_t>(count) - 1; i >= 0; --i) {
        arr->elements[static_cast<std::size_t>(i)] = vm.pop();
    }
    vm.push(Value::FromRaw(arr, ValueTag::kArray));
}

void Vm::H_LoadIndex(Vm& vm, const Instruction&) {
    Value idx = vm.pop();
    Value arrv = vm.pop();
    if (idx.tag != ValueTag::kInt) throw RuntimeError("LOAD_INDEX expects int index");
    if (arrv.tag != ValueTag::kArray) throw RuntimeError("LOAD_INDEX expects array");
    std::int64_t i = idx.AsInt();
    ArrayObject* arr = arrv.AsArray();
    if (i < 0 || static_cast<std::size_t>(i) >= arr->elements.size()) throw RuntimeError("array index out of bounds");
    vm.push(arr->elements[static_cast<std::size_t>(i)]);
}

void Vm::H_StoreIndex(Vm& vm, const Instruction&) {
    Value val = vm.pop();
    Value idx = vm.pop();
    Value arrv = vm.pop();
    if (idx.tag != ValueTag::kInt) throw RuntimeError("STORE_INDEX expects int index");
    if (arrv.tag != ValueTag::kArray) throw RuntimeError("STORE_INDEX expects array");
    std::int64_t i = idx.AsInt();
    ArrayObject* arr = arrv.AsArray();
    if (i < 0 || static_cast<std::size_t>(i) >= arr->elements.size()) throw RuntimeError("array index out of bounds");
    arr->elements[static_cast<std::size_t>(i)] = val;
}
