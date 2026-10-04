package minidb.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SqlTokenizerTest {
    @Test
    void keywordsAreCaseInsensitiveAndDoNotSwallowLongerNames() {
        List<Token> tokens = SqlTokenizer.tokenize("SeLeCt fromage FROM WhErE AND OR NOT");
        assertEquals(List.of(
                Token.Kind.SELECT,
                Token.Kind.IDENTIFIER,
                Token.Kind.FROM,
                Token.Kind.WHERE,
                Token.Kind.AND,
                Token.Kind.OR,
                Token.Kind.NOT,
                Token.Kind.EOF), kinds(tokens));
        assertEquals("SeLeCt", tokens.get(0).text());
        assertEquals("fromage", tokens.get(1).text());
        assertEquals("WhErE", tokens.get(3).text());
    }

    @Test
    void identifiersPreserveCaseAndRejectKeywordPrefixesOnlyAsWholeWords() {
        List<Token> tokens = SqlTokenizer.tokenize("Students _id OR_1 NOTE ANDY select1");
        assertEquals(List.of(
                "Students", "_id", "OR_1", "NOTE", "ANDY", "select1"),
                tokens.subList(0, 6).stream().map(Token::text).toList());
        assertTrue(tokens.subList(0, 6).stream().allMatch(token -> token.kind() == Token.Kind.IDENTIFIER));
    }

    @Test
    void unquotedNullIsNotAnIdentifier() {
        List<Token> tokens = SqlTokenizer.tokenize("Null NULL");
        assertEquals(List.of(Token.Kind.NULL, Token.Kind.NULL, Token.Kind.EOF), kinds(tokens));
        assertEquals("Null", tokens.get(0).text());
        assertEquals(Token.Kind.STRING, SqlTokenizer.tokenize("'NULL'").get(0).kind());
        assertEquals("NULL", SqlTokenizer.tokenize("'NULL'").get(0).text());
    }

    @Test
    void operatorsAreRecognizedWithoutSpaces() {
        List<Token> tokens = SqlTokenizer.tokenize("a<>b a<=c a>=d a=e a<f a>g");
        assertEquals(List.of(
                Token.Kind.IDENTIFIER, Token.Kind.NE, Token.Kind.IDENTIFIER,
                Token.Kind.IDENTIFIER, Token.Kind.LE, Token.Kind.IDENTIFIER,
                Token.Kind.IDENTIFIER, Token.Kind.GE, Token.Kind.IDENTIFIER,
                Token.Kind.IDENTIFIER, Token.Kind.EQ, Token.Kind.IDENTIFIER,
                Token.Kind.IDENTIFIER, Token.Kind.LT, Token.Kind.IDENTIFIER,
                Token.Kind.IDENTIFIER, Token.Kind.GT, Token.Kind.IDENTIFIER,
                Token.Kind.EOF), kinds(tokens));
        assertEquals("<>", tokens.get(1).text());
        assertEquals("<=", tokens.get(4).text());
        assertEquals(">=", tokens.get(7).text());
    }

    @Test
    void punctuationAndQualifiedNamesStaySeparate() {
        assertEquals(List.of(
                Token.Kind.STAR, Token.Kind.COMMA, Token.Kind.DOT,
                Token.Kind.LPAREN, Token.Kind.RPAREN, Token.Kind.SEMICOLON,
                Token.Kind.EOF), kinds(SqlTokenizer.tokenize("* , . ( ) ;")));
        List<Token> qualified = SqlTokenizer.tokenize("students.age");
        assertEquals(List.of(Token.Kind.IDENTIFIER, Token.Kind.DOT, Token.Kind.IDENTIFIER, Token.Kind.EOF),
                kinds(qualified));
        assertEquals(List.of("students", ".", "age"),
                qualified.subList(0, 3).stream().map(Token::text).toList());
    }

    @Test
    void whitespaceIncludesTabsNewlinesAndFormFeeds() {
        List<Token> tokens = SqlTokenizer.tokenize("\tSELECT\f*\r\nFROM\nstudents");
        assertEquals(List.of(
                Token.Kind.SELECT, Token.Kind.STAR, Token.Kind.FROM,
                Token.Kind.IDENTIFIER, Token.Kind.EOF), kinds(tokens));
        assertEquals(1, tokens.get(0).line());
        assertEquals(2, tokens.get(0).column());
        assertEquals(1, tokens.get(0).offset());
        Token from = tokens.get(2);
        assertEquals(2, from.line());
        assertEquals(1, from.column());
        assertEquals(11, from.offset());
        Token students = tokens.get(3);
        assertEquals(3, students.line());
        assertEquals(1, students.column());
    }

    @Test
    void integersCoverSignsZerosAndIntBounds() {
        assertInteger("0", "0", 0);
        assertInteger("42", "42", 42);
        assertInteger("+7", "+7", 7);
        assertInteger("-1", "-1", -1);
        assertInteger("+0", "+0", 0);
        assertInteger("-0", "-0", 0);
        assertInteger("007", "007", 7);
        assertInteger("2147483647", "2147483647", Integer.MAX_VALUE);
        assertInteger("+2147483647", "+2147483647", Integer.MAX_VALUE);
        assertInteger("-2147483647", "-2147483647", -2147483647);
        assertInteger("-2147483648", "-2147483648", Integer.MIN_VALUE);
    }

    @Test
    void integerOverflowDoesNotLeakNumberFormatException() {
        assertOverflow("2147483648");
        assertOverflow("-2147483649");
        assertOverflow("0002147483648");
        assertOverflow("-0002147483649");
        assertOverflow("999999999999999999999");
        SqlParseException ex = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("- 18"));
        assertTrue(ex.getMessage().contains("unexpected character '-'"));
        assertEquals(0, ex.offset());
        assertNull(ex.getCause());
    }

    @Test
    void numberThenNameIsTwoTokens() {
        List<Token> tokens = SqlTokenizer.tokenize("+12age");
        assertEquals(Token.Kind.INTEGER, tokens.get(0).kind());
        assertEquals(12, tokens.get(0).intValue());
        assertEquals(Token.Kind.IDENTIFIER, tokens.get(1).kind());
        assertEquals("age", tokens.get(1).text());
    }

    @Test
    void stringsDecodeQuotesAndKeepOtherCharacters() {
        assertEquals("", string("''"));
        assertEquals("O'Brien", string("'O''Brien'"));
        assertEquals("'", string("''''"));
        assertEquals("Zoë東京", string("'Zoë東京'"));
        assertEquals("say \\ hi", string("'say \\ hi'"));
        assertEquals(",", string("','"));
        assertEquals("SELECT", string("'SELECT'"));
        assertEquals("( )", string("'( )'"));
        assertEquals("a\nb", string("'a\nb'"));
    }

    @Test
    void unterminatedStringPointsAtTheOpeningQuote() {
        SqlParseException ex = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("SELECT 'ab"));
        assertTrue(ex.getMessage().contains("unterminated string literal"));
        assertEquals(7, ex.offset());
        assertEquals(1, ex.line());
        assertEquals(8, ex.column());
        assertNull(ex.getCause());

        SqlParseException multiline = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("'\n"));
        assertEquals(0, multiline.offset());
        assertEquals(1, multiline.line());
        assertEquals(1, multiline.column());
    }

    @Test
    void invalidCharactersAndCommentsAreNotSkipped() {
        SqlParseException dollar = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("SELECT $"));
        assertTrue(dollar.getMessage().contains("unexpected character '$'"));
        assertEquals(7, dollar.offset());
        assertEquals(1, dollar.line());
        assertEquals(8, dollar.column());

        SqlParseException quoted = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("\"name\""));
        assertTrue(quoted.getMessage().contains("unexpected character '\"'"));

        SqlParseException comment = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("-- note"));
        assertTrue(comment.getMessage().contains("unexpected character '-'"));
        assertEquals(0, comment.offset());

        SqlParseException block = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("/* note */"));
        assertTrue(block.getMessage().contains("unexpected character '/'"));

        SqlParseException letter = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize("café"));
        assertEquals(3, letter.offset());
    }

    @Test
    void emptyAndWhitespaceInputAreParseErrors() {
        SqlParseException empty = assertThrows(SqlParseException.class, () -> SqlTokenizer.tokenize(""));
        assertEquals("line 1, column 1: SQL input is empty", empty.getMessage());
        assertEquals(0, empty.offset());
        assertEquals(1, empty.line());
        assertEquals(1, empty.column());

        SqlParseException spaces = assertThrows(SqlParseException.class,
                () -> SqlTokenizer.tokenize(" \t\r\n"));
        assertTrue(spaces.getMessage().contains("whitespace only"));
        assertEquals(0, spaces.offset());
    }

    @Test
    void nullInputHasNoSourcePosition() {
        SqlParseException ex = assertThrows(SqlParseException.class, () -> new SqlTokenizer(null));
        assertEquals("SQL input is null", ex.getMessage());
        assertEquals(-1, ex.offset());
        assertEquals(0, ex.line());
        assertEquals(0, ex.column());
        assertNull(ex.getCause());
    }

    @Test
    void endOfInputIsATokenAndFurtherReadsStayThere() {
        SqlTokenizer tokenizer = new SqlTokenizer("id");
        Token identifier = tokenizer.next();
        Token eof = tokenizer.next();
        Token again = tokenizer.next();
        assertEquals(Token.Kind.IDENTIFIER, identifier.kind());
        assertEquals(Token.Kind.EOF, eof.kind());
        assertEquals(2, eof.offset());
        assertEquals(1, eof.line());
        assertEquals(3, eof.column());
        assertEquals(eof, again);
    }

    @Test
    void aFailureDoesNotAffectTheNextTokenizer() {
        assertThrows(SqlParseException.class, () -> SqlTokenizer.tokenize("'open"));
        List<Token> tokens = SqlTokenizer.tokenize("SELECT id");
        assertEquals(List.of(Token.Kind.SELECT, Token.Kind.IDENTIFIER, Token.Kind.EOF), kinds(tokens));
        assertEquals(0, tokens.get(0).offset());
        assertEquals(7, tokens.get(1).offset());
    }

    private static void assertInteger(String sql, String lexeme, int value) {
        List<Token> tokens = SqlTokenizer.tokenize(sql);
        assertEquals(List.of(Token.Kind.INTEGER, Token.Kind.EOF), kinds(tokens));
        assertEquals(lexeme, tokens.get(0).text());
        assertEquals(value, tokens.get(0).intValue());
        assertThrows(IllegalStateException.class, () -> tokens.get(1).intValue());
    }

    private static void assertOverflow(String sql) {
        SqlParseException ex = assertThrows(SqlParseException.class, () -> SqlTokenizer.tokenize(sql));
        assertTrue(ex.getMessage().contains("integer literal out of range: " + sql));
        assertEquals(0, ex.offset());
        assertNull(ex.getCause());
        assertTrue(!(ex.getCause() instanceof NumberFormatException));
    }

    private static String string(String sql) {
        List<Token> tokens = SqlTokenizer.tokenize(sql);
        assertEquals(List.of(Token.Kind.STRING, Token.Kind.EOF), kinds(tokens));
        assertEquals(0, tokens.get(0).offset());
        return tokens.get(0).text();
    }

    private static List<Token.Kind> kinds(List<Token> tokens) {
        return tokens.stream().map(Token::kind).toList();
    }
}
