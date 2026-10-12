## Layout

```
src/main/java/minidb/
  Main.java              program entry
  Engine.java            parse, then plan, then execute
  cli/Cli.java           command line
  sql/Parser.java        SQL text to a statement
  sql/ast/               statement and expression nodes
  catalog/               table and column metadata
  storage/               streaming CSV reads/writes and standalone storage demo
  types/                 integer and string values
  plan/Planner.java      statement to a logical plan
  exec/                  operators and result printing
data/                    put paired .csv and .schema table files here
```


## Run

```bash
mvn -q test
mvn -q package
java -jar target/minidb-0.1.0.jar data
```

The prompt accepts a line and exits on `quit`. A single-table `SELECT` parses; planning still throws.

## Parser (implemented)

The parser accepts one `SELECT` over one table, with an optional `WHERE`. It
returns an AST and does not read table files. Planning still throws.

See [the parser guide](docs/parser.md) for the grammar, AST, errors, and tests.

## Storage (implemented)

Storage works independently of the unfinished SQL pipeline. Requires Java 17+ and Maven.

```bash
mvn -q test
mvn -q package
java -cp target/minidb-0.1.0.jar minidb.storage.StorageDemo target/storage-demo
```

The demo writes `students.csv` and `students.schema`, then scans and prints their typed rows.
Use a different output directory on subsequent runs: writes intentionally reject existing tables.
The packaged JAR includes Apache Commons CSV and its runtime dependencies.

Each table has a CSV header and an explicit schema. For example, `students.schema`:

```text
id INTEGER
name STRING
```

And `students.csv`:

```csv
id,name
1,Alice
2,"Smith, Bob"
```

See [the storage guide](docs/storage.md) for the API, format rules, tests, and integration steps.

## Group

- [Group notes](https://docs.google.com/document/d/110spyKtpgsiMZG_rsPnJlrbhwEerZRns3I_K3ArTrzk/edit?usp=sharing)
- [Slack channel](https://app.slack.com/client/T0C1A7U2VJM/C0C1A7UQFFB)

Starting choices: Java, CSV files, integers and strings, then `SELECT` / `FROM` / `JOIN`. Add `WHERE`, `ORDER BY`, `GROUP BY`, more types, and subqueries only after that path works.
