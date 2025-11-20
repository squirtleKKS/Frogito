package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.symbols.SourceLocation;

public final class LiteralExpr extends BaseExpr {

    private final Object value;

    public LiteralExpr(Object value, SourceLocation location) {
        super(location);
        this.value = value;
    }

    public Object getValue() { return value; }
}
