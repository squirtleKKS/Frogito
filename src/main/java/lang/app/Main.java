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
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <code> <bytecode_file>");
            System.err.println("Example: java Main \"var int x = 5;\" output.frogc");
            System.exit(1);
        }
        
        String code = args[0];
        String bytecodeFile = args[1];
        
        try {
            Lexer lexer = new Lexer(code);
            List<Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens);
            Program ast = parser.parseProgram();
            AstOptimizer opt = new AstOptimizer();
            BytecodeGenerator gen = new BytecodeGenerator();
            Program optimizedProgram = opt.optimize(ast);
            BytecodeModule module = gen.generate(optimizedProgram);
            Disassembler.dump(module);
            
            try (FileOutputStream fos = new FileOutputStream(bytecodeFile)) {
                FrogcWriter.write(module, fos);
                System.out.println("Bytecode written to: " + bytecodeFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            System.err.println("Compilation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}



