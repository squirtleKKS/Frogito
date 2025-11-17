package lang.lexer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TokenPatternCoverageTest {
    private List<Token> run(String s) { return new Lexer(s).tokenize(); }

    @Test
    void operatorsAndBools() {
        List<Token> ts = run("true false == != <= >= && || = < > ! + - * / %");
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.BOOL_TRUE));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.BOOL_FALSE));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.EQ));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.NEQ));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.LE));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.GE));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.AND));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.OR));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.ASSIGN));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.LT));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.GT));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.NOT));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.PLUS));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.MINUS));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.STAR));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.SLASH));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.PERCENT));
        assertEquals(TokenType.EOF, ts.get(ts.size()-1).getType());
    }

    @Test
    void numbersAndStrings() {
        List<Token> ts = run("0 123 45.67 1.0e-3 \"a\\n\\\"b\\\"\"");
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.INT_LITERAL));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.FLOAT_LITERAL));
        assertTrue(ts.stream().anyMatch(t -> t.getType()==TokenType.STRING_LITERAL));
    }

    @Test
    void keywordsVsIdent() {
        List<Token> ts = run("var int string array void for while break continue func return if else true false foo trueFalse");
        assertEquals(TokenType.KW_VAR, ts.get(0).getType());
        assertEquals(TokenType.KW_INT, ts.get(1).getType());
        assertEquals(TokenType.KW_STRING, ts.get(2).getType());
        assertEquals(TokenType.KW_ARRAY, ts.get(3).getType());
        assertEquals(TokenType.KW_VOID, ts.get(4).getType());
        assertEquals(TokenType.KW_FOR, ts.get(5).getType());
        assertEquals(TokenType.KW_WHILE, ts.get(6).getType());
        assertEquals(TokenType.KW_BREAK, ts.get(7).getType());
        assertEquals(TokenType.KW_CONTINUE, ts.get(8).getType());
        assertEquals(TokenType.KW_FUNC, ts.get(9).getType());
        assertEquals(TokenType.KW_RETURN, ts.get(10).getType());
        assertEquals(TokenType.KW_IF, ts.get(11).getType());
        assertEquals(TokenType.KW_ELSE, ts.get(12).getType());
        assertEquals(TokenType.BOOL_TRUE, ts.get(13).getType());
        assertEquals(TokenType.BOOL_FALSE, ts.get(14).getType());
        assertEquals(TokenType.IDENT, ts.get(15).getType());
        assertEquals(TokenType.IDENT, ts.get(16).getType());
    }

    @Test
    void commentsSkipped() {
        List<Token> ts = run("x=1; // cmt\n y=2;");
        assertTrue(ts.stream().noneMatch(t -> t.getType()==TokenType.LINE_COMMENT));
    }
}
