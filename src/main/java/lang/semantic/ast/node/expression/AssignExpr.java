package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.symbols.SourceLocation;

public final class AssignExpr extends BaseExpr {

    private final String name;
    private final Expression value;

    public AssignExpr(String name, Expression value, SourceLocation location) {
        super(location);
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public Expression getValue() { return value; }
}