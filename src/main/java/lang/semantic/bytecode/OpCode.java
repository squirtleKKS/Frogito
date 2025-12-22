package lang.semantic.bytecode;

public enum OpCode {
    // constants
    PUSH_CONST,   // u32 constIndex

    // locals
    LOAD_LOCAL,   // u16 slot
    STORE_LOCAL,  // u16 slot

    // globals
    LOAD_GLOBAL,  // u32 nameConstIndex
    STORE_GLOBAL, // u32 nameConstIndex

    // arithmetic
    ADD, SUB, MUL, DIV, MOD, NEG,

    // comparisons / logic
    EQ, NEQ, LT, LE, GT, GE,
    AND, OR, NOT,

    // jumps
    JUMP,         // u32 ip
    JUMP_FALSE,   // u32 ip

    // functions
    CALL,         // u32 funcIndex, u16 argc
    RET,

    // arrays
    NEW_ARRAY,    // u16 count  (pop count elems, push array)
    NEW_ARRAY_SIZED,
    LOAD_INDEX,   // pop index, pop array, push elem
    STORE_INDEX,  // pop value, pop index, pop array (mutate)

    // misc
    POP           // pop top (например, ExprStmt или void call)
}
