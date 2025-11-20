package lang.semantic.symbols;


public final class VarSymbol implements Symbol {

    private final String name;
    private final FrogType type;

    public VarSymbol(String name, FrogType type) {
        this.name = name;
        this.type = type;
    }

    @Override public String getName() { return name; }
    @Override public FrogType getType() { return type; }
}
