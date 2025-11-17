package lang.lexer;

/**
 * Исключение, выбрасываемое лексером при обнаружении
 * недопустимого символа или ошибки токенизации.
 *
 * <p>
 * Содержит координаты позиции (строка, столбец),
 * где возникла ошибка.
 * </p>
 *
 * @see Lexer
 * @since 1.0
 */
public final class LexingException extends RuntimeException {

    /** Номер строки, в которой произошла ошибка. */
    private final int line;
    /** Номер столбца, где произошла ошибка. */
    private final int column;

    /**
     * Создаёт исключение с сообщением и позицией.
     *
     * @param message текст ошибки
     * @param line номер строки
     * @param column номер столбца
     */
    public LexingException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    /**
     * Возвращает номер строки, в которой произошла ошибка.
     *
     * @return номер строки
     */
    public int getLine() {
        return line;
    }

    /**
     * Возвращает номер столбца, где произошла ошибка.
     *
     * @return номер столбца
     */
    public int getColumn() {
        return column;
    }
}
