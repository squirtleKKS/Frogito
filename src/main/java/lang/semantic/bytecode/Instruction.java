package lang.semantic.bytecode;

public final class Instruction {

    public final OpCode op;
    public int a;     // u32/int operand (address, const index, func index, global name index)
    public short b;   // u16 operand  (local slot, argc, array count)
    public boolean hasA;
    public boolean hasB;

    private Instruction(OpCode op) {
        this.op = op;
    }

    public static Instruction of(OpCode op) {
        return new Instruction(op);
    }

    public static Instruction a(OpCode op, int a) {
        Instruction i = new Instruction(op);
        i.a = a;
        i.hasA = true;
        return i;
    }

    public static Instruction b(OpCode op, int b) {
        Instruction i = new Instruction(op);
        i.b = (short) b;
        i.hasB = true;
        return i;
    }

    public static Instruction ab(OpCode op, int a, int b) {
        Instruction i = new Instruction(op);
        i.a = a;
        i.b = (short) b;
        i.hasA = true;
        i.hasB = true;
        return i;
    }

    @Override
    public String toString() {
        if (hasA && hasB) return op + " " + a + " " + (b & 0xFFFF);
        if (hasA) return op + " " + a;
        if (hasB) return op + " " + (b & 0xFFFF);
        return op.toString();
    }
}
