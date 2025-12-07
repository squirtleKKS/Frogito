package lang.bytecodeGenerator;

import lang.semantic.bytecode.BytecodeModule;
import lang.semantic.bytecode.FunctionInfo;
import lang.semantic.bytecode.Instruction;
import lang.semantic.bytecode.OpCode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Disassembler {

    public static void dump(BytecodeModule m) {
        var pool = m.constPool.getPool();
        Map<Integer, String> labels = new HashMap<>();
        AtomicInteger counter = new AtomicInteger();

        for (int ip = 0; ip < m.code.size(); ip++) {
            Instruction ins = m.code.get(ip);
            if ((ins.op == OpCode.JUMP || ins.op == OpCode.JUMP_FALSE) && ins.hasA) {
                int target = ins.a;
                labels.computeIfAbsent(target, k -> "L" + (counter.getAndIncrement()));
            }
        }

        System.out.println("=== CODE (disasm) ===");

        for (int ip = 0; ip < m.code.size(); ip++) {
            if (labels.containsKey(ip)) {
                System.out.println(labels.get(ip) + ":");
            }

            Instruction ins = m.code.get(ip);
            System.out.printf("%04d  %s", ip, ins.op);

            if (ins.hasA || ins.hasB) System.out.print(" ");

            if (ins.hasA) {
                if (ins.op == OpCode.LOAD_GLOBAL || ins.op == OpCode.STORE_GLOBAL) {
                    var c = pool.get(ins.a);
                    System.out.print("\"" + c.value + "\"");
                } else if (ins.op == OpCode.PUSH_CONST) {
                    var c = pool.get(ins.a);
                    System.out.print(c.tag + "(" + c.value + ")");
                } else if (ins.op == OpCode.CALL) {
                    FunctionInfo f = m.functions.get(ins.a);
                    String name = (String) pool.get(f.nameConstIndex).value;
                    System.out.print(name + "@" + ins.a);
                } else if (ins.op == OpCode.JUMP || ins.op == OpCode.JUMP_FALSE) {
                    System.out.print(labels.getOrDefault(ins.a, String.valueOf(ins.a)));
                } else {
                    System.out.print(ins.a);
                }
            }

            if (ins.hasB) {
                if (ins.hasA) System.out.print(", ");
                System.out.print(ins.b & 0xFFFF);
            }

            System.out.println();
        }
    }

    private Disassembler() {}
}
