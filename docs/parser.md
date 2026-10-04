# SQL parser

This component turns one SQL statement into a `Query` tree. It checks syntax only.
Table lookup, column binding, operand types, and row evaluation belong to the
planner and executor. The parser does not read `.csv` or `.schema` files.

Version 1 accepts one `SELECT` over one unaliased table, with an optional `WHERE`
and an optional final semicolon. The statement must consume the whole string.
`docs/query-filtering.md` describes the larger single-table pipeline; this guide
covers the parser that is implemented now.

## Call the parser

```java
Parser parser = new SqlParser();
Query query = (Query) parser.parse("""
        SELECT id, name
        FROM students
        WHERE age >= 18 AND name <> '';
        """);
```

Imports are `minidb.sql.Parser`, `minidb.sql.SqlParser`, and `minidb.sql.ast.*`.
Each `parse` call scans its own input, so a failed call does not affect the next
one. A null, empty, or whitespace-only string throws `SqlParseException`.

The example above has this shape:

```text
Query
  select: ColumnRef(null, "id"), ColumnRef(null, "name")
  from:   TableRef("students", null)
  joins:  empty
  where:  And
            Comparison(ColumnRef(null, "age"), GE, IntLiteral(18))
            Comparison(ColumnRef(null, "name"), NE, StringLiteral(""))
```

Select-item aliases and the table alias are null. `where` is null when the
query has no filter. The select, from, and join lists are immutable snapshots.

## Grammar

```text
query         := SELECT select_list FROM identifier [WHERE predicate] [;] EOF
select_list   := * | column_ref (, column_ref)*
predicate     := or_expr
or_expr       := and_expr (OR and_expr)*
and_expr      := not_expr (AND not_expr)*
not_expr      := NOT not_expr | '(' predicate ')' | comparison
comparison    := operand comparison_op operand
operand       := column_ref | signed_integer | string_literal
column_ref    := identifier ['.' identifier]
comparison_op := '=' | '<>' | '<' | '<=' | '>' | '>='
```

Keywords are case-insensitive. Comparison binds before `NOT`, then `AND`, then
`OR`. `AND` and `OR` associate to the left. Parentheses override that order.

`SELECT *` is legal only as the sole select item. The select list keeps order
and repeated columns. A column is a bare name or `table.column`; identifier case
is preserved. Unqualified columns use a null table in `ColumnRef`.

## Literals, names, and whitespace

- Identifiers match storage's `[A-Za-z_][A-Za-z0-9_]*`. `SELECT`, `FROM`,
  `WHERE`, `AND`, `OR`, and `NOT` are reserved. Unquoted `NULL` is rejected.
  A name such as `order` or `join` can still be a column or table; `ORDER BY`
  and `JOIN` cannot.
- Integers are signed 32-bit values, including `-2147483648`. A sign must sit
  directly against the digits. Leading zeros are accepted and use the numeric
  value, so `007` is 7. Overflow is a parse error.
- Strings use single quotes. `''` inside a literal is one quote, as in
  `'O''Brien'`. Empty strings and Unicode text are kept. A backslash is an
  ordinary character. Double quotes are not string delimiters.
- `parse` accepts ordinary whitespace and line breaks, including tabs. The CLI
  still reads one trimmed line and does not assemble a multiline statement.
- The text `'NULL'` is a string. `age = '18'` and `name > 'Alice'` parse.
  Whether those comparisons are legal for the table's types is a planning
  decision. Unknown columns and qualifiers stay in the tree.

## AST nodes

Statement and table nodes were already in the repository. Expression nodes live
inside `sql/ast/Expression.java`:

| SQL | Java |
| --- | --- |
| `18` | `Expression.IntLiteral` |
| `'O''Brien'` | `Expression.StringLiteral` with decoded text |
| `= <> < <= > >=` | `ComparisonOp.EQ, NE, LT, LE, GT, GE` |
| comparison | `Expression.Comparison` |
| `AND` / `OR` / `NOT` | `Expression.And`, `Or`, `Not` |
| lone `*` | `Expression.Star` as the only `SelectItem` |
| `students.age` / `age` | existing `ColumnRef` |

`ColumnRef` remains its own record. The parser does not build `Join` values or
bound expressions. `ComparisonOp` is the enum a later planner can share when it
binds columns to indexes.

## What this version rejects

Unsupported syntax fails with `SqlParseException`. It is not ignored. The
message describes the error and includes a 1-based line and column. The exception's
`offset()` accessor provides the 0-based position in the original input; that
offset is not printed in the message. A tab counts as one column. Line breaks are `\n`,
`\r\n`, or a bare `\r`.

Rejected forms include JOIN, aliases, more than one table, `table.*`, `*` mixed
with other select items, `ORDER BY`, `GROUP BY`, `HAVING`, `DISTINCT`,
aggregates, `LIMIT`, `LIKE`, unquoted SQL `NULL`, quoted identifiers, comments,
arithmetic, subqueries, updates, chained comparisons such as `age < 20 < 30`,
and a second statement after the semicolon.

Parentheses and `NOT` share a limit of 128 simultaneously nested levels.
For example, `NOT (a = 1)` uses two levels. The next `NOT` or opening parenthesis
past the limit produces a positioned `SqlParseException`, so deeply nested input
does not terminate the CLI. Separate sibling predicates do not accumulate levels.

## Team integration

- `Engine.execute` parses, then calls `Planner.plan`. Planning and
  `Executor.compile` still throw. A parse tree is not a query result.
- The planner should validate one table, an empty join list, null aliases, and
  a predicate-shaped `where`. It should bind names with the catalog and reject
  mismatched types. String ordering such as `name > 'Alice'` can parse and
  still fail during planning. Integer ordering is a planning concern too.
- Expand `Expression.Star` from the catalog. Keep the input column order.
- The CLI prints `SqlParseException` and `UnsupportedOperationException`, then
  reads the next line. `StorageException` still propagates. `quit` and `exit`
  leave the prompt.
- Operators on the unmerged `filter` branch evaluate bound expressions, not
  these AST nodes. The shared `ComparisonOp` names are `EQ`, `NE`, `LT`, `LE`,
  `GT`, and `GE`.

## Run and verify

```bash
mvn -q -Dtest=SqlParserTest,SqlTokenizerTest,ExpressionAstTest,CliTest test
mvn -q test
mvn -q package
```

Parser tests do not need CSV fixtures. They assert tree structure, including
precedence and parentheses, and they check error positions. Tokenizer tests
cover quoting, keyword boundaries, signed bounds, and whitespace. `CliTest`
sends a syntax error, then a statement that reaches the planner stub, then
another syntax error. The stub message means parsing finished.
