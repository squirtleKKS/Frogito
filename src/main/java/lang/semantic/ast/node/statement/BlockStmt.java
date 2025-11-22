package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Statement;

import java.util.List;

public final class BlockStmt implements Statement {

    private final List<Statement> statements;
    private final SourceLocation location;

    public BlockStmt(List<Statement> statements, SourceLocation location) {
        this.statements = List.copyOf(statements);
        this.location = location;
    }

    public List<Statement> getStatements() { return statements; }

    @Override
    public SourceLocation getLocation() { return location; }
}
