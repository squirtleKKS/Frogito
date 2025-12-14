package lang.bytecodeGenerator;

import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.expression.*;
import lang.semantic.ast.node.statement.*;
import lang.semantic.bytecode.*;
import lang.semantic.symbols.FrogType;

import java.util.*;

import static lang.semantic.bytecode.OpCode.*;

public final class BytecodeGenerator {

    private final ConstantPool consts = new ConstantPool();
    private final List<Instruction> code = new ArrayList<>();

    private final Map<String, Integer> funcIndex = new HashMap<>();
    private final List<FunctionInfo> functions = new ArrayList<>();

    private final Set<String> globals = new HashSet<>();

    private Map<String, Integer> locals = null;
    private int nextLocalSlot = 0;

    private static final class LoopCtx {
        int startIp;
        List<Integer> breakJumps = new ArrayList<>();
        List<Integer> continueJumps = new ArrayList<>();
    }

    private final Deque<LoopCtx> loopStack = new ArrayDeque<>();

    public BytecodeModule generate(Program program) {
        registerBuiltin("print", 1);
        registerBuiltin("len", 1);
        registerBuiltin("new_array_bool", 2);
        registerBuiltin("push_int", 2);

        for (FunctionDeclStmt f : program.getFunctions()) {
            int nameIdx = consts.addString(f.getName());
            int idx = functions.size();
            funcIndex.put(f.getName(), idx);

            List<FrogType> paramTypes = new ArrayList<>();
            for (FunctionDeclStmt.Param p : f.getParams()) paramTypes.add(p.getType());

            functions.add(new FunctionInfo(
                    nameIdx,
                    f.getParams().size(),
                    0,
                    -1,
                    f.getReturnType(),
                    paramTypes
            ));
        }

        for (Statement st : program.getStatements()) {
            genStmtGlobal(st);
        }

        int jumpOverFuncs = emitJump(JUMP);

        for (FunctionDeclStmt f : program.getFunctions()) {
            Integer funcIdx = funcIndex.get(f.getName());
            if (funcIdx == null) {
                throw new IllegalStateException("Function index not found for " + f.getName());
            }

            int entry = code.size();

            enterFunctionScope(f);
            genBlock(f.getBody());

            if (f.getReturnType().equals(FrogType.VOID)) {
                code.add(Instruction.of(RET));
            }

            int localCount = nextLocalSlot;
            exitFunctionScope();

            FunctionInfo old = functions.get(funcIdx);
            functions.set(funcIdx, new FunctionInfo(
                    old.nameConstIndex,
                    old.paramCount,
                    localCount,
                    entry,
                    old.returnType,
                    old.paramTypes
            ));
        }

        int programExitIp = code.size();
        code.add(Instruction.of(RET));
        patchJump(jumpOverFuncs, programExitIp);

        return new BytecodeModule(consts, functions, code);
    }

    private void enterFunctionScope(FunctionDeclStmt f) {
        locals = new HashMap<>();
        nextLocalSlot = 0;
        for (FunctionDeclStmt.Param p : f.getParams()) {
            locals.put(p.getName(), nextLocalSlot++);
        }
    }

    private void exitFunctionScope() {
        locals = null;
        nextLocalSlot = 0;
    }

    private void genStmtGlobal(Statement st) {
        if (st instanceof BlockStmt b) {
            genBlock(b);
            return;
        }
        if (st instanceof VarDeclStmt v) {
            globals.add(v.getName());
            if (v.getInitializer() != null) genExpr(v.getInitializer());
            else pushDefault(v.getType());
            int nameIdx = consts.addString(v.getName());
            code.add(Instruction.a(STORE_GLOBAL, nameIdx));
            return;
        }
        genStmt(st);
    }

    private void genStmt(Statement st) {
        if (st instanceof VarDeclStmt v) {
            if (locals == null) {
                globals.add(v.getName());
                if (v.getInitializer() != null) genExpr(v.getInitializer());
                else pushDefault(v.getType());
                int nameIdx = consts.addString(v.getName());
                code.add(Instruction.a(STORE_GLOBAL, nameIdx));
                return;
            }
            int slot = declareLocal(v.getName());
            if (v.getInitializer() != null) genExpr(v.getInitializer());
            else pushDefault(v.getType());
            code.add(Instruction.b(STORE_LOCAL, slot));
        }
        else if (st instanceof ExprStmt e) {
            genExpr(e.getExpression());
            if (!e.getExpression().getType().equals(FrogType.VOID)) {
                code.add(Instruction.of(POP));
            }
        }
        else if (st instanceof IndexAssignStmt ia) {
            genIndexAssign(ia);
        }
        else if (st instanceof BlockStmt b) {
            genBlock(b);
        }
        else if (st instanceof IfStmt i) {
            genIf(i);
        }
        else if (st instanceof WhileStmt w) {
            genWhile(w);
        }
        else if (st instanceof ForStmt f) {
            genFor(f);
        }
        else if (st instanceof ReturnStmt r) {
            genReturn(r);
        }
        else if (st instanceof BreakStmt) {
            genBreak();
        }
        else if (st instanceof ContinueStmt) {
            genContinue();
        }
        else if (st instanceof FunctionDeclStmt) {
        }
        else {
            throw new IllegalStateException("Unknown stmt: " + st.getClass());
        }
    }

