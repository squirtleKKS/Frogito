package lang.app;

import lang.bytecodeGenerator.BytecodeGenerator;
import lang.bytecodeGenerator.Disassembler;
import lang.bytecodeGenerator.FrogcWriter;
import lang.lexer.Lexer;
import lang.lexer.token.Token;
import lang.lexer.token.TokenFactory;
import lang.optimizer.AstOptimizer;
import lang.parser.Parser;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.statement.*;
import lang.semantic.ast.printer.ASTPrinter;
import lang.semantic.bytecode.BytecodeModule;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String code = "var int x = (10 + 10 + 20 + 30);\n";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Program ast = parser.parseProgram();
        AstOptimizer opt = new AstOptimizer();
        BytecodeGenerator gen = new BytecodeGenerator();
        Program optimizedProgram = opt.optimize(ast);
        BytecodeModule module = gen.generate(optimizedProgram);
        Disassembler.dump(module);
        try (FileOutputStream fos = new FileOutputStream("hello.frogc")) {
            FrogcWriter.write(module, fos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
