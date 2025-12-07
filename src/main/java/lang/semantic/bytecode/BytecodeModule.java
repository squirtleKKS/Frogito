package lang.semantic.bytecode;

import java.util.List;

public final class BytecodeModule {
    public final ConstantPool constPool;
    public final List<FunctionInfo> functions;
    public final List<Instruction> code;

    public BytecodeModule(ConstantPool constPool,
                          List<FunctionInfo> functions,
                          List<Instruction> code) {
        this.constPool = constPool;
        this.functions = functions;
        this.code = code;
    }
}
