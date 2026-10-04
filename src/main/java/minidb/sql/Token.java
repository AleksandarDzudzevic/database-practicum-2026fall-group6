package minidb.sql;

/**
 * One lexical token. {@code text} is the raw lexeme, except string tokens,
 * whose text is the decoded value. {@code number} is meaningful for integers.
 */
public record Token(Kind kind, String text, int number, int offset, int line, int column) {
    public Token {
        if (kind == null || text == null) {
            throw new IllegalArgumentException("Token requires a kind and text");
        }
    }

    public enum Kind {
        SELECT, FROM, WHERE, AND, OR, NOT, NULL,
        IDENTIFIER, STAR, COMMA, DOT, LPAREN, RPAREN, SEMICOLON,
        EQ, NE, LT, LE, GT, GE,
        INTEGER, STRING, EOF
    }

    public static Token of(Kind kind, String text, int offset, int line, int column) {
        return new Token(kind, text, 0, offset, line, column);
    }

    public static Token integer(String lexeme, int value, int offset, int line, int column) {
        return new Token(Kind.INTEGER, lexeme, value, offset, line, column);
    }

    public int intValue() {
        if (kind != Kind.INTEGER) {
            throw new IllegalStateException("Not an integer token: " + kind);
        }
        return number;
    }
}