    private void genBlock(BlockStmt b) {
        for (Statement s : b.getStatements()) {
            if (locals == null) genStmtGlobal(s);
            else genStmt(s);
        }
    }

    private int declareLocal(String name) {
        int slot = nextLocalSlot++;
        locals.put(name, slot);
        return slot;
    }

    private Integer resolveLocal(String name) {
        return locals.get(name);
    }

    private void genIf(IfStmt i) {
        genExpr(i.getCondition());
        int jFalse = emitJump(JUMP_FALSE);

        genStmt(i.getThenBranch());
        int jEnd = emitJump(JUMP);

        patchJump(jFalse, code.size());

        if (i.getElseBranch() != null) {
            genStmt(i.getElseBranch());
        }

        patchJump(jEnd, code.size());
    }

    private void genWhile(WhileStmt w) {
        LoopCtx ctx = new LoopCtx();
        ctx.startIp = code.size();
        loopStack.push(ctx);

        genExpr(w.getCondition());
        int jFalse = emitJump(JUMP_FALSE);

        genStmt(w.getBody());
        code.add(Instruction.a(JUMP, ctx.startIp));

        int endIp = code.size();
        patchJump(jFalse, endIp);

        for (int br : ctx.breakJumps) patchJump(br, endIp);
        for (int cont : ctx.continueJumps) patchJump(cont, ctx.startIp);

        loopStack.pop();
    }

    private void genFor(ForStmt f) {
        LoopCtx ctx = new LoopCtx();
        loopStack.push(ctx);

        if (f.getInitializer() != null) genStmt(f.getInitializer());

        ctx.startIp = code.size();

        int jFalse = -1;
        if (f.getCondition() != null) {
            genExpr(f.getCondition());
            jFalse = emitJump(JUMP_FALSE);
        }

        genStmt(f.getBody());

        int continueIp = code.size();
        if (f.getIncrement() != null) {
            genExpr(f.getIncrement());
            code.add(Instruction.of(POP));
        }
        code.add(Instruction.a(JUMP, ctx.startIp));

        int endIp = code.size();
        if (jFalse != -1) patchJump(jFalse, endIp);

        for (int br : ctx.breakJumps) patchJump(br, endIp);
        for (int cont : ctx.continueJumps) patchJump(cont, continueIp);

        loopStack.pop();
    }

    private void genReturn(ReturnStmt r) {
        if (r.getValue() != null) genExpr(r.getValue());
        code.add(Instruction.of(RET));
    }

    private void genIndexAssign(IndexAssignStmt ia) {
        genExpr(ia.getTarget().getArray());
        genExpr(ia.getTarget().getIndex());
        genExpr(ia.getValue());
        code.add(Instruction.of(STORE_INDEX));
    }


    private void genBreak() {
        LoopCtx ctx = loopStack.peek();
        if (ctx == null) throw new IllegalStateException("break outside loop in codegen");
        int j = emitJump(JUMP);
        ctx.breakJumps.add(j);
    }

    private void genContinue() {
        LoopCtx ctx = loopStack.peek();
        if (ctx == null) throw new IllegalStateException("continue outside loop in codegen");
        int j = emitJump(JUMP);
        ctx.continueJumps.add(j);
    }

    private void genExpr(Expression e) {
        if (e instanceof LiteralExpr lit) {
            genLiteral(lit);
        }
        else if (e instanceof VarExpr v) {
            genVar(v);
        }
        else if (e instanceof AssignExpr a) {
            genAssign(a);
        }
        else if (e instanceof UnaryExpr u) {
            genUnary(u);
        }
        else if (e instanceof BinaryExpr b) {
            genBinary(b);
        }
        else if (e instanceof CallExpr c) {
            genCall(c);
        }
        else if (e instanceof IndexExpr idx) {
            genIndex(idx);
        }
        else if (e instanceof ArrayLiteralExpr arr) {
            genArrayLiteral(arr);
        }
        else {
            throw new IllegalStateException("Unknown expr: " + e.getClass());
        }
    }

