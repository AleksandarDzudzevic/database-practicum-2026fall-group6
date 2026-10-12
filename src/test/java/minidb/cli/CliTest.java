package minidb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import minidb.Engine;

class CliTest {
    @Test
    void excessiveNestingDoesNotTerminateTheSession() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        String prefix = "SELECT a FROM t WHERE ";
        String input = prefix + "(".repeat(10_000) + "a=1\n"
                + "SELECT id FROM students\n"
                + prefix + "NOT ".repeat(10_000) + "a=1\n"
                + "SELECT id FROM students\n"
                + prefix + "NOT (".repeat(5_000) + "a=1" + ")".repeat(5_000) + "\n"
                + "SELECT id FROM students\nquit\n";
        new Cli(new Engine(Path.of("unused-data"))).run(new StringReader(input), output);

        String text = buffer.toString(StandardCharsets.UTF_8);
        int previous = -1;
        for (int i = 0; i < 3; i++) {
            int error = text.indexOf("predicate nesting exceeds limit of 128", previous + 1);
            int planner = text.indexOf("Planning is not implemented", error + 1);
            assertTrue(error > previous, text);
            assertTrue(planner > error, text);
            previous = planner;
        }
        assertEquals(7, text.split("minidb>", -1).length - 1);
    }

    @Test
    void parseErrorDoesNotStopTheNextStatement() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        String input = """
                SELECT FROM t
                SELECT id FROM students
                SELECT a FROM t JOIN u
                quit
                """;
        new Cli(new Engine(Path.of("unused-data"))).run(new StringReader(input), output);

        String text = buffer.toString(StandardCharsets.UTF_8);
        int parseError = text.indexOf("expected column name");
        int plannerStub = text.indexOf("Planning is not implemented");
        int joinError = text.indexOf("JOIN is not supported");
        assertTrue(parseError >= 0, text);
        assertTrue(plannerStub > parseError, text);
        assertTrue(joinError > plannerStub, text);
        assertEquals(4, text.split("minidb>", -1).length - 1);
    }
}
