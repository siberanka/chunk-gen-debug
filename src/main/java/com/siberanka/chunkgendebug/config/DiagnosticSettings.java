package com.siberanka.chunkgendebug.config;

public record DiagnosticSettings(
        boolean captureStackTraces,
        int maxStackFrames,
        int maxEventListeners,
        int maxEntityDetails,
        boolean includePlayerIdentities,
        long correlationTtlMillis,
        int maxCorrelationEntries) {

    public DiagnosticSettings {
        if (maxStackFrames < 1 || maxEventListeners < 1 || maxEntityDetails < 0
                || correlationTtlMillis < 1 || maxCorrelationEntries < 1) {
            throw new IllegalArgumentException("Diagnostic limits are invalid");
        }
    }
}