    private void genLiteral(LiteralExpr lit) {
        Object v = lit.getValue();
        int idx;
        if (v instanceof Integer i) idx = consts.addInt(i);
        else if (v instanceof Double d) idx = consts.addFloat(d);
        else if (v instanceof Boolean b) idx = consts.addBool(b);
        else idx = consts.addString(v.toString());
        code.add(Instruction.a(PUSH_CONST, idx));
    }

    private void genVar(VarExpr v) {
        Integer slot = locals != null ? resolveLocal(v.getName()) : null;
        if (slot != null) {
            code.add(Instruction.b(LOAD_LOCAL, slot));
        } else {
            int nameIdx = consts.addString(v.getName());
            code.add(Instruction.a(LOAD_GLOBAL, nameIdx));
        }
    }

    private void genAssign(AssignExpr a) {
        genExpr(a.getValue());
        Integer slot = locals != null ? resolveLocal(a.getName()) : null;
        if (slot != null) {
            code.add(Instruction.b(STORE_LOCAL, slot));
            code.add(Instruction.b(LOAD_LOCAL, slot));
        } else {
            int nameIdx = consts.addString(a.getName());
            code.add(Instruction.a(STORE_GLOBAL, nameIdx));
            code.add(Instruction.a(LOAD_GLOBAL, nameIdx));
        }
    }

    private void genUnary(UnaryExpr u) {
        genExpr(u.getExpr());
        switch (u.getOp()) {
            case NEGATE -> code.add(Instruction.of(NEG));
            case NOT -> code.add(Instruction.of(NOT));
            default -> throw new AssertionError();
        }
    }

    private void genBinary(BinaryExpr b) {
        genExpr(b.getLeft());
        genExpr(b.getRight());

        switch (b.getOp()) {
            case PLUS -> code.add(Instruction.of(ADD));
            case MINUS -> code.add(Instruction.of(SUB));
            case MUL -> code.add(Instruction.of(MUL));
            case DIV -> code.add(Instruction.of(DIV));
            case MOD -> code.add(Instruction.of(MOD));

            case EQ -> code.add(Instruction.of(EQ));
            case NEQ -> code.add(Instruction.of(NEQ));
            case LT -> code.add(Instruction.of(LT));
            case LE -> code.add(Instruction.of(LE));
            case GT -> code.add(Instruction.of(GT));
            case GE -> code.add(Instruction.of(GE));

            case AND -> code.add(Instruction.of(AND));
            case OR -> code.add(Instruction.of(OR));
            default -> throw new AssertionError();
        }
    }

    private void genCall(CallExpr c) {
        for (Expression arg : c.getArgs()) genExpr(arg);

        Integer idx = funcIndex.get(c.getCallee());
        if (idx == null) throw new IllegalStateException("Unknown function in codegen: " + c.getCallee());

        code.add(Instruction.ab(CALL, idx, c.getArgs().size()));
    }

    private void genIndex(IndexExpr idx) {
        genExpr(idx.getArray());
        genExpr(idx.getIndex());
        code.add(Instruction.of(LOAD_INDEX));
    }

    private void genArrayLiteral(ArrayLiteralExpr arr) {
        for (Expression el : arr.getElements()) genExpr(el);
        code.add(Instruction.b(NEW_ARRAY, arr.getElements().size()));
    }

    private void pushDefault(FrogType t) {
        int cIdx;
        switch (t.getKind()) {
            case INT -> cIdx = consts.addInt(0);
            case FLOAT -> cIdx = consts.addFloat(0.0);
            case BOOL -> cIdx = consts.addBool(false);
            case STRING -> cIdx = consts.addString("");
            case ARRAY -> {
                code.add(Instruction.b(NEW_ARRAY, 0));
                return;
            }
            case VOID -> cIdx = consts.addInt(0);
            default -> throw new AssertionError();
        }
        code.add(Instruction.a(PUSH_CONST, cIdx));
    }

    private int emitJump(OpCode op) {
        Instruction i = Instruction.a(op, -1);
        code.add(i);
        return code.size() - 1;
    }

    private void patchJump(int atIndex, int targetIp) {
        code.get(atIndex).a = targetIp;
    }
    private void registerBuiltin(String name, int paramCount) {
        int nameIdx = consts.addString(name);
        int idx = functions.size();
        funcIndex.put(name, idx);

        List<FrogType> paramTypes = new ArrayList<>();
        for (int i = 0; i < paramCount; i++) {
            paramTypes.add(FrogType.VOID);
        }

        functions.add(new FunctionInfo(
                nameIdx,
                paramCount,
                0,
                -1,
                FrogType.VOID,
                paramTypes
        ));
    }
}
