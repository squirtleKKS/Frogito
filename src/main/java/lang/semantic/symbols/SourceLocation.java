package lang.semantic.symbols;

public final class SourceLocation {

    private final int line;
    private final int column;

    public SourceLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int getLine() { return line; }
    public int getColumn() { return column; }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
