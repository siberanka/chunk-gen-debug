package com.siberanka.chunkgendebug.log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public final class AsyncLogWriter implements AutoCloseable {
    private static final byte NEWLINE = (byte) '\n';

    private final ArrayBlockingQueue<Envelope> queue;
    private final ConcurrentHashMap<LogTarget, LongAdder> droppedByTarget = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final WriterSettings settings;
    private final WorldDirectoryResolver resolver;
    private final Consumer<String> errorReporter;
    private final Thread thread;

    public AsyncLogWriter(
            Path root,
            WriterSettings settings,
            Consumer<String> errorReporter) throws IOException {
        this.settings = settings;
        this.errorReporter = errorReporter;
        this.queue = new ArrayBlockingQueue<>(settings.queueCapacity());
        Files.createDirectories(root);
        this.resolver = new WorldDirectoryResolver(root.toRealPath());
        this.thread = Thread.ofPlatform()
                .name("chunk-gen-debug-writer")
                .daemon(true)
                .unstarted(this::writerLoop);
        this.thread.start();
    }

    public boolean submit(LogTarget target, DiagnosticRecord record) {
        if (!accepting.get()) {
            markDropped(target);
            return false;
        }
        if (queue.offer(new Envelope(target, record))) {
            accepted.incrementAndGet();
            return true;
        }
        markDropped(target);
        return false;
    }

    public WriterStats stats() {
        return new WriterStats(
                accepted.get(), written.get(), dropped.get(), writeFailures.get(), queue.size());
    }

    public String worldDirectory(String worldName) {
        return resolver.directoryName(worldName);
    }

    @Override
    public void close() {
        if (!accepting.getAndSet(false)) {
            return;
        }
        thread.interrupt();
        try {
            if (!stopped.await(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                errorReporter.accept("Writer did not drain within shutdown timeout; "
                        + queue.size() + " records remain");
                thread.interrupt();
                stopped.await(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            errorReporter.accept("Interrupted while waiting for diagnostic writer shutdown");
        }
    }

    private void markDropped(LogTarget target) {
        dropped.incrementAndGet();
        droppedByTarget.computeIfAbsent(target, ignored -> new LongAdder()).increment();
    }

    private void writerLoop() {
        LinkedHashMap<LogTarget, LogHandle> handles = new LinkedHashMap<>(16, 0.75f, true);
        long nextFlush = System.nanoTime() + settings.flushInterval().toNanos();
        long nextErrorReport = 0;
        try {
            while (accepting.get() || !queue.isEmpty()) {
                try {
                    Envelope envelope = queue.poll(250, TimeUnit.MILLISECONDS);
                    if (envelope != null) {
                        emitDroppedNotice(envelope.target(), handles);
                        write(envelope.target(), envelope.record(), handles);
                        written.incrementAndGet();
                    }
                    if (System.nanoTime() >= nextFlush) {
                        flushAll(handles);
                        nextFlush = System.nanoTime() + settings.flushInterval().toNanos();
                    }
                } catch (InterruptedException interrupted) {
                    // Interruption is the normal wake-up path during shutdown.
                } catch (IOException | RuntimeException failure) {
                    writeFailures.incrementAndGet();
                    long now = System.nanoTime();
                    if (now >= nextErrorReport) {
                        errorReporter.accept("Diagnostic writer error: " + failure.getMessage());
                        nextErrorReport = now + TimeUnit.SECONDS.toNanos(10);
                    }
                }
            }
            try {
                for (LogTarget target : new ArrayList<>(droppedByTarget.keySet())) {
                    emitDroppedNotice(target, handles);
                }
                flushAll(handles);
            } catch (IOException finalFlushFailure) {
                writeFailures.incrementAndGet();
                errorReporter.accept("Final diagnostic flush failed: " + finalFlushFailure.getMessage());
            }
        } finally {
            closeAll(handles);
            stopped.countDown();
        }
    }

    private void emitDroppedNotice(LogTarget target, LinkedHashMap<LogTarget, LogHandle> handles)
            throws IOException {
        LongAdder counter = droppedByTarget.get(target);
        if (counter == null) {
            return;
        }
        long count = counter.sumThenReset();
        if (count == 0) {
            return;
        }
        DiagnosticRecord overflow = DiagnosticRecord.builder("overflow", "overflow-" + Instant.now().toEpochMilli())
                .put("droppedRecords", count)
                .put("reason", "bounded queue capacity reached; event threads were not blocked")
                .build();
        write(target, overflow, handles);
    }

    private void write(
            LogTarget target,
            DiagnosticRecord record,
            LinkedHashMap<LogTarget, LogHandle> handles) throws IOException {
        byte[] payload = JsonEncoder.encode(record.fields()).getBytes(StandardCharsets.UTF_8);
        LogHandle handle = handles.get(target);
        if (handle == null) {
            handle = open(target);
            handles.put(target, handle);
            evictHandles(handles);
        }
        if (handle.size + payload.length + 1 > settings.maxFileBytes()) {
            handle.close();
            rotate(handle.path);
            handle = open(target);
            handles.put(target, handle);
        }
        handle.output.write(payload);
        handle.output.write(NEWLINE);
        handle.size += payload.length + 1L;
    }

    private LogHandle open(LogTarget target) throws IOException {
        Path path = resolver.resolve(target);
        resolver.verifySafeParent(path);
        long size = Files.exists(path) ? Files.size(path) : 0;
        OutputStream stream = new BufferedOutputStream(Files.newOutputStream(
                path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND), 64 * 1024);
        return new LogHandle(path, stream, size);
    }

    private void rotate(Path path) throws IOException {
        for (int index = settings.retainedFiles(); index >= 1; index--) {
            Path source = index == 1 ? path : Path.of(path + "." + (index - 1));
            Path destination = Path.of(path + "." + index);
            if (Files.exists(source)) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void evictHandles(LinkedHashMap<LogTarget, LogHandle> handles) throws IOException {
        while (handles.size() > settings.maxOpenFiles()) {
            Map.Entry<LogTarget, LogHandle> eldest = handles.entrySet().iterator().next();
            eldest.getValue().close();
            handles.remove(eldest.getKey());
        }
    }

    private static void flushAll(LinkedHashMap<LogTarget, LogHandle> handles) throws IOException {
        IOException aggregate = null;
        for (LogHandle handle : handles.values()) {
            try {
                handle.output.flush();
            } catch (IOException failure) {
                if (aggregate == null) {
                    aggregate = failure;
                } else {
                    aggregate.addSuppressed(failure);
                }
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    private static void closeAll(LinkedHashMap<LogTarget, LogHandle> handles) {
        for (LogHandle handle : handles.values()) {
            try {
                handle.close();
            } catch (IOException ignored) {
                // Shutdown is best effort; active failures were already surfaced.
            }
        }
        handles.clear();
    }

    private record Envelope(LogTarget target, DiagnosticRecord record) {
    }

    private static final class LogHandle implements AutoCloseable {
        private final Path path;
        private final OutputStream output;
        private long size;

        private LogHandle(Path path, OutputStream output, long size) {
            this.path = path;
            this.output = output;
            this.size = size;
        }

        @Override
        public void close() throws IOException {
            output.flush();
            output.close();
        }
    }
}
