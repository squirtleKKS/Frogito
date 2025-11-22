package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;

public final class ExprStmt implements Statement {

    private final Expression expression;
    private final SourceLocation location;

    public ExprStmt(Expression expression, SourceLocation location) {
        this.expression = expression;
        this.location = location;
    }

    public Expression getExpression() { return expression; }

    @Override
    public SourceLocation getLocation() { return location; }
}
