package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.expression.operations.UnaryOp;
import lang.semantic.symbols.SourceLocation;

public final class UnaryExpr extends BaseExpr {

    private final UnaryOp op;
    private final Expression expr;

    public UnaryExpr(UnaryOp op, Expression expr, SourceLocation location) {
        super(location);
        this.op = op;
        this.expr = expr;
    }

    public UnaryOp getOp() { return op; }
    public Expression getExpr() { return expr; }
}
