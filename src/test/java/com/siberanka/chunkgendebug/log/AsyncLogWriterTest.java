package com.siberanka.chunkgendebug.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncLogWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void concurrentlyAcceptsOrExplicitlyDropsEveryRecordWithoutBlockingProducers() throws Exception {
        WriterSettings settings = new WriterSettings(
                256, 3, 8 * 1024 * 1024, 2, Duration.ofMillis(20), Duration.ofSeconds(10));
        List<String> errors = new ArrayList<>();
        AsyncLogWriter writer = new AsyncLogWriter(temporaryDirectory, settings, errors::add);
        LogTarget target = new LogTarget("world", LogStream.LOAD);
        int producers = 8;
        int recordsPerProducer = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        long started = System.nanoTime();
        for (int producer = 0; producer < producers; producer++) {
            int producerId = producer;
            executor.submit(() -> {
                for (int index = 0; index < recordsPerProducer; index++) {
                    writer.submit(target, DiagnosticRecord.builder("test", producerId + ":" + index)
                            .put("producer", producerId)
                            .put("sequence", index)
                            .build());
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        long producerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        writer.close();

        WriterStats stats = writer.stats();
        assertEquals((long) producers * recordsPerProducer, stats.accepted() + stats.dropped());
        assertEquals(stats.accepted(), stats.written());
        assertEquals(0, stats.writeFailures());
        assertEquals(0, stats.queued());
        assertTrue(producerMillis < 10_000, "bounded offers should finish promptly");
        assertTrue(Files.size(temporaryDirectory.resolve("world/chunk-load.log")) > 0);
        assertTrue(errors.isEmpty(), () -> "writer errors: " + errors);
    }

    @Test
    void rotatesAndRetainsConfiguredNumberOfFiles() throws Exception {
        WriterSettings settings = new WriterSettings(
                1_024, 3, 1_024, 2, Duration.ofMillis(10), Duration.ofSeconds(10));
        AsyncLogWriter writer = new AsyncLogWriter(temporaryDirectory, settings, ignored -> { });
        LogTarget target = new LogTarget("world", LogStream.GENERATION);
        String payload = "x".repeat(400);
        for (int index = 0; index < 30; index++) {
            writer.submit(target, DiagnosticRecord.builder("rotation", String.valueOf(index))
                    .put("payload", payload)
                    .build());
        }
        writer.close();

        assertTrue(Files.exists(temporaryDirectory.resolve("world/chunk-gen.log")));
        assertTrue(Files.exists(temporaryDirectory.resolve("world/chunk-gen.log.1")));
        assertTrue(Files.exists(temporaryDirectory.resolve("world/chunk-gen.log.2")));
        assertFalse(Files.exists(temporaryDirectory.resolve("world/chunk-gen.log.3")));
    }
}
