package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.symbols.SourceLocation;

public final class VarExpr extends BaseExpr {

    private final String name;

    public VarExpr(String name, SourceLocation location) {
        super(location);
        this.name = name;
    }

    public String getName() { return name; }
}
