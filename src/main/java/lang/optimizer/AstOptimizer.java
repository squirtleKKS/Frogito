package lang.optimizer;


import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.expression.*;
import lang.semantic.ast.node.expression.operations.BinaryOp;
import lang.semantic.ast.node.expression.operations.UnaryOp;
import lang.semantic.ast.node.statement.*;
import lang.semantic.symbols.FrogType;
import lang.semantic.symbols.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class AstOptimizer {

    public Program optimize(Program program) {
        List<FunctionDeclStmt> newFuncs = new ArrayList<>();
        for (FunctionDeclStmt f : program.getFunctions()) {
            newFuncs.add(optimizeFunction(f));
        }

        List<Statement> newStmts = new ArrayList<>();
        for (Statement s : program.getStatements()) {
            Statement opt = optimizeStmt(s);
            if (opt != null) newStmts.add(opt);
        }

        return new Program(newFuncs, newStmts, program.getLocation());
    }

    private FunctionDeclStmt optimizeFunction(FunctionDeclStmt f) {
        BlockStmt bodyOpt = (BlockStmt) optimizeStmt(f.getBody());
        return new FunctionDeclStmt(
                f.getName(),
                f.getParams(),
                f.getReturnType(),
                bodyOpt,
                f.getLocation()
        );
    }

    private Statement optimizeStmt(Statement st) {
        if (st instanceof VarDeclStmt v) {
            Expression initOpt = v.getInitializer() == null ? null : optimizeExpr(v.getInitializer());
            VarDeclStmt nv = new VarDeclStmt(v.getType(), v.getName(), initOpt, v.getLocation());
            return nv;
        }

        if (st instanceof ExprStmt e) {
            Expression exprOpt = optimizeExpr(e.getExpression());
            return new ExprStmt(exprOpt, e.getLocation());
        }

        if (st instanceof BlockStmt b) {
            return optimizeBlock(b);
        }

        if (st instanceof IfStmt i) {
            return optimizeIf(i);
        }

        if (st instanceof WhileStmt w) {
            Expression condOpt = optimizeExpr(w.getCondition());
            Statement bodyOpt = optimizeStmt(w.getBody());
            if (bodyOpt == null) bodyOpt = new BlockStmt(List.of(), w.getBody().getLocation());
            if (isBoolLiteral(condOpt, false)) {
                return null;
            }

            return new WhileStmt(condOpt, bodyOpt, w.getLocation());
        }

        if (st instanceof ForStmt f) {
            Statement initOpt = f.getInitializer() == null ? null : optimizeStmt(f.getInitializer());
            Expression condOpt = f.getCondition() == null ? null : optimizeExpr(f.getCondition());
            Expression incOpt = f.getIncrement() == null ? null : optimizeExpr(f.getIncrement());
            Statement bodyOpt = optimizeStmt(f.getBody());
            if (bodyOpt == null) bodyOpt = new BlockStmt(List.of(), f.getBody().getLocation());
            if (condOpt != null && isBoolLiteral(condOpt, false)) {
                if (initOpt != null) return initOpt;
                return null;
            }

            return new ForStmt(initOpt, condOpt, incOpt, bodyOpt, f.getLocation());
        }

        if (st instanceof ReturnStmt r) {
            Expression valOpt = r.getValue() == null ? null : optimizeExpr(r.getValue());
            ReturnStmt nr = new ReturnStmt(valOpt, r.getLocation());
            nr.setExpectedType(r.getExpectedType());
            return nr;
        }

        if (st instanceof BreakStmt || st instanceof ContinueStmt) {
            return st;
        }

        if (st instanceof FunctionDeclStmt) {
            return st;
        }

        return st;
    }

    private BlockStmt optimizeBlock(BlockStmt b) {
        List<Statement> out = new ArrayList<>();

        for (Statement s : b.getStatements()) {
            Statement opt = optimizeStmt(s);
            if (opt != null) {
                out.add(opt);
                if (opt instanceof ReturnStmt) {
                    break;
                }
            }
        }

        return new BlockStmt(out, b.getLocation());
    }

    private Statement optimizeIf(IfStmt i) {
        Expression condOpt = optimizeExpr(i.getCondition());
        Statement thenOpt = optimizeStmt(i.getThenBranch());
        Statement elseOpt = i.getElseBranch() == null ? null : optimizeStmt(i.getElseBranch());
        if (isBoolLiteral(condOpt, true)) {
            return thenOpt;
        }
        if (isBoolLiteral(condOpt, false)) {
            return elseOpt;
        }

        if (thenOpt == null) thenOpt = new BlockStmt(List.of(), i.getThenBranch().getLocation());

        return new IfStmt(condOpt, thenOpt, elseOpt, i.getLocation());
    }

    private Expression optimizeExpr(Expression e) {
        if (e instanceof LiteralExpr || e instanceof VarExpr) return e;

        if (e instanceof AssignExpr a) {
            Expression valOpt = optimizeExpr(a.getValue());
            AssignExpr na = new AssignExpr(a.getName(), valOpt, a.getLocation());
            na.setType(e.getType());
            return na;
        }

        if (e instanceof UnaryExpr u) {
            Expression inner = optimizeExpr(u.getExpr());

            if (inner instanceof LiteralExpr lit) {
                Object v = lit.getValue();
                Expression folded = foldUnary(u.getOp(), v, u.getLocation(), e.getType());
                if (folded != null) return folded;
            }

            UnaryExpr nu = new UnaryExpr(u.getOp(), inner, u.getLocation());
            nu.setType(e.getType());
            return nu;
        }

        if (e instanceof BinaryExpr b) {
            Expression L = optimizeExpr(b.getLeft());
            Expression R = optimizeExpr(b.getRight());
            if (L instanceof LiteralExpr lLit && R instanceof LiteralExpr rLit) {
                Expression folded = foldBinary(b.getOp(), lLit, rLit, b.getLocation(), e.getType());
                if (folded != null) return folded;
            }
            Expression simplified = simplifyAlgebra(b.getOp(), L, R, b.getLocation(), e.getType());
            if (simplified != null) return simplified;

            BinaryExpr nb = new BinaryExpr(L, b.getOp(), R, b.getLocation());
            nb.setType(e.getType());
            return nb;
        }

        if (e instanceof CallExpr c) {
            List<Expression> argsOpt = new ArrayList<>();
            for (Expression arg : c.getArgs()) argsOpt.add(optimizeExpr(arg));
            CallExpr nc = new CallExpr(c.getCallee(), argsOpt, c.getLocation());
            nc.setType(e.getType());
            return nc;
        }

        if (e instanceof IndexExpr idx) {
            Expression arrOpt = optimizeExpr(idx.getArray());
            Expression indOpt = optimizeExpr(idx.getIndex());
            IndexExpr ni = new IndexExpr(arrOpt, indOpt, idx.getLocation());
            ni.setType(e.getType());
            return ni;
        }

        if (e instanceof ArrayLiteralExpr arr) {
            List<Expression> elemsOpt = new ArrayList<>();
            for (Expression el : arr.getElements()) elemsOpt.add(optimizeExpr(el));
            ArrayLiteralExpr na = new ArrayLiteralExpr(elemsOpt, arr.getLocation());
            na.setType(e.getType());
            return na;
        }

        return e;
    }

    private Expression foldUnary(UnaryOp op, Object v, SourceLocation loc, FrogType type) {
        try {
            switch (op) {
                case NEGATE -> {
                    if (v instanceof Integer i) {
                        LiteralExpr e = new LiteralExpr(-i, loc);
                        e.setType(type);
                        return e;
                    }
                    if (v instanceof Double d) {
                        LiteralExpr e = new LiteralExpr(-d, loc);
                        e.setType(type);
                        return e;
                    }
                }
                case NOT -> {
                    if (v instanceof Boolean b) {
                        LiteralExpr e = new LiteralExpr(!b, loc);
                        e.setType(type);
                        return e;
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Expression foldBinary(BinaryOp op,
                                  LiteralExpr lLit,
                                  LiteralExpr rLit,
                                  SourceLocation loc,
                                  FrogType resultType) {
        Object lv = lLit.getValue();
        Object rv = rLit.getValue();

        try {
            switch (op) {
                case PLUS -> {
                    if (lv instanceof Integer a && rv instanceof Integer b) {
                        return litInt(a + b, loc, resultType);
                    }
                    if (lv instanceof Double a && rv instanceof Double b) {
                        return litFloat(a + b, loc, resultType);
                    }
                    if (lv instanceof String a && rv instanceof String b) {
                        return litString(a + b, loc, resultType);
                    }
                }
                case MINUS -> {
                    if (lv instanceof Integer a && rv instanceof Integer b) {
                        return litInt(a - b, loc, resultType);
                    }
                    if (lv instanceof Double a && rv instanceof Double b) {
                        return litFloat(a - b, loc, resultType);
                    }
                }
                case MUL -> {
                    if (lv instanceof Integer a && rv instanceof Integer b) {
                        return litInt(a * b, loc, resultType);
                    }
                    if (lv instanceof Double a && rv instanceof Double b) {
                        return litFloat(a * b, loc, resultType);
                    }
                }
                case DIV -> {
                    if (lv instanceof Integer a && rv instanceof Integer b) {
                        return litInt(a / b, loc, resultType);
                    }
                    if (lv instanceof Double a && rv instanceof Double b) {
                        return litFloat(a / b, loc, resultType);
                    }
                }
                case MOD -> {
                    if (lv instanceof Integer a && rv instanceof Integer b) {
                        return litInt(a % b, loc, resultType);
                    }
                }

                case LT -> { return litBool(compare(lv, rv) < 0, loc, resultType); }
                case LE -> { return litBool(compare(lv, rv) <= 0, loc, resultType); }
                case GT -> { return litBool(compare(lv, rv) > 0, loc, resultType); }
                case GE -> { return litBool(compare(lv, rv) >= 0, loc, resultType); }
                case EQ -> { return litBool(lv.equals(rv), loc, resultType); }
                case NEQ -> { return litBool(!lv.equals(rv), loc, resultType); }
                case AND -> {
                    if (lv instanceof Boolean a && rv instanceof Boolean b) {
                        return litBool(a && b, loc, resultType);
                    }
                }
                case OR -> {
                    if (lv instanceof Boolean a && rv instanceof Boolean b) {
                        return litBool(a || b, loc, resultType);
                    }
                }
            }
        } catch (Exception ignored) { }

        return null;
    }

    private int compare(Object lv, Object rv) {
        if (lv instanceof Integer a && rv instanceof Integer b) return Integer.compare(a, b);
        if (lv instanceof Double a && rv instanceof Double b) return Double.compare(a, b);
        if (lv instanceof String a && rv instanceof String b) return a.compareTo(b);
        if (lv instanceof Boolean a && rv instanceof Boolean b) return Boolean.compare(a, b);
        if (lv instanceof Comparable ca && rv instanceof Comparable cb) return ca.compareTo(cb);
        throw new IllegalArgumentException("Not comparable");
    }

    private Expression simplifyAlgebra(BinaryOp op,
                                       Expression L,
                                       Expression R,
                                       SourceLocation loc,
                                       FrogType resultType) {

        boolean L0 = isNumberLiteral(L, 0);
        boolean R0 = isNumberLiteral(R, 0);
        boolean L1 = isNumberLiteral(L, 1);
        boolean R1 = isNumberLiteral(R, 1);

        switch (op) {
            case PLUS -> {
                if (R0) return L;
                if (L0) return R;
            }
            case MINUS -> {
                if (R0) return L;
            }
            case MUL -> {
                if (R1) return L;
                if (L1) return R;
                if (R0) return R;
                if (L0) return L;
            }
            case DIV -> {
                if (R1) return L;
            }
        }
        return null;
    }

    private boolean isNumberLiteral(Expression e, int value) {
        if (!(e instanceof LiteralExpr lit)) return false;
        Object v = lit.getValue();
        if (v instanceof Integer i) return i == value;
        if (v instanceof Double d) return d == value;
        return false;
    }

    private boolean isBoolLiteral(Expression e, boolean value) {
        if (!(e instanceof LiteralExpr lit)) return false;
        Object v = lit.getValue();
        return (v instanceof Boolean b) && b == value;
    }

    private LiteralExpr litInt(int v, SourceLocation loc, FrogType t) {
        LiteralExpr e = new LiteralExpr(v, loc);
        e.setType(t);
        return e;
    }

    private LiteralExpr litFloat(double v, SourceLocation loc, FrogType t) {
        LiteralExpr e = new LiteralExpr(v, loc);
        e.setType(t);
        return e;
    }

    private LiteralExpr litBool(boolean v, SourceLocation loc, FrogType t) {
        LiteralExpr e = new LiteralExpr(v, loc);
        e.setType(t);
        return e;
    }

    private LiteralExpr litString(String v, SourceLocation loc, FrogType t) {
        LiteralExpr e = new LiteralExpr(v, loc);
        e.setType(t);
        return e;
    }
}
