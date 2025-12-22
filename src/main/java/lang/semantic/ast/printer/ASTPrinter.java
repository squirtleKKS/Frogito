package lang.semantic.ast.printer;

import java.util.List;

import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.expression.ArrayLiteralExpr;
import lang.semantic.ast.node.expression.AssignExpr;
import lang.semantic.ast.node.expression.BinaryExpr;
import lang.semantic.ast.node.expression.CallExpr;
import lang.semantic.ast.node.expression.IndexExpr;
import lang.semantic.ast.node.expression.LiteralExpr;
import lang.semantic.ast.node.expression.UnaryExpr;
import lang.semantic.ast.node.expression.VarExpr;
import lang.semantic.ast.node.statement.BlockStmt;
import lang.semantic.ast.node.statement.BreakStmt;
import lang.semantic.ast.node.statement.ContinueStmt;
import lang.semantic.ast.node.statement.ExprStmt;
import lang.semantic.ast.node.statement.ForStmt;
import lang.semantic.ast.node.statement.FunctionDeclStmt;
import lang.semantic.ast.node.statement.IfStmt;
import lang.semantic.ast.node.statement.IndexAssignStmt;
import lang.semantic.ast.node.statement.ReturnStmt;
import lang.semantic.ast.node.statement.VarDeclStmt;
import lang.semantic.ast.node.statement.WhileStmt;

public class ASTPrinter {

    private int indent = 0;

    private void pad() {
        System.out.print("  ".repeat(Math.max(0, indent)));
    }

    public void printProgram(Program program) {
        System.out.println("Program:");

        indent++;
        printFunctions(program.getFunctions());
        printStatements(program.getStatements());
        indent--;
    }

    private void printFunctions(List<FunctionDeclStmt> funcs) {
        pad(); System.out.println("Functions:");
        indent++;

        for (FunctionDeclStmt f : funcs) {
            pad();
            System.out.println("Function " + f.getName() + " : " + f.getReturnType());
            indent++;

            for (FunctionDeclStmt.Param p : f.getParams()) {
                pad();
                System.out.println("Param " + p.getName() + " : " + p.getType());
            }

            printBlock(f.getBody());

            indent--;
        }

        indent--;
    }

    private void printStatements(List<Statement> stmts) {
        pad(); System.out.println("Statements:");
        indent++;

        for (Statement st : stmts) {
            printStatement(st);
        }

        indent--;
    }

    private void printBlock(BlockStmt block) {
        pad(); System.out.println("Block:");
        indent++;

        for (Statement s : block.getStatements()) {
            printStatement(s);
        }

        indent--;
    }

    private void printStatement(Statement st) {
        if (st instanceof VarDeclStmt v) {
            pad();
            System.out.println("VarDecl " + v.getName() + " : " + v.getType());
            indent++;
            if (v.getInitializer() != null) {
                printExpression(v.getInitializer());
            }
            indent--;
        } else if (st instanceof ExprStmt e) {
            pad(); System.out.println("ExprStmt:");
            indent++;
            printExpression(e.getExpression());
            indent--;
        } else if (st instanceof IfStmt i) {
            pad(); System.out.println("IfStmt:");
            indent++;
            printExpression(i.getCondition());
            printStatement(i.getThenBranch());
            if (i.getElseBranch() != null) {
                printStatement(i.getElseBranch());
            }
            indent--;
        } else if (st instanceof ForStmt f) {
            pad(); System.out.println("ForStmt:");
            indent++;
            if (f.getInitializer() != null) printStatement(f.getInitializer());
            if (f.getCondition() != null) printExpression(f.getCondition());
            if (f.getIncrement() != null) printExpression(f.getIncrement());
            printStatement(f.getBody());
            indent--;
        } else if (st instanceof WhileStmt w) {
            pad(); System.out.println("WhileStmt:");
            indent++;
            printExpression(w.getCondition());
            printStatement(w.getBody());
            indent--;
        } else if (st instanceof ReturnStmt r) {
            pad(); System.out.println("Return:");
            indent++;
            if (r.getValue() != null) printExpression(r.getValue());
            indent--;
        } else if (st instanceof BreakStmt) {
            pad(); System.out.println("Break;");
        } else if (st instanceof ContinueStmt) {
            pad(); System.out.println("Continue;");
        } else if (st instanceof IndexAssignStmt ia) {
            pad();
            System.out.println("IndexAssign:");
            indent++;
            printExpression(ia.getTarget());
            printExpression(ia.getValue());
            indent--;
        } else if (st instanceof BlockStmt b) {
            printBlock(b);
        } else {
            pad(); System.out.println("Unknown statement: " + st.getClass());
        }
    }

    private void printExpression(Expression e) {
        if (e instanceof LiteralExpr lit) {
            pad(); System.out.println("Literal: " + lit.getValue());
        } else if (e instanceof VarExpr v) {
            pad(); System.out.println("Var: " + v.getName());
        } else if (e instanceof AssignExpr a) {
            pad(); System.out.println("Assign " + a.getName());
            indent++;
            printExpression(a.getValue());
            indent--;
        } else if (e instanceof UnaryExpr u) {
            pad(); System.out.println("Unary: " + u.getOp());
            indent++;
            printExpression(u.getExpr());
            indent--;
        } else if (e instanceof BinaryExpr b) {
            pad(); System.out.println("Binary: " + b.getOp());
            indent++;
            printExpression(b.getLeft());
            printExpression(b.getRight());
            indent--;
        } else if (e instanceof CallExpr c) {
            pad(); System.out.println("Call: " + c.getCallee());
            indent++;
            for (Expression arg : c.getArgs()) {
                printExpression(arg);
            }
            indent--;
        } else if (e instanceof IndexExpr idx) {
            pad(); System.out.println("Index:");
            indent++;
            printExpression(idx.getArray());
            printExpression(idx.getIndex());
            indent--;
        } else if (e instanceof ArrayLiteralExpr arr) {
            pad(); System.out.println("ArrayLiteral:");
            indent++;
            for (Expression el : arr.getElements()) {
                printExpression(el);
            }
            indent--;
        } else {
            pad(); System.out.println("Unknown expression: " + e.getClass());
        }
    }
}
