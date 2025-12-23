package lang.lexer;

import lang.bytecodeGenerator.BytecodeGenerator;
import lang.bytecodeGenerator.Disassembler;
import lang.lexer.token.Token;
import lang.optimizer.AstOptimizer;
import lang.parser.Parser;
import lang.semantic.ast.node.Program;
import lang.semantic.bytecode.BytecodeModule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BytecodeTest {

    private BytecodeModule compileToBytecode(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Program ast = parser.parseProgram();
        Program optAst = new AstOptimizer().optimize(ast);
        return new BytecodeGenerator().generate(optAst);
    }

    private String disasm(BytecodeModule module) {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            Disassembler.dump(module);
        } finally {
            System.setOut(oldOut);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private String normalize(String s) {
        return s.replace("\r\n", "\n")
                .replaceAll("[ \t]+", " ")
                .trim();
    }

    private void assertDisasmExact(String source, String expectedDisasm) {
        BytecodeModule m = compileToBytecode(source);
        String actual = disasm(m);
        assertEquals(normalize(expectedDisasm), normalize(actual));
    }

    private void assertDisasmContains(String source, String... mustContain) {
        String actual = normalize(disasm(compileToBytecode(source)));
        for (String piece : mustContain) {
            assertTrue(actual.contains(normalize(piece)),
                    "Expected disasm to contain:\n" + piece + "\n\nActual:\n" + actual);
        }
    }

    @Test
    void testConstantFoldingArithmeticChain() {
        String src = "var int x = (10 + 10 + 20 + 30);";
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(70)
            0001  STORE_GLOBAL "x"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testConstantFoldingPrecedence() {
        String src = "var int y = 2 + 3 * 4;";
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(14)
            0001  STORE_GLOBAL "y"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testConstantFoldingComparison() {
        String src = "var bool b = (10 > 3);";
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST BOOL(true)
            0001  STORE_GLOBAL "b"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testIfTrueEliminatesElse() {
        String src = """
            if (true) {
                var int a = 1;
            } else {
                var int a = 2;
            }
            """;
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(1)
            0001  STORE_GLOBAL "a"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testWhileFalseRemoved() {
        String src = """
            var int x = 0;
            while (false) {
                x = x + 1;
            }
            """;
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(0)
            0001  STORE_GLOBAL "x"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testArrayLiteralConstantElements() {
        String src = """
            var array<int> nums = {1 + 2, 3, 4};
            """;
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(3)
            0001  PUSH_CONST INT(3)
            0002  PUSH_CONST INT(4)
            0003  NEW_ARRAY 3
            0004  STORE_GLOBAL "nums"
            0005  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testSimpleFunctionAndCallFromGlobal() {
        String src = """
            func int inc(int x) { return x + 1; }
            var int a = inc(5);
            """;
        assertDisasmContains(src,
                "PUSH_CONST INT(5)",
                "CALL inc@",
                "STORE_GLOBAL \"a\"",
                "LOAD_LOCAL 0",
                "PUSH_CONST INT(1)",
                "ADD",
                "RET",
                "KVA"
        );
    }

    @Test
    void testBigConstantFolding() {
        String src = "var int x = ((2 + 3) * (4 + 5) - 10) / 2;";
        String expected = """
            === CODE (disasm) ===
            0000  PUSH_CONST INT(17)
            0001  STORE_GLOBAL "x"
            0002  KVA
            """;
        assertDisasmExact(src, expected);
    }

    @Test
    void testNestedIfStructure() {
        String src = """
            var int x = 3;
            if (x > 0) {
                if (x > 10) {
                    x = 100;
                } else {
                    x = 50;
                }
            } else {
                x = 0;
            }
            """;

        assertDisasmContains(src,
                "PUSH_CONST INT(3)",
                "STORE_GLOBAL \"x\"",
                "LOAD_GLOBAL \"x\"",
                "PUSH_CONST INT(0)",
                "GT",
                "JUMP_FALSE",
                "PUSH_CONST INT(10)",
                "GT",
                "PUSH_CONST INT(100)",
                "STORE_GLOBAL \"x\"",
                "PUSH_CONST INT(50)",
                "STORE_GLOBAL \"x\"",
                "PUSH_CONST INT(0)",
                "STORE_GLOBAL \"x\"",
                "KVA"
        );
    }

    @Test
    void testForWithBreakContinue() {
        String src = """
            var int s = 0;
            for (var int i = 0; i < 5; i = i + 1) {
                if (i == 2) continue;
                if (i == 4) break;
                s = s + i;
            }
            """;

        assertDisasmContains(src,
                "STORE_GLOBAL \"s\"",
                "PUSH_CONST INT(0)",
                "STORE_GLOBAL \"i\"",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(5)",
                "LT",
                "JUMP_FALSE",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(2)",
                "EQ",
                "JUMP_FALSE",
                "JUMP",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(4)",
                "EQ",
                "JUMP_FALSE",
                "JUMP",
                "LOAD_GLOBAL \"s\"",
                "LOAD_GLOBAL \"i\"",
                "ADD",
                "STORE_GLOBAL \"s\"",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(1)",
                "ADD",
                "STORE_GLOBAL \"i\"",
                "JUMP",
                "KVA"
        );
    }

    @Test
    void testNestedArraysAndIndex() {
        String src = """
            var array<array<int>> m = { {1,2}, {3,4} };
            var int a = m[0][1];
            """;

        assertDisasmContains(src,
                "PUSH_CONST INT(1)",
                "PUSH_CONST INT(2)",
                "NEW_ARRAY 2",
                "PUSH_CONST INT(3)",
                "PUSH_CONST INT(4)",
                "NEW_ARRAY 2",
                "NEW_ARRAY 2",
                "STORE_GLOBAL \"m\"",
                "LOAD_GLOBAL \"m\"",
                "PUSH_CONST INT(0)",
                "LOAD_INDEX",
                "PUSH_CONST INT(1)",
                "LOAD_INDEX",
                "STORE_GLOBAL \"a\"",
                "KVA"
        );
    }

    @Test
    void testWhileLoopSum() {
        String src = """
            var int i = 0;
            var int sum = 0;
            while (i < 3) {
                sum = sum + i;
                i = i + 1;
            }
            """;

        assertDisasmContains(src,
                "STORE_GLOBAL \"i\"",
                "STORE_GLOBAL \"sum\"",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(3)",
                "LT",
                "JUMP_FALSE",
                "LOAD_GLOBAL \"sum\"",
                "LOAD_GLOBAL \"i\"",
                "ADD",
                "STORE_GLOBAL \"sum\"",
                "LOAD_GLOBAL \"i\"",
                "PUSH_CONST INT(1)",
                "ADD",
                "STORE_GLOBAL \"i\"",
                "JUMP",
                "KVA"
        );
    }

    @Test
    void testIndexAssignBytecode() {
        String src = """
            var array<int> a = {1,2,3};
            a[1] = 9;
            """;

        assertDisasmContains(src,
                "NEW_ARRAY 3",
                "STORE_GLOBAL \"a\"",
                "LOAD_GLOBAL \"a\"",
                "PUSH_CONST INT(1)",
                "PUSH_CONST INT(9)",
                "STORE_INDEX",
                "KVA"
        );
    }

    @Test
    void testAlgorithmFactorialRecursion() {
        String src = """
            func int fact(int n) {
                if (n <= 1) return 1;
                return n * fact(n - 1);
            }
            var int r = fact(5);
            """;

        assertDisasmContains(src,
                "PUSH_CONST INT(5)",
                "CALL fact@",
                "STORE_GLOBAL \"r\"",
                "LOAD_LOCAL 0",
                "PUSH_CONST INT(1)",
                "LE",
                "JUMP_FALSE",
                "PUSH_CONST INT(1)",
                "RET",
                "LOAD_LOCAL 0",
                "LOAD_LOCAL 0",
                "PUSH_CONST INT(1)",
                "SUB",
                "CALL fact@",
                "MUL",
                "RET",
                "KVA"
        );
    }

    @Test
    void testAlgorithmArraySorting() {
        String src = """
            func void sort(array<int> a) {
                var int n = len(a);
                var int i = 0;
                while (i < n) {
                    var int j = 0;
                    while (j < n - 1) {
                        if (a[j] > a[j + 1]) {
                            var int tmp = a[j];
                            a[j] = a[j + 1];
                            a[j + 1] = tmp;
                        }
                        j = j + 1;
                    }
                    i = i + 1;
                }
            }

            var array<int> nums = {3, 1, 2};
            sort(nums);
            """;

        assertDisasmContains(src,
                "CALL len@",
                "LOAD_INDEX",
                "STORE_INDEX",
                "JUMP_FALSE",
                "CALL sort@",
                "NEW_ARRAY 3",
                "STORE_GLOBAL \"nums\"",
                "KVA"
        );
    }

    @Test
    void testAlgorithmSieve() {
        String src = """
        func array<int> sieve(int n) {
            var array<bool> prime = new_array_bool(n + 1, true);
            prime[0] = false;
            prime[1] = false;

            var int p = 2;
            while (p * p <= n) {
                if (prime[p]) {
                    var int k = p * p;
                    while (k <= n) {
                        prime[k] = false;
                        k = k + p;
                    }
                }
                p = p + 1;
            }

            var array<int> out = {};
            var int i = 2;
            while (i <= n) {
                if (prime[i]) out = push_int(out, i);
                i = i + 1;
            }
            return out;
        }

        var array<int> primes = sieve(30);
        """;

        assertDisasmContains(src,
                "PUSH_CONST INT(30)",
                "CALL sieve@",
                "STORE_GLOBAL \"primes\"",
                "CALL new_array_bool@",
                "STORE_LOCAL",
                "PUSH_CONST INT(0)",
                "PUSH_CONST BOOL(false)",
                "STORE_INDEX",
                "PUSH_CONST INT(1)",
                "PUSH_CONST BOOL(false)",
                "STORE_INDEX",
                "NEW_ARRAY 0",
                "STORE_LOCAL",
                "CALL push_int@",
                "RET",
                "KVA"
        );
    }
}