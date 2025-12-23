package lang.parser;

import lang.lexer.token.Token;
import lang.lexer.token.TokenType;
import lang.semantic.ast.node.Expression;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.node.Statement;
import lang.semantic.ast.node.statement.FunctionDeclStmt;
import lang.semantic.symbols.*;
import lang.semantic.ast.node.statement.*;
import lang.semantic.ast.node.expression.operations.*;
import lang.semantic.ast.node.expression.*;

import java.util.ArrayList;
import java.util.List;

import static lang.lexer.token.TokenType.*;

public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    private final SymbolTable symbols = new SymbolTable();
    private FuncSymbol currentFunction = null;
    private int loopDepth = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        symbols.declare(new FuncSymbol("print", FrogType.VOID, List.of(FrogType.INT)));
        symbols.declare(new FuncSymbol("len", FrogType.INT, List.of(FrogType.arrayOf(FrogType.INT))));
        symbols.declare(new FuncSymbol("new_array_bool",
                FrogType.arrayOf(FrogType.BOOL),
                List.of(FrogType.INT, FrogType.BOOL)));
        symbols.declare(new FuncSymbol("push_int",
                FrogType.arrayOf(FrogType.INT),
                List.of(FrogType.arrayOf(FrogType.INT), FrogType.INT)));

    }

    public Program parseProgram() {
        List<FunctionDeclStmt> functions = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();

        SourceLocation loc = location(peek());

        while (!isAtEnd()) {
            if (match(KW_FUNC)) {
                functions.add(parseFunction());
            } else if (match(KW_VAR)) {
                statements.add(parseVarDecl(previous()));
            } else {
                statements.add(parseStatement());
            }
        }
        for (FunctionDeclStmt f : functions) {
            if (!f.getReturnType().equals(FrogType.VOID)) {
                if (!alwaysReturns(f.getBody())) {
                    throw new ParseException(null,
                            "Функция '" + f.getName()
                                    + "' с возвращаемым типом " + f.getReturnType()
                                    + " не гарантирует возврат значения");
                }
            }
        }

        return new Program(functions, statements, loc);
    }

    private FunctionDeclStmt parseFunction() {
        FrogType returnType = parseType();

        Token nameTok = consume(IDENT, "Ожидалось имя функции");
        String name = nameTok.getLexeme();

        consume(LPAREN, "Ожидалась '(' после имени функции");

        List<FunctionDeclStmt.Param> params = new ArrayList<>();
        List<FrogType> paramTypes = new ArrayList<>();

        if (!check(RPAREN)) {
            do {
                FrogType paramType = parseType();
                Token paramNameTok = consume(IDENT, "Ожидалось имя параметра");
                FunctionDeclStmt.Param param =
                        new FunctionDeclStmt.Param(paramNameTok.getLexeme(), paramType, location(paramNameTok));
                params.add(param);
                paramTypes.add(paramType);
            } while (match(COMMA));
        }
        consume(RPAREN, "Ожидалась ')' после списка параметров");

        FuncSymbol funcSym = new FuncSymbol(name, returnType, paramTypes);
        if (!symbols.declare(funcSym)) {
            throw error(nameTok, "Функция '" + name + "' уже объявлена");
        }

        FuncSymbol previousFunc = currentFunction;
        currentFunction = funcSym;

        symbols.pushScope();
        for (FunctionDeclStmt.Param p : params) {
            boolean ok = symbols.declare(new VarSymbol(p.getName(), p.getType()));
            if (!ok) {
                throw new ParseException(null,
                        "Дублирующее имя параметра '" + p.getName()
                                + "' в функции '" + name + "'");
            }
        }

        BlockStmt body = parseBlock();

        symbols.popScope();
        currentFunction = previousFunc;

        return new FunctionDeclStmt(name, params, returnType, body, location(nameTok));
    }

    private VarDeclStmt parseVarDecl(Token varToken) {
        FrogType type = parseType();
        Token nameTok = consume(IDENT, "Ожидалось имя переменной");
        String name = nameTok.getLexeme();

        Expression arraySize = null;
        if (match(LBRACK)) {

            if (type.getKind() != FrogType.Kind.ARRAY) {
                throw error(nameTok, "Размер можно задавать только массивам");
            }

            arraySize = parseExpression();

            if (!(arraySize instanceof LiteralExpr)
                    || arraySize.getType() != FrogType.INT) {
                throw error(nameTok, "Размер массива должен быть целочисленным литералом");
            }

            consume(RBRACK, "Ожидалась ']'");
        }

        Expression init = null;
        if (match(ASSIGN)) {
            init = parseExpression();
        }

        Token semi = consume(SEMICOLON, "Ожидалась ';' после объявления переменной");

        VarSymbol varSym = new VarSymbol(name, type);
        if (!symbols.declare(varSym)) {
            throw error(nameTok, "Переменная '" + name + "' уже объявлена в этой области");
        }

        if (init != null) {
            if (init instanceof ArrayLiteralExpr arr
                    && arr.getElements().isEmpty()
                    && type.getKind() == FrogType.Kind.ARRAY) {
                arr.setType(type);
            }

            if (arraySize != null && init instanceof ArrayLiteralExpr) {
                throw error(nameTok,
                        "Нельзя одновременно задавать размер массива и инициализатор");
            }

            FrogType initType = init.getType();
            if (!type.isAssignableFrom(initType)) {
                throw error(nameTok, "Тип инициализатора " + initType
                        + " не совместим с типом переменной " + type);
            }
        }


        return new VarDeclStmt(type, name, init, location(semi), arraySize);
    }

    private FrogType parseType() {
        Token t = advance();
        return switch (t.getType()) {
            case KW_INT -> FrogType.INT;
            case KW_FLOAT -> FrogType.FLOAT;
            case KW_BOOL -> FrogType.BOOL;
            case KW_STRING -> FrogType.STRING;
            case KW_VOID -> FrogType.VOID;
            case KW_ARRAY -> {
                consume(LT, "Ожидался '<' после 'array'");
                FrogType elem = parseType();
                consume(GT, "Ожидался '>' после определения типа массива");
                yield FrogType.arrayOf(elem);
            }
            default -> throw error(t, "Ожидался тип, найдено " + t.getLexeme());
        };
    }

    private Statement parseStatement() {
        if (match(KW_IF)) return parseIf();
        if (match(KW_FOR)) return parseFor();
        if (match(KW_WHILE)) return parseWhile();
        if (match(KW_RETURN)) return parseReturn(previous());
        if (match(KW_BREAK)) return parseBreak(previous());
        if (match(KW_CONTINUE)) return parseContinue(previous());
        if (match(LBRACE)) return parseBlockAfterLbrace();
        if (check(IDENT)
                && current + 1 < tokens.size()
                && tokens.get(current + 1).getType() == LBRACK) {
            return parseIndexAssignOrExpr();
        }


        return parseExprStatement();
    }

    private BlockStmt parseBlock() {
        Token lbrace = consume(LBRACE, "Ожидалась '{' для начала блока");
        return parseBlockAfterLbrace();
    }

    private BlockStmt parseBlockAfterLbrace() {
        symbols.pushScope();
        List<Statement> stmts = new ArrayList<>();

        while (!check(RBRACE) && !isAtEnd()) {
            if (match(KW_VAR)) {
                stmts.add(parseVarDecl(previous()));
            } else {
                stmts.add(parseStatement());
            }
        }

        Token rbrace = consume(RBRACE, "Ожидался '}' в конце блока");
        symbols.popScope();

        return new BlockStmt(stmts, location(rbrace));
    }

    private Statement parseExprStatement() {
        Expression expr = parseExpression();
        Token semi = consume(SEMICOLON, "Ожидалась ';' после выражения");
        return new ExprStmt(expr, location(semi));
    }

    private IfStmt parseIf() {
        Token ifToken = previous();
        consume(LPAREN, "Ожидалась '(' после 'if'");
        Expression cond = parseExpression();
        consume(RPAREN, "Ожидалась ')' после условия if");

        if (!cond.getType().equals(FrogType.BOOL)) {
            throw error(ifToken, "Условие if должно быть типа bool, найдено " + cond.getType());
        }

        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (match(KW_ELSE)) {
            elseBranch = parseStatement();
        }

        return new IfStmt(cond, thenBranch, elseBranch, location(ifToken));
    }

    private WhileStmt parseWhile() {
        Token whileToken = previous();
        consume(LPAREN, "Ожидалась '(' после 'while'");
        Expression cond = parseExpression();
        consume(RPAREN, "Ожидалась ')' после условия while");

        if (!cond.getType().equals(FrogType.BOOL)) {
            throw error(whileToken, "Условие while должно быть типа bool");
        }

        loopDepth++;
        Statement body = parseStatement();
        loopDepth--;

        return new WhileStmt(cond, body, location(whileToken));
    }

    private ForStmt parseFor() {
        Token forToken = previous();
        consume(LPAREN, "Ожидалась '(' после 'for'");

        Statement init = null;
        if (match(SEMICOLON)) {
        } else if (match(KW_VAR)) {
            init = parseVarDecl(previous());
        } else {
            init = parseExprStatement();
        }

        Expression condition = null;
        if (!check(SEMICOLON)) {
            condition = parseExpression();
            if (!condition.getType().equals(FrogType.BOOL)) {
                throw error(peek(), "Условие цикла for должно быть bool");
            }
        }
        consume(SEMICOLON, "Ожидалась ';' после условия for");

        Expression increment = null;
        if (!check(RPAREN)) {
            increment = parseExpression();
        }
        consume(RPAREN, "Ожидалась ')' после частей for");

        loopDepth++;
        Statement body = parseStatement();
        loopDepth--;

        return new ForStmt(init, condition, increment, body, location(forToken));
    }

    private ReturnStmt parseReturn(Token returnToken) {
        Expression value = null;
        if (!check(SEMICOLON)) {
            value = parseExpression();
        }
        consume(SEMICOLON, "Ожидалась ';' после return");

        if (currentFunction == null) {
            throw error(returnToken, "return вне функции");
        }

        FrogType expected = currentFunction.getReturnType();
        FrogType actual = (value == null) ? FrogType.VOID : value.getType();

        if (!expected.isAssignableFrom(actual)) {
            throw error(returnToken, "Функция '" + currentFunction.getName()
                    + "' должна возвращать значение типа " + expected
                    + ", а не " + actual);
        }

        ReturnStmt stmt = new ReturnStmt(value, location(returnToken));
        stmt.setExpectedType(expected);
        return stmt;
    }
    private Statement parseIndexAssignOrExpr() {
        Expression left = parseCall();

        if (left instanceof IndexExpr idx && match(ASSIGN)) {
            Token eq = previous();
            Expression right = parseExpression();
            Token semi = consume(SEMICOLON, "Ожидалась ';' после присваивания в массив");

            FrogType arrT = idx.getArray().getType();
            FrogType idxT = idx.getIndex().getType();
            FrogType valT = right.getType();

            if (arrT.getKind() != FrogType.Kind.ARRAY) {
                throw error(eq, "Слева от '=' должен быть массив, найдено " + arrT);
            }
            if (!idxT.equals(FrogType.INT)) {
                throw error(eq, "Индекс массива должен быть int, найдено " + idxT);
            }

            FrogType elemT = arrT.getElementType();
            if (!elemT.isAssignableFrom(valT)) {
                throw error(eq, "Нельзя присвоить " + valT + " элементу массива типа " + elemT);
            }

            IndexAssignStmt st = new IndexAssignStmt(idx, right, location(semi));
            st.setValueType(valT);
            return st;
        }

        Token semi = consume(SEMICOLON, "Ожидалась ';' после выражения");
        return new ExprStmt(left, location(semi));
    }



    private BreakStmt parseBreak(Token breakToken) {
        consume(SEMICOLON, "Ожидалась ';' после break");
        if (loopDepth == 0) {
            throw error(breakToken, "break разрешён только внутри цикла");
        }
        return new BreakStmt(location(breakToken));
    }

    private ContinueStmt parseContinue(Token contToken) {
        consume(SEMICOLON, "Ожидалась ';' после continue");
        if (loopDepth == 0) {
            throw error(contToken, "continue разрешён только внутри цикла");
        }
        return new ContinueStmt(location(contToken));
    }

    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseLogicOr();

        if (match(ASSIGN)) {
            Token eq = previous();
            Expression value = parseAssignment();

            if (expr instanceof VarExpr varExpr) {
                Symbol sym = symbols.resolve(varExpr.getName());
                if (!(sym instanceof VarSymbol varSym)) {
                    throw error(eq, "Переменная '" + varExpr.getName() + "' не объявлена");
                }
                FrogType lhsType = varSym.getType();
                FrogType rhsType = value.getType();

                if (!lhsType.isAssignableFrom(rhsType)) {
                    throw error(eq, "Нельзя присвоить значение типа " + rhsType
                            + " переменной типа " + lhsType);
                }

                AssignExpr assign = new AssignExpr(varExpr.getName(), value, expr.getLocation());
                assign.setType(lhsType);
                return assign;
            }

            throw error(eq, "Недопустимое место для присваивания");
        }

        return expr;
    }

    private Expression parseLogicOr() {
        Expression expr = parseLogicAnd();

        while (match(OR)) {
            Token op = previous();
            Expression right = parseLogicAnd();
            expr = makeBinary(expr, BinaryOp.OR, right, op);
        }

        return expr;
    }

    private Expression parseLogicAnd() {
        Expression expr = parseEquality();

        while (match(AND)) {
            Token op = previous();
            Expression right = parseEquality();
            expr = makeBinary(expr, BinaryOp.AND, right, op);
        }

        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseComparison();

        while (match(EQ, NEQ)) {
            Token op = previous();
            Expression right = parseComparison();
            BinaryOp bop = (op.getType() == EQ) ? BinaryOp.EQ : BinaryOp.NEQ;
            expr = makeBinary(expr, bop, right, op);
        }

        return expr;
    }

    private Expression parseComparison() {
        Expression expr = parseTerm();

        while (match(LT, LE, GT, GE)) {
            Token op = previous();
            Expression right = parseTerm();
            BinaryOp bop = switch (op.getType()) {
                case LT -> BinaryOp.LT;
                case LE -> BinaryOp.LE;
                case GT -> BinaryOp.GT;
                case GE -> BinaryOp.GE;
                default -> throw new AssertionError();
            };
            expr = makeBinary(expr, bop, right, op);
        }

        return expr;
    }

    private Expression parseTerm() {
        Expression expr = parseFactor();

        while (match(PLUS, MINUS)) {
            Token op = previous();
            Expression right = parseFactor();
            BinaryOp bop = (op.getType() == PLUS) ? BinaryOp.PLUS : BinaryOp.MINUS;
            expr = makeBinary(expr, bop, right, op);
        }

        return expr;
    }

    private Expression parseFactor() {
        Expression expr = parseUnary();

        while (match(STAR, SLASH, PERCENT)) {
            Token op = previous();
            Expression right = parseUnary();
            BinaryOp bop = switch (op.getType()) {
                case STAR -> BinaryOp.MUL;
                case SLASH -> BinaryOp.DIV;
                case PERCENT -> BinaryOp.MOD;
                default -> throw new AssertionError();
            };
            expr = makeBinary(expr, bop, right, op);
        }

        return expr;
    }

    private Expression parseUnary() {
        if (match(NOT)) {
            Token op = previous();
            Expression right = parseUnary();
            if (!right.getType().equals(FrogType.BOOL)) {
                throw error(op, "Оператор '!' применим только к bool");
            }
            UnaryExpr expr = new UnaryExpr(UnaryOp.NOT, right, location(op));
            expr.setType(FrogType.BOOL);
            return expr;
        }

        if (match(MINUS)) {
            Token op = previous();
            Expression right = parseUnary();
            if (!right.getType().isNumeric()) {
                throw error(op, "Унарный '-' применим только к числовым типам");
            }
            UnaryExpr expr = new UnaryExpr(UnaryOp.NEGATE, right, location(op));
            expr.setType(right.getType());
            return expr;
        }

        return parseCall();
    }

    private Expression parseCall() {
        Expression expr = parsePrimary();

        while (true) {
            if (match(LPAREN)) {
                expr = finishCall(expr, previous());
            } else if (match(LBRACK)) {
                Token lb = previous();
                Expression index = parseExpression();
                consume(RBRACK, "Ожидался ']' после индекса массива");
                if (!index.getType().equals(FrogType.INT)) {
                    throw error(lb, "Индекс массива должен быть типа int");
                }
                if (expr.getType().getKind() != FrogType.Kind.ARRAY) {
                    throw error(lb, "Ожидался тип array<...> для индексирования, найдено " + expr.getType());
                }
                IndexExpr i = new IndexExpr(expr, index, expr.getLocation());
                i.setType(expr.getType().getElementType());
                expr = i;
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression finishCall(Expression calleeExpr, Token lparen) {
        if (!(calleeExpr instanceof VarExpr varCallee)) {
            throw error(lparen, "Вызывать можно только функции по имени");
        }

        List<Expression> args = new ArrayList<>();
        if (!check(RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(COMMA));
        }
        Token rparen = consume(RPAREN, "Ожидалась ')' после аргументов функции");

        Symbol sym = symbols.resolve(varCallee.getName());
        if (!(sym instanceof FuncSymbol funcSym)) {
            throw error(lparen, "'" + varCallee.getName() + "' не является функцией");
        }

        if (funcSym.getParamTypes().size() != args.size()) {
            throw error(lparen, "Функция '" + funcSym.getName() + "' ожидает "
                    + funcSym.getParamTypes().size() + " аргументов, передано " + args.size());
        }

        for (int i = 0; i < args.size(); i++) {
            FrogType expected = funcSym.getParamTypes().get(i);
            FrogType actual = args.get(i).getType();

            if (funcSym.getName().equals("print")) {
                FrogType.Kind k = actual.getKind();
                if (k == FrogType.Kind.INT || k == FrogType.Kind.FLOAT
                        || k == FrogType.Kind.BOOL || k == FrogType.Kind.STRING) {
                    continue;
                }
            }

            if (!expected.isAssignableFrom(actual)) {
                throw error(lparen, "Аргумент #" + (i + 1) + " функции '" + funcSym.getName()
                        + "' должен быть типа " + expected + ", найдено " + actual);
            }
        }

        CallExpr call = new CallExpr(varCallee.getName(), args, location(lparen));
        call.setType(funcSym.getReturnType());
        return call;
    }

    private Expression parsePrimary() {
        Token t = advance();
        switch (t.getType()) {
            case BOOL_TRUE: {
                LiteralExpr e = new LiteralExpr(Boolean.TRUE, location(t));
                e.setType(FrogType.BOOL);
                return e;
            }
            case BOOL_FALSE: {
                LiteralExpr e = new LiteralExpr(Boolean.FALSE, location(t));
                e.setType(FrogType.BOOL);
                return e;
            }
            case INT_LITERAL: {
                int v = Integer.parseInt(t.getLexeme());
                LiteralExpr e = new LiteralExpr(v, location(t));
                e.setType(FrogType.INT);
                return e;
            }
            case FLOAT_LITERAL: {
                double v = Double.parseDouble(t.getLexeme());
                LiteralExpr e = new LiteralExpr(v, location(t));
                e.setType(FrogType.FLOAT);
                return e;
            }
            case STRING_LITERAL: {
                LiteralExpr e = new LiteralExpr(t.getLexeme(), location(t));
                e.setType(FrogType.STRING);
                return e;
            }
            case IDENT: {
                String name = t.getLexeme();
                Symbol sym = symbols.resolve(name);
                if (sym == null) {
                    throw error(t, "Идентификатор '" + name + "' не объявлен");
                }
                VarExpr v = new VarExpr(name, location(t));
                v.setType(sym.getType());
                return v;
            }
            case LPAREN: {
                Expression expr = parseExpression();
                consume(RPAREN, "Ожидалась ')' после выражения");
                return expr;
            }
            case LBRACE: {
                List<Expression> elems = new ArrayList<>();
                if (!check(RBRACE)) {
                    do {
                        elems.add(parseExpression());
                    } while (match(COMMA));
                }
                Token rbrace = consume(RBRACE, "Ожидалась '}' после литерала массива");

                if (elems.isEmpty()) {
                    ArrayLiteralExpr arr = new ArrayLiteralExpr(elems, location(t));
                    arr.setType(FrogType.arrayOf(FrogType.VOID));
                    return arr;
                } else {
                    FrogType elemType = elems.get(0).getType();
                    for (int i = 1; i < elems.size(); i++) {
                        FrogType t2 = elems.get(i).getType();
                        if (!elemType.isAssignableFrom(t2)) {
                            throw error(rbrace, "Все элементы массива должны иметь одинаковый тип, "
                                    + "ожидался " + elemType + ", найдено " + t2);
                        }
                    }
                    ArrayLiteralExpr arr = new ArrayLiteralExpr(elems, location(t));
                    arr.setType(FrogType.arrayOf(elemType));
                    return arr;
                }
            }
            default:
                throw error(t, "Ожидалось выражение, найдено " + t.getLexeme());
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        skipComments();
        return previous();
    }

    private void skipComments() {
        while (!isAtEnd()
                && tokens.get(current).getType() == LINE_COMMENT) {
            current++;
        }
    }

    private boolean isAtEnd() {
        return peekRaw().getType() == EOF;
    }

    private Token peek() {
        skipComments();
        return peekRaw();
    }

    private Token peekRaw() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseException error(Token token, String message) {
        return new ParseException(token, message);
    }

    private SourceLocation location(Token t) {
        return new SourceLocation(t.getLine(), t.getColumn());
    }

    private BinaryExpr makeBinary(Expression left, BinaryOp op,
                                  Expression right, Token opToken) {

        switch (op) {
            case PLUS, MINUS, MUL, DIV, MOD -> {
                FrogType lt = left.getType();
                FrogType rt = right.getType();
                if (op == BinaryOp.PLUS
                        && lt.equals(FrogType.STRING)
                        && rt.equals(FrogType.STRING)) {
                    BinaryExpr e = new BinaryExpr(left, op, right, location(opToken));
                    e.setType(FrogType.STRING);
                    return e;
                }
                if (!lt.isNumeric() || !rt.isNumeric()) {
                    throw error(opToken, "Арифметические операции допустимы только для чисел");
                }
                if (!lt.equals(rt)) {
                    throw error(opToken, "Операнды должны иметь одинаковый числовой тип, "
                            + "получено " + lt + " и " + rt);
                }
                BinaryExpr e = new BinaryExpr(left, op, right, location(opToken));
                e.setType(lt);
                return e;
            }
            case EQ, NEQ -> {
                FrogType lt = left.getType();
                FrogType rt = right.getType();
                if (!lt.equals(rt)) {
                    throw error(opToken, "Сравнение ==/!= возможно только для одинаковых типов, "
                            + "получено " + lt + " и " + rt);
                }
                BinaryExpr e = new BinaryExpr(left, op, right, location(opToken));
                e.setType(FrogType.BOOL);
                return e;
            }
            case LT, LE, GT, GE -> {
                FrogType lt = left.getType();
                FrogType rt = right.getType();
                if (!lt.isNumeric() || !rt.isNumeric() || !lt.equals(rt)) {
                    throw error(opToken,
                            "Операции сравнения <, <=, >, >= допустимы только для чисел "
                                    + "одинакового типа, получено " + lt + " и " + rt);
                }
                BinaryExpr e = new BinaryExpr(left, op, right, location(opToken));
                e.setType(FrogType.BOOL);
                return e;
            }
            case AND, OR -> {
                if (!left.getType().equals(FrogType.BOOL)
                        || !right.getType().equals(FrogType.BOOL)) {
                    throw error(opToken,
                            "Логические операции && и || допустимы только для bool");
                }
                BinaryExpr e = new BinaryExpr(left, op, right, location(opToken));
                e.setType(FrogType.BOOL);
                return e;
            }
            default -> throw new AssertionError();
        }
    }
    private boolean alwaysReturns(Statement stmt) {
        if (stmt instanceof ReturnStmt) {
            return true;
        } else if (stmt instanceof BlockStmt b) {
            List<Statement> stmts = b.getStatements();
            for (Statement s : stmts) {
                if (alwaysReturns(s)) {
                    return true;
                }
            }
            return false;
        } else if (stmt instanceof IfStmt ifs) {
            if (ifs.getElseBranch() == null) return false;
            return alwaysReturns(ifs.getThenBranch())
                    && alwaysReturns(ifs.getElseBranch());
        }
        return false;
    }
}
