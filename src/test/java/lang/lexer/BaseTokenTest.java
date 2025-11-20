package lang.lexer;

import lang.lexer.token.BaseToken;
import lang.lexer.token.TokenType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Набор модульных тестов для класса {@link BaseToken}.
 * <p>
 * Проверяет корректность работы конструктора, геттеров и метода {@link BaseToken#toString()}.
 * </p>
 *
 * @since 1.0
 */
public class BaseTokenTest {

    /**
     * Проверяет, что все поля токена правильно инициализируются.
     */
    @Test
    void testTokenFields() {
        BaseToken token = new BaseToken(TokenType.KW_IF, "if", 3, 15);

        assertEquals(TokenType.KW_IF, token.getType());
        assertEquals("if", token.getLexeme());
        assertEquals(3, token.getLine());
        assertEquals(15, token.getColumn());
    }

    /**
     * Проверяет корректность форматирования метода {@link BaseToken#toString()}.
     */
    @Test
    void testToStringFormat() {
        BaseToken token = new BaseToken(TokenType.IDENT, "fact", 1, 6);
        String result = token.toString();

        assertTrue(result.contains("IDENT('fact')@1:6"),
                "Unexpected toString() output: " + result);
    }

    /**
     * Проверяет, что два токена с одинаковыми параметрами имеют одинаковые значения полей.
     */
    @Test
    void testEqualityByFields() {
        BaseToken t1 = new BaseToken(TokenType.INT_LITERAL, "42", 2, 8);
        BaseToken t2 = new BaseToken(TokenType.INT_LITERAL, "42", 2, 8);

        assertNotSame(t1, t2);

        assertEquals(t1.getLexeme(), t2.getLexeme());
        assertEquals(t1.getType(), t2.getType());
    }
}
