package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Statement;
import lang.semantic.symbols.FrogType;

public final class VarDeclStmt implements Statement {

    private final FrogType type;
    private final String name;
    private final Expression initializer;
    private final SourceLocation location;
    private final Expression arraySize;

    public VarDeclStmt(FrogType type, String name,
                       Expression initializer, SourceLocation location,  Expression arraySize) {
        this.type = type;
        this.name = name;
        this.initializer = initializer;
        this.location = location;
        this.arraySize = arraySize;
    }

    public FrogType getType() { return type; }
    public String getName() { return name; }
    public Expression getInitializer() { return initializer; }
    public Expression getArraySize() { return arraySize; }

    @Override
    public SourceLocation getLocation() { return location; }
}
