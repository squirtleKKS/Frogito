package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;

public final class WhileStmt implements Statement {

    private final Expression condition;
    private final Statement body;
    private final SourceLocation location;

    public WhileStmt(Expression condition,
                     Statement body,
                     SourceLocation location) {
        this.condition = condition;
        this.body = body;
        this.location = location;
    }

    public Expression getCondition() { return condition; }
    public Statement getBody() { return body; }

    @Override
    public SourceLocation getLocation() { return location; }
}

