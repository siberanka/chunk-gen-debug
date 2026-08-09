package com.siberanka.chunkgendebug.log;

import java.util.Objects;

public record LogTarget(String worldDirectory, LogStream stream) {
    public LogTarget {
        Objects.requireNonNull(worldDirectory, "worldDirectory");
        Objects.requireNonNull(stream, "stream");
        if (worldDirectory.isBlank() || worldDirectory.contains("/") || worldDirectory.contains("\\")) {
            throw new IllegalArgumentException("Unsafe world directory: " + worldDirectory);
        }
    }
}
