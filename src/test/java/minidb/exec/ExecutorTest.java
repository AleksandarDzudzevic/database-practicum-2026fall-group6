package minidb.exec;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import minidb.catalog.Table;
import minidb.plan.LogicalOperator;
import minidb.storage.*;

class ExecutorTest {
    @Test void compilationIsLazyAndEachTreeOwnsAnIndependentCursor() {
        int[] counts = new int[3];
        TableStore store = new TableStore() {
            public RowCursor scan(String name) {
                assertEquals("students", name);
                counts[0]++;
                return new RowCursor() {
                    public void open() { counts[1]++; }
                    public Row next() { return null; }
                    public void close() { counts[2]++; }
                };
            }
            public void write(Table table, Iterable<Row> rows) { fail("Read-only query"); }
        };
        Executor executor = new Executor(store);
        var plan = new LogicalOperator.Project(new LogicalOperator.Scan("students"), List.of(0));
        Operator first = executor.compile(plan);
        Operator second = executor.compile(plan);
        assertNotSame(first, second);
        assertArrayEquals(new int[]{0, 0, 0}, counts);
        first.open();
        second.open();
        assertNull(first.next());
        assertNull(second.next());
        first.close();
        second.close();
        assertArrayEquals(new int[]{2, 2, 2}, counts);
    }

    @Test void rejectsUnknownOrMissingPlans() {
        Executor executor = new Executor(null);
        assertThrows(IllegalArgumentException.class, () -> executor.compile(null));
        assertThrows(IllegalArgumentException.class, () -> executor.compile(new LogicalOperator() {}));
    }
}
