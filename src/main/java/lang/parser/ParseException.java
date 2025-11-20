package lang.parser;

import lang.lexer.token.Token;

public class ParseException extends RuntimeException {

    private final Token token;

    public ParseException(Token token, String message) {
        super(formatMessage(token, message));
        this.token = token;
    }

    private static String formatMessage(Token token, String message) {
        if (token == null) return message;
        return message + " at " + token.getLine() + ":" + token.getColumn()
                + " near '" + token.getLexeme() + "'";
    }

    public Token getToken() { return token; }
}
