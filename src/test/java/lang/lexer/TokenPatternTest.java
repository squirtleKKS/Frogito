package lang.lexer;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Набор тестов для проверки корректности шаблонов токенов ({@link TokenPattern}).
 * <p>
 * Проверяется, что регулярные выражения совпадают с корректными примерами
 * и не совпадают с ошибочными строками.
 * </p>
 *
 * @since 1.0
 */
public class TokenPatternTest {

    /**
     * Проверяет, что шаблон корректно находит соответствие по типу токена.
     *
     * @param text исходный текст
     * @param expectedType ожидаемый тип токена
     * @return {@code true}, если текст соответствует типу
     */
    private boolean matches(String text, TokenType expectedType) {
        return TokenPattern.ALL.stream()
                .anyMatch(p -> {
                    Matcher m = p.pattern().matcher(text);
                    return m.find() && m.start() == 0 && p.type() == expectedType;
                });
    }

    /** Проверяет ключевые слова. */
    @Test
    void testKeywords() {
        assertTrue(matches("func", TokenType.KW_FUNC));
        assertTrue(matches("return", TokenType.KW_RETURN));
        assertTrue(matches("if", TokenType.KW_IF));
        assertTrue(matches("else", TokenType.KW_ELSE));
        assertTrue(matches("while", TokenType.KW_WHILE));
        assertTrue(matches("for", TokenType.KW_FOR));
    }

    /** Проверяет арифметические операции. */
    @Test
    void testArithmeticOperators() {
        assertTrue(matches("+", TokenType.PLUS));
        assertTrue(matches("-", TokenType.MINUS));
        assertTrue(matches("*", TokenType.STAR));
        assertTrue(matches("/", TokenType.SLASH));
        assertTrue(matches("%", TokenType.PERCENT));
    }

    /** Проверяет операторы сравнения. */
    @Test
    void testComparisons() {
        assertTrue(matches("==", TokenType.EQ));
        assertTrue(matches("!=", TokenType.NEQ));
        assertTrue(matches("<", TokenType.LT));
        assertTrue(matches(">", TokenType.GT));
        assertTrue(matches("<=", TokenType.LE));
        assertTrue(matches(">=", TokenType.GE));
    }

    /** Проверяет литералы (числа и строки). */
    @Test
    void testLiterals() {
        assertTrue(matches("123", TokenType.INT_LITERAL));
        assertTrue(matches("3.14", TokenType.FLOAT_LITERAL));
        assertTrue(matches("0.5e2", TokenType.FLOAT_LITERAL));
        assertTrue(matches("\"hello\"", TokenType.STRING_LITERAL));
        assertTrue(matches("\"a\\n\\t\\\"b\\\"\"", TokenType.STRING_LITERAL));
    }

    /** Проверяет идентификаторы. */
    @Test
    void testIdentifiers() {
        assertTrue(matches("x", TokenType.IDENT));
        assertTrue(matches("abc123", TokenType.IDENT));
        assertTrue(matches("_temp", TokenType.IDENT));
        assertTrue(matches("ABC_temp", TokenType.IDENT));
        assertFalse(matches("123abc", TokenType.IDENT));
    }

    /** Проверяет комментарии. */
    @Test
    void testComments() {
        assertTrue(matches("// comment", TokenType.LINE_COMMENT));
        assertFalse(matches("/ comment", TokenType.LINE_COMMENT));
    }

    /** Проверяет разделители и скобки. */
    @Test
    void testDelimiters() {
        assertTrue(matches("(", TokenType.LPAREN));
        assertTrue(matches(")", TokenType.RPAREN));
        assertTrue(matches("{", TokenType.LBRACE));
        assertTrue(matches("}", TokenType.RBRACE));
        assertTrue(matches("[", TokenType.LBRACK));
        assertTrue(matches("]", TokenType.RBRACK));
        assertTrue(matches(";", TokenType.SEMICOLON));
        assertTrue(matches(",", TokenType.COMMA));
    }
}