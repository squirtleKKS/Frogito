package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.symbols.SourceLocation;

import java.util.List;

public final class CallExpr extends BaseExpr {

    private final String callee;
    private final List<Expression> args;

    public CallExpr(String callee,
                    List<Expression> args,
                    SourceLocation location) {
        super(location);
        this.callee = callee;
        this.args = List.copyOf(args);
    }

    public String getCallee() { return callee; }
    public List<Expression> getArgs() { return args; }
}
