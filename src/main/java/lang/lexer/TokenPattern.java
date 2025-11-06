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

            simple("var", TokenType.KW_VAR),
            simple("func", TokenType.KW_FUNC),
            simple("return", TokenType.KW_RETURN),
            simple("if", TokenType.KW_IF),
            simple("else", TokenType.KW_ELSE),
            simple("for", TokenType.KW_FOR),
            simple("while", TokenType.KW_WHILE),
            simple("break", TokenType.KW_BREAK),
            simple("continue", TokenType.KW_CONTINUE),

            simple("int", TokenType.KW_INT),
            simple("float", TokenType.KW_FLOAT),
            simple("bool", TokenType.KW_BOOL),
            simple("string", TokenType.KW_STRING),
            simple("array", TokenType.KW_ARRAY),
            simple("void", TokenType.KW_VOID),

            simple("true", TokenType.BOOL_TRUE),
            simple("false", TokenType.BOOL_FALSE),

            regex("[0-9]+\\.[0-9]+([eE][+-]?[0-9]+)?", TokenType.FLOAT_LITERAL),
            regex("[0-9]+", TokenType.INT_LITERAL),
            regex("\"(?:\\\\.|[^\"\\\\])*\"", TokenType.STRING_LITERAL),

            regex("[A-Za-z_][A-Za-z0-9_]*", TokenType.IDENT),

            regex("//[^\\r\\n]*", TokenType.LINE_COMMENT),

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
            simple("+", TokenType.PLUS),
            simple("-", TokenType.MINUS),
            simple("*", TokenType.STAR),
            simple("/", TokenType.SLASH),
            simple("%", TokenType.PERCENT),

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
            public TokenType type() { return type; }
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
}
