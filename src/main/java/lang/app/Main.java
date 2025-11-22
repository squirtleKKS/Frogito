package lang.app;

import lang.lexer.Lexer;
import lang.lexer.token.Token;
import lang.lexer.token.TokenFactory;
import lang.parser.Parser;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.statement.*;
import lang.semantic.ast.printer.ASTPrinter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String code = "var int x = (10 + 10 + 20 + 30);\n";
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        ASTPrinter printer = new ASTPrinter();
        printer.printProgram(parser.parseProgram());
    }
}
