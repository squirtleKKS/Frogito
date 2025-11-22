package lang.semantic.ast.node.statement;

import lang.semantic.symbols.SourceLocation;
import lang.semantic.ast.node.Statement;
import lang.semantic.symbols.FrogType;

import java.util.List;

public final class FunctionDeclStmt implements Statement {

    public static final class Param {
        private final String name;
        private final FrogType type;
        private final SourceLocation location;

        public Param(String name, FrogType type, SourceLocation location) {
            this.name = name;
            this.type = type;
            this.location = location;
        }

        public String getName() { return name; }
        public FrogType getType() { return type; }
        public SourceLocation getLocation() { return location; }
    }

    private final String name;
    private final List<Param> params;
    private final FrogType returnType;
    private final BlockStmt body;
    private final SourceLocation location;

    public FunctionDeclStmt(String name,
                            List<Param> params,
                            FrogType returnType,
                            BlockStmt body,
                            SourceLocation location) {
        this.name = name;
        this.params = List.copyOf(params);
        this.returnType = returnType;
        this.body = body;
        this.location = location;
    }

    public String getName() { return name; }
    public List<Param> getParams() { return params; }
    public FrogType getReturnType() { return returnType; }
    public BlockStmt getBody() { return body; }

    @Override
    public SourceLocation getLocation() { return location; }
}
