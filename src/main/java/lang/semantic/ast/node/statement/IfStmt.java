package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;

public final class IfStmt implements Statement {

    private final Expression condition;
    private final Statement thenBranch;
    private final Statement elseBranch;
    private final SourceLocation location;

    public IfStmt(Expression condition,
                  Statement thenBranch,
                  Statement elseBranch,
                  SourceLocation location) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
        this.location = location;
    }

    public Expression getCondition() { return condition; }
    public Statement getThenBranch() { return thenBranch; }
    public Statement getElseBranch() { return elseBranch; }

    @Override
    public SourceLocation getLocation() { return location; }
}
