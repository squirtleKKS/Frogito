package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.symbols.SourceLocation;

public final class IndexExpr extends BaseExpr {

    private final Expression array;
    private final Expression index;

    public IndexExpr(Expression array, Expression index, SourceLocation location) {
        super(location);
        this.array = array;
        this.index = index;
    }

    public Expression getArray() { return array; }
    public Expression getIndex() { return index; }
}