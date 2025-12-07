package lang.bytecodeGenerator;

import lang.semantic.bytecode.*;
import lang.semantic.symbols.FrogType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FrogcWriter {

    public static void write(BytecodeModule m, OutputStream out) throws IOException {
        DataOutputStream d = new DataOutputStream(out);

        // ---- Header ----
        d.writeBytes("FROG");          // magic 4 bytes
        d.writeShort(1);               // version u16

        d.writeInt(m.constPool.getPool().size());  // constCount u32
        d.writeInt(m.functions.size());            // funcCount u32
        d.writeInt(m.code.size());                 // codeSize u32 (instructions)

        // ---- ConstantPool ----
        for (ConstantPool.Const c : m.constPool.getPool()) {
            d.writeByte(tagToByte(c.tag));
            switch (c.tag) {
                case INT -> d.writeInt((Integer) c.value);
                case FLOAT -> d.writeDouble((Double) c.value);
                case BOOL -> d.writeByte(((Boolean) c.value) ? 1 : 0);
                case STRING -> {
                    byte[] bytes = ((String) c.value).getBytes(StandardCharsets.UTF_8);
                    d.writeInt(bytes.length);
                    d.write(bytes);
                }
            }
        }

        // ---- FunctionTable ----
        for (FunctionInfo f : m.functions) {
            d.writeInt(f.nameConstIndex);
            d.writeShort(f.paramCount);
            d.writeShort(f.localCount);
            d.writeInt(f.entryIp);
            d.writeByte(typeToByte(f.returnType));

            List<FrogType> ptypes = f.paramTypes;
            for (FrogType pt : ptypes) {
                d.writeByte(typeToByte(pt));
            }
        }

        // ---- CodeSection ----
        for (Instruction ins : m.code) {
            d.writeByte(opToByte(ins.op));
            // encoding: [op][flags][a?][b?]
            byte flags = 0;
            if (ins.hasA) flags |= 1;
            if (ins.hasB) flags |= 2;
            d.writeByte(flags);

            if (ins.hasA) d.writeInt(ins.a);
            if (ins.hasB) d.writeShort(ins.b);
        }
        d.flush();
    }

    private static byte tagToByte(ConstantPool.Tag t) {
        return switch (t) {
            case INT -> 1;
            case FLOAT -> 2;
            case BOOL -> 3;
            case STRING -> 4;
        };
    }

    private static byte typeToByte(FrogType t) {
        return switch (t.getKind()) {
            case INT -> 1;
            case FLOAT -> 2;
            case BOOL -> 3;
            case STRING -> 4;
            case VOID -> 5;
            case ARRAY -> 6;
        };
    }

    private static byte opToByte(OpCode op) {
        return (byte) op.ordinal();
    }
}
