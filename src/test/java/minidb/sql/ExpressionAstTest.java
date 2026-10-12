package minidb.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import minidb.sql.ast.ColumnRef;
import minidb.sql.ast.Expression;
import minidb.sql.ast.Query;
import minidb.sql.ast.SelectItem;
import minidb.sql.ast.TableRef;

class ExpressionAstTest {
    @Test
    void recordsCompareByStructure() {
        Expression.IntLiteral eighteen = new Expression.IntLiteral(18);
        assertEquals(eighteen, new Expression.IntLiteral(18));
        assertEquals(eighteen.hashCode(), new Expression.IntLiteral(18).hashCode());
        assertNotEquals(eighteen, new Expression.IntLiteral(19));

        Expression.StringLiteral empty = new Expression.StringLiteral("");
        assertEquals(empty, new Expression.StringLiteral(""));
        assertNotEquals(empty, new Expression.StringLiteral("Alice"));

        Expression age = new Expression.Comparison(
                new ColumnRef(null, "age"), Expression.ComparisonOp.GE, eighteen);
        Expression name = new Expression.Comparison(
                new ColumnRef("students", "name"),
                Expression.ComparisonOp.NE,
                new Expression.StringLiteral(""));
        Expression where = new Expression.And(age, name);
        assertEquals(where, new Expression.And(age, name));
        assertEquals(new Expression.Or(age, name), new Expression.Or(age, name));
        assertEquals(new Expression.Not(where), new Expression.Not(where));
        assertNotEquals(new Expression.And(age, name), new Expression.Or(age, name));
        assertEquals(new Expression.Star(), new Expression.Star());

        Query query = new Query(
                List.of(new SelectItem(new ColumnRef(null, "id"), null)),
                List.of(new TableRef("students", null)),
                List.of(),
                where);
        assertEquals(query, new Query(
                List.of(new SelectItem(new ColumnRef(null, "id"), null)),
                List.of(new TableRef("students", null)),
                List.of(),
                new Expression.And(age, name)));
        assertNull(new Query(List.of(), List.of(), List.of(), null).where());
    }

    @Test
    void comparisonOperatorsMatchTheSharedContract() {
        assertEquals(List.of(
                Expression.ComparisonOp.EQ,
                Expression.ComparisonOp.NE,
                Expression.ComparisonOp.LT,
                Expression.ComparisonOp.LE,
                Expression.ComparisonOp.GT,
                Expression.ComparisonOp.GE),
                List.of(Expression.ComparisonOp.values()));
    }

    @Test
    void expressionNodesAreExpressionImplementations() {
        Expression expression = new Expression.Not(new Expression.IntLiteral(1));
        assertTrue(expression instanceof Expression.Not);
    }

    @Test
    void parseExceptionCarriesSourcePosition() {
        SqlParseException positioned = new SqlParseException("bad token", 4, 2, 3);
        assertEquals("bad token", positioned.getMessage());
        assertEquals(4, positioned.offset());
        assertEquals(2, positioned.line());
        assertEquals(3, positioned.column());
        assertTrue(positioned instanceof RuntimeException);

        SqlParseException unpositioned = new SqlParseException("SQL input is null");
        assertEquals(-1, unpositioned.offset());
        assertEquals(0, unpositioned.line());
        assertEquals(0, unpositioned.column());
        assertNull(unpositioned.getCause());
        assertThrows(IllegalArgumentException.class, () -> new Token(null, "", 0, 0, 1, 1));
    }
}
