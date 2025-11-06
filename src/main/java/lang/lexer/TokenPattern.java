package lang.lexer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Интерфейс, описывающий шаблоны токенов, используемые лексером.
 * <p>
 * Каждый шаблон содержит регулярное выражение и тип токена, которому оно соответствует.
 * Шаблоны определяют, как исходный код преобразуется в поток токенов.
 * </p>
 * <p>
 * Приоритет шаблонов определяется порядком их объявления в списке {@link #ALL}.
 * </p>
 *
 * @see TokenType
 * @since 1.0
 */
public interface TokenPattern {

    /**
     * Возвращает тип токена, которому соответствует данный шаблон.
     *
     * @return тип токена
     */
    TokenType type();

    /**
     * Возвращает регулярное выражение, описывающее шаблон токена.
     *
     * @return регулярное выражение ({@link Pattern})
     */
    Pattern pattern();

    /**
     * Список всех шаблонов токенов языка Frogito.
     * Используется лексером при лексическом анализе.
     */
    List<TokenPattern> ALL = List.of(

            // --- ключевые слова ---
            word("var", TokenType.KW_VAR),
            word("func", TokenType.KW_FUNC),
            word("return", TokenType.KW_RETURN),
            word("if", TokenType.KW_IF),
            word("else", TokenType.KW_ELSE),
            word("for", TokenType.KW_FOR),
            word("while", TokenType.KW_WHILE),
            word("break", TokenType.KW_BREAK),
            word("continue", TokenType.KW_CONTINUE),

            // --- типы данных ---
            word("int", TokenType.KW_INT),
            word("float", TokenType.KW_FLOAT),
            word("bool", TokenType.KW_BOOL),
            word("string", TokenType.KW_STRING),
            word("array", TokenType.KW_ARRAY),
            word("void", TokenType.KW_VOID),

            // --- логические литералы ---
            word("true", TokenType.BOOL_TRUE),
            word("false", TokenType.BOOL_FALSE),

            // --- числовые и строковые литералы ---
            regex("[0-9]+\\.[0-9]+([eE][+-]?[0-9]+)?", TokenType.FLOAT_LITERAL),
            regex("[0-9]+", TokenType.INT_LITERAL),
            regex("\"(?:\\\\.|[^\"\\\\])*\"", TokenType.STRING_LITERAL),

            // --- идентификаторы ---
            regex("[A-Za-z_][A-Za-z0-9_]*", TokenType.IDENT),

            // --- комментарии ---
            regex("//[^\\r\\n]*", TokenType.LINE_COMMENT),

            // --- операторы сравнения и логические ---
            simple("==", TokenType.EQ),
            simple("!=", TokenType.NEQ),
            simple("<=", TokenType.LE),
            simple(">=", TokenType.GE),
            simple("&&", TokenType.AND),
            simple("||", TokenType.OR),
            simple("=", TokenType.ASSIGN),
            simple("<", TokenType.LT),
            simple(">", TokenType.GT),
            simple("!", TokenType.NOT),

            // --- арифметические операторы ---
            simple("+", TokenType.PLUS),
            simple("-", TokenType.MINUS),
            simple("*", TokenType.STAR),
            simple("/", TokenType.SLASH),
            simple("%", TokenType.PERCENT),

            // --- разделители и скобки ---
            simple("(", TokenType.LPAREN),
            simple(")", TokenType.RPAREN),
            simple("{", TokenType.LBRACE),
            simple("}", TokenType.RBRACE),
            simple("[", TokenType.LBRACK),
            simple("]", TokenType.RBRACK),
            simple(";", TokenType.SEMICOLON),
            simple(",", TokenType.COMMA)
    );

    /**
     * Создаёт шаблон токена на основе регулярного выражения.
     *
     * @param regex регулярное выражение (без якорей ^ и $)
     * @param type  тип токена
     * @return объект {@link TokenPattern}
     */
    static TokenPattern regex(String regex, TokenType type) {
        return new TokenPattern() {
            private final Pattern p = Pattern.compile("^(" + regex + ")");
            @Override
            public TokenType type() { return type; }
            @Override
            public Pattern pattern() { return p; }
        };
    }

    /**
     * Создаёт шаблон токена для фиксированной строки (например, ключевого слова).
     *
     * @param literal строка (лексема)
     * @param type тип токена
     * @return объект {@link TokenPattern}
     */
    static TokenPattern simple(String literal, TokenType type) {
        return regex(Pattern.quote(literal), type);
    }

    /**
     * Создаёт шаблон для ключевых слов и булевых литералов,
     * которые должны совпадать только на границе слова.
     * <p><b>НОВЫЙ МЕТОД</b>: добавлен для предотвращения ситуаций вроде "trueFalse".</p>
     *
     * @param literal строка (лексема)
     * @param type тип токена
     * @return объект {@link TokenPattern}
     */
    static TokenPattern word(String literal, TokenType type) {
        return regex(Pattern.quote(literal) + "(?![A-Za-z0-9_])", type);
    }
}
