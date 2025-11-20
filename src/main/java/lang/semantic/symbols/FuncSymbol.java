package lang.semantic.symbols;

import java.util.List;

public final class FuncSymbol implements Symbol {

    private final String name;
    private final FrogType returnType;
    private final List<FrogType> paramTypes;

    public FuncSymbol(String name, FrogType returnType, List<FrogType> paramTypes) {
        this.name = name;
        this.returnType = returnType;
        this.paramTypes = List.copyOf(paramTypes);
    }

    @Override public String getName() { return name; }
    @Override public FrogType getType() { return returnType; }

    public FrogType getReturnType() { return returnType; }
    public List<FrogType> getParamTypes() { return paramTypes; }
}
