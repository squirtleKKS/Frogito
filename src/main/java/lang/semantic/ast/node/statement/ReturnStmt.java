package lang.semantic.ast.node.statement;

import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;
import lang.semantic.symbols.FrogType;
import lang.semantic.symbols.SourceLocation;

public final class ReturnStmt implements Statement {

    private final Expression value;
    private final SourceLocation location;

    private FrogType expectedType;

    public ReturnStmt(Expression value, SourceLocation location) {
        this.value = value;
        this.location = location;
    }

    public Expression getValue() { return value; }
    public FrogType getExpectedType() { return expectedType; }
    public void setExpectedType(FrogType expectedType) { this.expectedType = expectedType; }

    @Override
    public SourceLocation getLocation() { return location; }
}

