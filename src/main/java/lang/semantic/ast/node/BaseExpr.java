package lang.semantic.ast.node;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.symbols.FrogType;

public abstract class BaseExpr implements Expression {

    private final SourceLocation location;
    private FrogType type;

    protected BaseExpr(SourceLocation location) {
        this.location = location;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public FrogType getType() {
        return type;
    }

    @Override
    public void setType(FrogType type) {
        this.type = type;
    }
}

