package lang.semantic.ast.node;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.statement.FunctionDeclStmt;

import java.util.List;

public final class Program implements AstNode {

    private final List<FunctionDeclStmt> functions;
    private final List<Statement> statements;
    private final SourceLocation location;

    public Program(List<FunctionDeclStmt> functions,
                   List<Statement> statements,
                   SourceLocation location) {
        this.functions = List.copyOf(functions);
        this.statements = List.copyOf(statements);
        this.location = location;
    }

    public List<FunctionDeclStmt> getFunctions() { return functions; }
    public List<Statement> getStatements() { return statements; }

    @Override
    public SourceLocation getLocation() { return location; }
}
