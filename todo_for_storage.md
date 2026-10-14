# Storage follow-up

Completed:

- [x] Handle each supported data type explicitly. `CsvRowCursor` and
  `DirectoryTableStore.validateRow()` now use exhaustive `switch` expressions for
  `INTEGER` and `STRING`, without default branches. Adding another `DataType`
  requires updating both switches before the project compiles. Parsing, value
  validation, and file formats retain their existing behavior.
- [x] Check integration with the new query pipeline: write a table through storage,
  then query its persisted data through a fresh `Engine`, including CSV quoting,
  string identifiers with leading zeros, and integer boundaries.

Questions for the first demo, in priority order:

1. Should the CLI recover from `StorageException` as it already does for syntax
   and planning errors? A nonexistent table, mismatched header, or bad integer
   currently terminates the session. Add CLI tests for continuing with a valid
   query after each failure.
2. Should results display plain values and column headers? Current output exposes
   `Value[type=..., raw=...]`, which makes a live demo harder to read. Coordinate
   this with the teammate who owns `QueryResult` and CLI output.
3. Does the demo need large result sets, or only large input scans? Storage and
   scan/filter/project stream, but `Engine` accumulates all selected rows in
   `QueryResult`. Streaming output is a separate change if large results are part
   of the demo; the existing constrained-heap storage test already verifies large
   input reads and writes.
