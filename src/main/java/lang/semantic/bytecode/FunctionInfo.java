package lang.semantic.bytecode;

import lang.semantic.symbols.FrogType;

import java.util.List;

public final class FunctionInfo {
    public final int nameConstIndex;        // STRING in const pool
    public final short paramCount;
    public final short localCount;
    public final int entryIp;              // индекс инструкции, куда прыгать
    public final FrogType returnType;
    public final List<FrogType> paramTypes;

    public FunctionInfo(int nameConstIndex,
                        int paramCount,
                        int localCount,
                        int entryIp,
                        FrogType returnType,
                        List<FrogType> paramTypes) {
        this.nameConstIndex = nameConstIndex;
        this.paramCount = (short) paramCount;
        this.localCount = (short) localCount;
        this.entryIp = entryIp;
        this.returnType = returnType;
        this.paramTypes = List.copyOf(paramTypes);
    }
}
