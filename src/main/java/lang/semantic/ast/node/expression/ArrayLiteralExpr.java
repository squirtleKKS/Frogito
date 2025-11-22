package lang.semantic.ast.node.expression;

import lang.semantic.ast.node.BaseExpr;
import lang.semantic.ast.node.Expression;
import lang.semantic.symbols.SourceLocation;

import java.util.List;

public final class ArrayLiteralExpr extends BaseExpr {

    private final List<Expression> elements;

    public ArrayLiteralExpr(List<Expression> elements, SourceLocation location) {
        super(location);
        this.elements = List.copyOf(elements);
    }

    public List<Expression> getElements() { return elements; }
}

