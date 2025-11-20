package lang.semantic.ast.node;

import lang.semantic.symbols.FrogType;

public interface Expression extends AstNode {
    FrogType getType();
    void setType(FrogType type);
}
