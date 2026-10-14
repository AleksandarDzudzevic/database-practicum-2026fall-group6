package minidb;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import minidb.plan.PlanningException;
import minidb.catalog.Column;
import minidb.catalog.Table;
import minidb.storage.DirectoryTableStore;
import minidb.storage.Row;
import minidb.storage.StorageException;
import minidb.types.DataType;
import minidb.types.Value;

class EngineTest {
    @TempDir Path data;
    private Engine engine;

    @BeforeEach void prepare() throws Exception {
        Files.writeString(data.resolve("students.schema"), "id INTEGER\nname STRING\nage INTEGER\n");
        Files.writeString(data.resolve("students.csv"), "id,name,age\n1,Alice,20\n2,Bob,17\n3,O'Brien,25\n1,Alice,20\n");
        engine = new Engine(data);
    }

    private List<List<Object>> query(String sql) {
        return engine.execute(sql).rows().stream()
                .map(row -> row.values().stream().map(value -> value.raw()).toList()).toList();
    }

    @Test void executesStarAndPreservesDuplicateRows() {
        assertEquals(List.of(List.of(1, "Alice", 20), List.of(2, "Bob", 17),
                List.of(3, "O'Brien", 25), List.of(1, "Alice", 20)), query("SELECT * FROM students"));
    }

    @Test void queriesTableWrittenThroughStorageWithBothTypes() {
        Table table = new Table("imported", List.of(
                new Column("code", DataType.STRING),
                new Column("amount", DataType.INTEGER),
                new Column("label", DataType.STRING)));
        new DirectoryTableStore(data).write(table, List.of(
                new Row(List.of(new Value(DataType.STRING, "00123"),
                        new Value(DataType.INTEGER, Integer.MIN_VALUE),
                        new Value(DataType.STRING, "Smith, Bob"))),
                new Row(List.of(new Value(DataType.STRING, "00124"),
                        new Value(DataType.INTEGER, Integer.MAX_VALUE),
                        new Value(DataType.STRING, "O'Brien")))));

        // A fresh engine discovers the exported schema and data without an in-memory table.
        engine = new Engine(data);
        assertEquals(List.of(List.of("00123", "Smith, Bob")),
                query("SELECT code, label FROM imported WHERE amount < 0"));
        assertEquals(List.of(List.of("00124", Integer.MAX_VALUE)),
                query("SELECT code, amount FROM imported WHERE label = 'O''Brien'"));
    }

    @Test void filtersBeforeProjectionAndPreservesRepeatedColumns() {
        assertEquals(List.of(List.of("Alice", 1, "Alice"), List.of("O'Brien", 3, "O'Brien"),
                List.of("Alice", 1, "Alice")),
                query("SELECT name, students.id, name FROM students WHERE age >= 18"));
    }

    @Test void executesNestedPredicatesAndEscapedStrings() {
        assertEquals(List.of(List.of(3)), query("SELECT id FROM students WHERE "
                + "NOT (age < 18 OR name = 'Alice') AND name = 'O''Brien' AND age > id"));
    }

    @Test void supportsEmptyResultsAndRepeatedExecution() throws Exception {
        assertTrue(query("SELECT id FROM students WHERE age > 100").isEmpty());
        assertEquals(4, query("SELECT id FROM students").size());
        assertEquals(4, query("SELECT id FROM students").size());
        Files.writeString(data.resolve("students.csv"), "id,name,age\n");
        assertTrue(query("SELECT * FROM students").isEmpty());
    }

    @Test void rejectsInvalidTypesBeforeReadingMalformedData() throws Exception {
        Files.writeString(data.resolve("students.csv"), "id,name,age\nbad,Alice,20\n");
        assertThrows(PlanningException.class, () -> engine.execute("SELECT id FROM students WHERE age = '18'"));
        assertThrows(StorageException.class, () -> engine.execute("SELECT id FROM students"));
    }
}
