package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Statement;

public final class BreakStmt implements Statement {

    private final SourceLocation location;

    public BreakStmt(SourceLocation location) {
        this.location = location;
    }

    @Override
    public SourceLocation getLocation() { return location; }
}
