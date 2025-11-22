package lang.semantic.symbols;

import java.util.Objects;

public final class FrogType {

    public enum Kind {
        INT, FLOAT, BOOL, STRING, VOID, ARRAY
    }

    private final Kind kind;
    private final FrogType elementType;

    private FrogType(Kind kind, FrogType elementType) {
        this.kind = kind;
        this.elementType = elementType;
    }

    public static final FrogType INT = new FrogType(Kind.INT, null);
    public static final FrogType FLOAT = new FrogType(Kind.FLOAT, null);
    public static final FrogType BOOL = new FrogType(Kind.BOOL, null);
    public static final FrogType STRING = new FrogType(Kind.STRING, null);
    public static final FrogType VOID = new FrogType(Kind.VOID, null);

    public static FrogType arrayOf(FrogType elementType) {
        Objects.requireNonNull(elementType, "elementType");
        return new FrogType(Kind.ARRAY, elementType);
    }

    public Kind getKind() {
        return kind;
    }

    public FrogType getElementType() {
        return elementType;
    }

    public boolean isNumeric() {
        return kind == Kind.INT || kind == Kind.FLOAT;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case INT -> "int";
            case FLOAT -> "float";
            case BOOL -> "bool";
            case STRING -> "string";
            case VOID -> "void";
            case ARRAY -> "array<" + elementType + ">";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrogType other)) return false;
        if (kind != other.kind) return false;
        if (kind != Kind.ARRAY) return true;
        return elementType.equals(other.elementType);
    }

    @Override
    public int hashCode() {
        return kind == Kind.ARRAY
                ? Objects.hash(kind, elementType)
                : Objects.hash(kind);
    }

    public boolean isAssignableFrom(FrogType rhs) {
        return this.equals(rhs);
    }
}
