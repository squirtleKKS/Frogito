package lang.lexer;


import lang.lexer.token.Token;
import lang.parser.ParseException;
import lang.parser.Parser;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.statement.FunctionDeclStmt;
import lang.semantic.ast.node.statement.VarDeclStmt;
import lang.semantic.symbols.FrogType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {
    private Program compile(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parseProgram();
    }
    @Test
    void testVarDeclaration() {
        Program program = compile("var int x = 10;");

        assertEquals(1, program.getStatements().size());
        Statement stmt = program.getStatements().get(0);

        assertTrue(stmt instanceof VarDeclStmt);
        VarDeclStmt v = (VarDeclStmt) stmt;

        assertEquals("x", v.getName());
        assertEquals(FrogType.INT, v.getType());
        assertEquals(FrogType.INT, v.getInitializer().getType());
    }

    @Test
    void testSimpleFunction() {
        String src = """
            func int add(int a, int b) {
                return a + b;
            }
            """;

        Program program = compile(src);

        assertEquals(1, program.getFunctions().size());
        FunctionDeclStmt f = program.getFunctions().get(0);

        assertEquals("add", f.getName());
        assertEquals(FrogType.INT, f.getReturnType());
        assertEquals(2, f.getParams().size());
    }

    @Test
    void testRecursion() {
        String src = """
            func int fact(int n) {
                if (n <= 1) return 1;
                return n * fact(n - 1);
            }
            """;

        Program program = compile(src);
        assertEquals(1, program.getFunctions().size());
    }

    @Test
    void testMissingReturnError() {
        String src = """
            func int bad() {
                var int x = 5;
            }
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testReturnTypeMismatch() {
        String src = """
            func int bad() {
                return "hello";
            }
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testAssignmentTypeMismatch() {
        String src = """
            var int x = 10;
            x = "string";
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testUndeclaredVariable() {
        String src = """
            var int x = y;
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testBreakOutsideLoop() {
        String src = """
            break;
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testArrayLiteral() {
        String src = "var array<int> nums = {1, 2, 3};";

        Program program = compile(src);
        VarDeclStmt v = (VarDeclStmt) program.getStatements().get(0);

        assertEquals("nums", v.getName());
        assertEquals(FrogType.arrayOf(FrogType.INT), v.getType());
    }

    @Test
    void testEmptyArrayError() {
        String src = "var array<int> nums = {};";

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testNestedArrays() {
        String src = """
            var array<array<int>> m = {
                {1,2},
                {3,4}
            };
            """;

        Program program = compile(src);
        VarDeclStmt v = (VarDeclStmt) program.getStatements().get(0);

        assertEquals(
                FrogType.arrayOf(FrogType.arrayOf(FrogType.INT)),
                v.getType()
        );
    }
    @Test
    void testFunctionCall() {
        String src = """
            func int inc(int x) { return x + 1; }
            var int a = inc(5);
            """;

        Program program = compile(src);
        VarDeclStmt v = (VarDeclStmt) program.getStatements().get(0);

        assertEquals(FrogType.INT, v.getInitializer().getType());
    }

    @Test
    void testFunctionCallWrongArgCount() {
        String src = """
            func int f(int x, int y) { return x + y; }
            var int a = f(10);
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }

    @Test
    void testFunctionCallWrongArgType() {
        String src = """
            func int f(int x) { return x; }
            var int a = f("hello");
            """;

        assertThrows(ParseException.class, () -> compile(src));
    }
    @Test
    void testBreakInsideLoop() {
        String src = """
            for (var int i = 0; i < 3; i = i + 1) {
                break;
            }
            """;
        Program program = compile(src);
        assertEquals(1, program.getStatements().size());
    }

    @Test
    void testContinueInsideLoop() {
        String src = """
            var int x = 0;
            while (x < 5) {
                continue;
            }
            """;
        Program program = compile(src);
        assertEquals(2, program.getStatements().size());
    }

    @Test
    void testBreakError() {
        assertThrows(ParseException.class, () -> compile("break;"));
    }
}
