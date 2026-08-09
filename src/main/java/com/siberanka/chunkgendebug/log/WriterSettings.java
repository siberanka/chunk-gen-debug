package com.siberanka.chunkgendebug.log;

import java.time.Duration;

public record WriterSettings(
        int queueCapacity,
        int maxOpenFiles,
        long maxFileBytes,
        int retainedFiles,
        Duration flushInterval,
        Duration shutdownTimeout) {

    public WriterSettings {
        if (queueCapacity < 1 || maxOpenFiles < 1 || maxFileBytes < 1024 || retainedFiles < 1) {
            throw new IllegalArgumentException("Writer limits must be positive");
        }
        if (flushInterval.isNegative() || flushInterval.isZero()
                || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("Writer durations must be positive");
        }
    }
}
