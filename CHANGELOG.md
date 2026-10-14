# Project updates

## 2026-10-11 — Single-table query execution

Current working-tree milestone; this entry does not imply a published release.

### Implemented

- Connected tokenizer/parser, planner, executor, and CSV storage through Engine.
- Added column binding, type validation, star expansion, and logical plans.
- Converted Scan/Filter/Project plans into fresh, lazily opened operator trees.
- Added typed comparisons, boolean short-circuit evaluation, streaming operators,
  and resource cleanup on EOF, early close, and runtime failures.
- Made the CLI recover from syntax and planning errors.
- Added shared synthetic students data with reproducible SQL examples.

### Validation

- Most recent full test run: 170 tests passed, no failures or skipped tests.
- Built the packaged JAR and manually checked filtering, quoted/empty strings,
  integer comparison, and continuing after a type error with the shared sample.

### Remaining limitations

- Single-table read-only SQL; no JOIN, aliases, sorting, aggregation, or SQL writes.
- Storage errors still propagate out of the CLI.
- Results accumulate in memory and print using Java Value representations.
- Parser and planner enforce expression-depth limits; no query optimizer.

See README.md for startup, data formats, supported SQL, and troubleshooting.
