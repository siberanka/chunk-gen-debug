package com.siberanka.chunkgendebug.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldDirectoryResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesOrdinaryWorldNames() {
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(temporaryDirectory);
        assertEquals("world_nether-2", resolver.directoryName("world_nether-2"));
    }

    @Test
    void makesTraversalAndUnicodeNamesSafeAndCollisionResistant() {
        WorldDirectoryResolver resolver = new WorldDirectoryResolver(temporaryDirectory);
        String first = resolver.directoryName("../dünya");
        String second = resolver.directoryName("..\\dünya");

        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_.-]+"));
        assertTrue(resolver.resolve(new LogTarget(first, LogStream.LOAD))
                .startsWith(temporaryDirectory.toAbsolutePath().normalize()));
    }

    @Test
    void targetRejectsPathSeparators() {
        assertThrows(IllegalArgumentException.class, () -> new LogTarget("../world", LogStream.LOAD));
        assertThrows(IllegalArgumentException.class, () -> new LogTarget("a\\b", LogStream.LOAD));
    }
}
