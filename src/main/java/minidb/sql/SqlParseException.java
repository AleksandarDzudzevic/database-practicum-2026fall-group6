package minidb.sql;

/** A SQL syntax error. Planning and storage failures use their own types. */
public final class SqlParseException extends RuntimeException {
    private final int offset;
    private final int line;
    private final int column;

    /** Used when the input has no source position, such as a null string. */
    public SqlParseException(String message) {
        this(message, -1, 0, 0);
    }

    public SqlParseException(String message, int offset, int line, int column) {
        super(message);
        this.offset = offset;
        this.line = line;
        this.column = column;
    }

    /** Zero-based index into the original SQL, or -1 when there is no source. */
    public int offset() {
        return offset;
    }

    /** One-based line, or 0 when there is no source. */
    public int line() {
        return line;
    }

    /** One-based column, or 0 when there is no source. A tab counts as one column. */
    public int column() {
        return column;
    }
}
