package com.siberanka.chunkgendebug.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationTrackerTest {
    @Test
    void remainsBoundedUnderAdversarialUniqueKeys() {
        CorrelationTracker tracker = new CorrelationTracker(60_000, 128);
        UUID world = UUID.randomUUID();
        for (int index = 0; index < 10_000; index++) {
            tracker.record(new ChunkKey(world, index, -index), "TEST", Map.of("n", index));
        }
        assertTrue(tracker.size() <= 128);
        assertEquals(9_999, tracker.find(new ChunkKey(world, 9_999, -9_999)).details().get("n"));
    }

    @Test
    void expiresStaleEvidence() throws InterruptedException {
        CorrelationTracker tracker = new CorrelationTracker(1, 128);
        ChunkKey key = new ChunkKey(UUID.randomUUID(), 1, 2);
        tracker.record(key, "TEST", Map.of());
        Thread.sleep(10);
        assertNull(tracker.find(key));
    }
}
