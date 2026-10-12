package minidb;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class EngineTest {
    @Test
    void executeReachesUnimplementedPlanner() {
        Engine engine = new Engine(Path.of("data"));
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> engine.execute("SELECT id FROM students"));
        assertTrue(ex.getMessage().contains("Planning is not implemented"));
    }
}
