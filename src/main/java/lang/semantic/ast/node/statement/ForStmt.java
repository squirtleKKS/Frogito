package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;

public final class ForStmt implements Statement {

    private final Statement initializer;
    private final Expression condition;
    private final Expression increment;
    private final Statement body;
    private final SourceLocation location;

    public ForStmt(Statement initializer,
                   Expression condition,
                   Expression increment,
                   Statement body,
                   SourceLocation location) {
        this.initializer = initializer;
        this.condition = condition;
        this.increment = increment;
        this.body = body;
        this.location = location;
    }

    public Statement getInitializer() { return initializer; }
    public Expression getCondition() { return condition; }
    public Expression getIncrement() { return increment; }
    public Statement getBody() { return body; }

    @Override
    public SourceLocation getLocation() { return location; }
}

