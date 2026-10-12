package minidb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns one SQL string into tokens. Each tokenizer scans its input once;
 * create a new one for every statement.
 */
public final class SqlTokenizer {
    private final String sql;
    private int index;
    private int line = 1;
    private int column = 1;
    private boolean carriageReturn;
    private boolean finished;

    public SqlTokenizer(String sql) {
        if (sql == null) {
            throw new SqlParseException("SQL input is null");
        }
        this.sql = sql;
    }

    public static List<Token> tokenize(String sql) {
        SqlTokenizer tokenizer = new SqlTokenizer(sql);
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token token = tokenizer.next();
            tokens.add(token);
            if (token.kind() == Token.Kind.EOF) {
                return List.copyOf(tokens);
            }
        }
    }

    public Token next() {
        if (finished) {
            return Token.of(Token.Kind.EOF, "", index, line, column);
        }
        skipWhitespace();
        if (index >= sql.length()) {
            if (sql.isBlank()) {
                String detail = sql.isEmpty() ? "SQL input is empty" : "SQL input is whitespace only";
                throw error(0, 1, 1, detail);
            }
            finished = true;
            return Token.of(Token.Kind.EOF, "", index, line, column);
        }
        return readToken();
    }

    private Token readToken() {
        int startOffset = index;
        int startLine = line;
        int startColumn = column;
        char c = sql.charAt(index);
        if (isIdentifierStart(c)) {
            return identifier(startOffset, startLine, startColumn);
        }
        if (isDigit(c)) {
            return number(0, startOffset, startLine, startColumn);
        }
        if (c == '+' || c == '-') {
            if (index + 1 < sql.length() && isDigit(sql.charAt(index + 1))) {
                int sign = c == '-' ? -1 : 1;
                consume();
                return number(sign, startOffset, startLine, startColumn);
            }
            throw error(startOffset, startLine, startColumn, "unexpected character '" + c + "'");
        }
        if (c == '\'') {
            return string(startOffset, startLine, startColumn);
        }
        if (c == '<') {
            if (startsWith("<>")) {
                return operator(Token.Kind.NE, "<>", startOffset, startLine, startColumn);
            }
            if (startsWith("<=")) {
                return operator(Token.Kind.LE, "<=", startOffset, startLine, startColumn);
            }
            return operator(Token.Kind.LT, "<", startOffset, startLine, startColumn);
        }
        if (c == '>') {
            if (startsWith(">=")) {
                return operator(Token.Kind.GE, ">=", startOffset, startLine, startColumn);
            }
            return operator(Token.Kind.GT, ">", startOffset, startLine, startColumn);
        }
        Token.Kind single = switch (c) {
            case '*' -> Token.Kind.STAR;
            case ',' -> Token.Kind.COMMA;
            case '.' -> Token.Kind.DOT;
            case '(' -> Token.Kind.LPAREN;
            case ')' -> Token.Kind.RPAREN;
            case ';' -> Token.Kind.SEMICOLON;
            case '=' -> Token.Kind.EQ;
            default -> null;
        };
        if (single != null) {
            consume();
            return Token.of(single, String.valueOf(c), startOffset, startLine, startColumn);
        }
        throw error(startOffset, startLine, startColumn, "unexpected character " + describe(c));
    }

    private boolean startsWith(String prefix) {
        return sql.startsWith(prefix, index);
    }

    private Token operator(Token.Kind kind, String text, int startOffset, int startLine, int startColumn) {
        for (int i = 0; i < text.length(); i++) {
            consume();
        }
        return Token.of(kind, text, startOffset, startLine, startColumn);
    }

    private Token identifier(int startOffset, int startLine, int startColumn) {
        StringBuilder text = new StringBuilder();
        while (index < sql.length() && isIdentifierPart(sql.charAt(index))) {
            text.append((char) consume());
        }
        String lexeme = text.toString();
        Token.Kind keyword = keyword(lexeme);
        return Token.of(keyword == null ? Token.Kind.IDENTIFIER : keyword, lexeme,
                startOffset, startLine, startColumn);
    }

    private Token number(int sign, int startOffset, int startLine, int startColumn) {
        StringBuilder lexeme = new StringBuilder();
        if (sign < 0) {
            lexeme.append('-');
        } else if (sign > 0) {
            lexeme.append('+');
        }
        if (index >= sql.length() || !isDigit(sql.charAt(index))) {
            throw error(startOffset, startLine, startColumn, "integer literal requires digits");
        }
        long magnitude = 0;
        boolean overflow = false;
        while (index < sql.length() && isDigit(sql.charAt(index))) {
            int digit = sql.charAt(index) - '0';
            lexeme.append(sql.charAt(index));
            if (!overflow) {
                if (magnitude > (Long.MAX_VALUE - digit) / 10) {
                    overflow = true;
                } else {
                    magnitude = magnitude * 10 + digit;
                }
            }
            consume();
        }
        long limit = sign < 0 ? 2147483648L : 2147483647L;
        if (overflow || magnitude > limit) {
            throw error(startOffset, startLine, startColumn,
                    "integer literal out of range: " + lexeme);
        }
        int value = sign < 0
                ? (magnitude == 2147483648L ? Integer.MIN_VALUE : (int) -magnitude)
                : (int) magnitude;
        return Token.integer(lexeme.toString(), value, startOffset, startLine, startColumn);
    }

    private Token string(int startOffset, int startLine, int startColumn) {
        consume();
        StringBuilder decoded = new StringBuilder();
        while (index < sql.length()) {
            char c = sql.charAt(index);
            if (c == '\'') {
                if (index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    consume();
                    consume();
                    decoded.append('\'');
                } else {
                    consume();
                    return Token.of(Token.Kind.STRING, decoded.toString(),
                            startOffset, startLine, startColumn);
                }
            } else {
                decoded.append((char) consume());
            }
        }
        throw error(startOffset, startLine, startColumn, "unterminated string literal");
    }

    private void skipWhitespace() {
        while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) {
            consume();
        }
    }

    private int consume() {
        char c = sql.charAt(index++);
        if (c == '\r') {
            line++;
            column = 1;
            carriageReturn = true;
        } else if (c == '\n') {
            if (!carriageReturn) {
                line++;
                column = 1;
            }
            carriageReturn = false;
        } else {
            column++;
            carriageReturn = false;
        }
        return c;
    }

    private static Token.Kind keyword(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "select" -> Token.Kind.SELECT;
            case "from" -> Token.Kind.FROM;
            case "where" -> Token.Kind.WHERE;
            case "and" -> Token.Kind.AND;
            case "or" -> Token.Kind.OR;
            case "not" -> Token.Kind.NOT;
            case "null" -> Token.Kind.NULL;
            default -> null;
        };
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String describe(char c) {
        if (c >= 32 && c < 127) {
            return "'" + c + "'";
        }
        return String.format("U+%04X", (int) c);
    }

    private static SqlParseException error(int offset, int line, int column, String detail) {
        return new SqlParseException(
                "line " + line + ", column " + column + ": " + detail, offset, line, column);
    }
}
