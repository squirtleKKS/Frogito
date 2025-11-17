package lang.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Класс, реализующий лексический анализатор языка <b>Frogito</b>.
 * <p>
 * Лексер преобразует исходный текст программы в последовательность токенов
 * на основе шаблонов {@link TokenPattern#ALL}.
 * </p>
 *
 * <p>
 * Работает построчно, отслеживая позицию (номер строки и столбца)
 * и формирует поток токенов до достижения конца файла.
 * </p>
 *
 * @see Token
 * @see TokenPattern
 * @see TokenType
 * @since 1.0
 */
public final class Lexer {

    /** Исходный текст программы. */
    private final String input;
    /** Общая длина входной строки. */
    private final int length;
    /** Текущая позиция курсора в строке. */
    private int index = 0;
    /** Текущий номер строки. */
    private int line = 1;
    /** Текущий номер столбца. */
    private int column = 1;
    /** Фабрика токенов для создания объектов {@link Token}. */
    private final TokenFactory factory;

    /**
     * Создаёт лексер с входной строкой и фабрикой токенов по умолчанию.
     *
     * @param input исходный код
     */
    public Lexer(String input) {
        this(input, TokenFactory.defaultFactory());
    }

    /**
     * Создаёт лексер с заданной фабрикой токенов.
     *
     * @param input исходный код
     * @param factory фабрика токенов
     */
    public Lexer(String input, TokenFactory factory) {
        this.input = input == null ? "" : input;
        this.length = this.input.length();
        this.factory = factory == null ? TokenFactory.defaultFactory() : factory;
    }

    /**
     * Выполняет лексический анализ входного текста и возвращает список токенов.
     * <p>
     * Последний элемент списка — служебный токен {@link TokenType#EOF}.
     * </p>
     *
     * @return список токенов
     */
    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        Token t;
        while ((t = nextTokenInternal()) != null) {
            out.add(t);
            if (t.getType() == TokenType.EOF) break;
        }
        return out;
    }

    /**
     * Возвращает следующий токен из входного потока.
     *
     * @return токен либо {@code null}, если достигнут конец файла
     */
    private Token nextTokenInternal() {
        skipWhitespace();
        if (isAtEnd()) {
            return factory.create(TokenType.EOF, "", line, column);
        }

        for (TokenPattern pat : TokenPattern.ALL) {
            Matcher m = pat.pattern().matcher(input);
            m.region(index, length);
            if (m.lookingAt()) {
                String lexeme = m.group();
                // --- новый код: пропуск комментариев ---
                if (pat.type() == TokenType.LINE_COMMENT) {
                    advance(lexeme);
                    return nextTokenInternal();
                }
                Token tok = factory.create(pat.type(), lexeme, line, column);
                advance(lexeme);
                return tok;
            }
        }

        // --- новый код: генерация исключения при неизвестном символе ---
        char bad = input.charAt(index);
        throw new LexingException("Unexpected character: '" + printable(bad) + "' at " + line + ":" + column, line, column);
    }

    /**
     * Пропускает пробелы, табы и переводы строк.
     */
    private void skipWhitespace() {
        while (!isAtEnd()) {
            char c = input.charAt(index);
            if (c == ' ' || c == '\t' || c == '\r') {
                index++;
                column++;
                continue;
            }
            if (c == '\n') {
                index++;
                line++;
                column = 1;
                continue;
            }
            break;
        }
    }

    /**
     * Продвигает курсор на длину считанной лексемы,
     * обновляя позицию по строке и столбцу.
     *
     * @param text лексема
     */
    private void advance(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        index += text.length();
    }

    /**
     * Проверяет, достигнут ли конец входного текста.
     *
     * @return {@code true}, если достигнут конец
     */
    private boolean isAtEnd() {
        return index >= length;
    }

    /**
     * Возвращает печатное представление символа
     * для сообщений об ошибках.
     *
     * @param c символ
     * @return строка, представляющая символ
     */
    private String printable(char c) {
        if (c == '\n') return "\\n";
        if (c == '\r') return "\\r";
        if (c == '\t') return "\\t";
        return String.valueOf(c);
    }
}
