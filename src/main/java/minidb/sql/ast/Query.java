package minidb.sql.ast;

import java.util.List;

/**
 * Select-project-join shape. {@code where} is null when the query has no filter.
 * Grouping, ordering, and subqueries can be added as further fields.
 */
public record Query(
        List<SelectItem> select,
        List<TableRef> from,
        List<Join> joins,
        Expression where) implements Statement {
}
