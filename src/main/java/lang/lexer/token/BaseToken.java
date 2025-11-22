package lang.lexer.token;

/**
 * Реализация интерфейса {@link Token}, представляющая
 * базовый (универсальный) токен лексического анализатора.
 *
 * <p>
 * Класс {@code BaseToken} используется для хранения основной информации
 * о токене, распознанном лексером:
 * </p>
 * <ul>
 *     <li>его <b>тип</b> ({@link TokenType}),</li>
 *     <li><b>лексему</b> (фрагмент исходного кода, соответствующий токену),</li>
 *     <li><b>номер строки</b> (line) — где начинается токен,</li>
 *     <li><b>номер символа в строке</b> (column) — позиция первого символа токена.</li>
 * </ul>
 *
 * <p>
 * Объекты этого класса создаются фабрикой {@link TokenFactory} (например,
 * методом {@link TokenFactory#defaultFactory()}), и обычно используются
 * лексером как универсальное представление токенов любого типа.
 * </p>
 *
 * @see Token
 * @see TokenType
 * @see TokenFactory
 * @since 1.0
 */
public class BaseToken implements Token {

    /** Тип токена (например, ключевое слово, оператор, литерал). */
    private final TokenType type;

    /** Исходный текст токена (лексема). */
    private final String lexeme;

    /** Номер строки, где начинается токен (нумерация с 1). */
    private final int line;

    /** Номер символа в строке, где начинается токен (нумерация с 1). */
    private final int column;

    /**
     * Создаёт новый экземпляр токена с указанными параметрами.
     *
     * @param type   тип токена ({@link TokenType})
     * @param lexeme исходный текст токена, соответствующий распознанной лексеме
     * @param line   номер строки, где токен начинается (нумерация с 1)
     * @param column номер символа в строке, где токен начинается (нумерация с 1)
     */
    public BaseToken(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    /**
     * Возвращает тип токена.
     *
     * @return тип токена ({@link TokenType})
     */
    @Override public TokenType getType() { return type; }

    /**
     * Возвращает номер строки, где начинается токен.
     * Нумерация начинается с 1.
     *
     * @return номер строки токена
     */
    @Override public String getLexeme() { return lexeme; }

    /**
     * Возвращает номер строки, где начинается токен.
     * Нумерация начинается с 1.
     *
     * @return номер строки токена
     */
    @Override public int getLine() { return line; }

    /**
     * Возвращает номер символа (позицию в строке),
     * где начинается токен. Нумерация с 1.
     *
     * @return номер символа в строке
     */
    @Override public int getColumn() { return column; }

    /**
     * Возвращает читаемое представление токена.
     * <p>
     * Формат:
     * <pre>{@code
     * TYPE('lexeme')@line:column
     * }</pre>
     * Пример:
     * <pre>{@code
     * KW_FUNC('func')@1:1
     * }</pre>
     *
     * @return строка с информацией о типе, тексте и позиции токена
     */
    @Override
    public String toString() {
        return type + "('" + lexeme + "')@" + line + ":" + column;
    }
}
