package lang.semantic.ast.node.statement;

import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.expression.IndexExpr;
import lang.semantic.symbols.FrogType;
import lang.semantic.symbols.SourceLocation;

public final class IndexAssignStmt implements Statement {
    private final IndexExpr target;
    private final Expression value;
    private final SourceLocation location;

    private FrogType valueType;

    public IndexAssignStmt(IndexExpr target, Expression value, SourceLocation location) {
        this.target = target;
        this.value = value;
        this.location = location;
    }

    public IndexExpr getTarget() { return target; }
    public Expression getValue() { return value; }
    public SourceLocation getLocation() { return location; }

    public FrogType getValueType() { return valueType; }
    public void setValueType(FrogType t) { this.valueType = t; }
}
