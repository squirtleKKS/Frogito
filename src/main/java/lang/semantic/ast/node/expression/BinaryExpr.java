package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.expression.operations.BinaryOp;
import lang.semantic.symbols.SourceLocation;

public final class BinaryExpr extends BaseExpr {

    private final Expression left;
    private final BinaryOp op;
    private final Expression right;

    public BinaryExpr(Expression left, BinaryOp op,
                      Expression right, SourceLocation location) {
        super(location);
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public Expression getLeft() { return left; }
    public BinaryOp getOp() { return op; }
    public Expression getRight() { return right; }
}
