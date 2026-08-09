package com.siberanka.chunkgendebug.config;

import com.siberanka.chunkgendebug.log.WriterSettings;
import java.time.Duration;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(WriterSettings writer, DiagnosticSettings diagnostics) {
    private static final int SCHEMA_VERSION = 1;

    public static PluginSettings load(FileConfiguration config, Consumer<String> warning) {
        int schema = config.getInt("schema-version", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported config schema " + schema
                    + "; this build supports schema " + SCHEMA_VERSION);
        }

        int queueCapacity = integer(config, "logging.queue-capacity", 16_384, 256, 1_000_000, warning);
        int maxOpenFiles = integer(config, "logging.max-open-files", 48, 3, 1_024, warning);
        int maxFileSizeMiB = integer(config, "logging.max-file-size-mib", 64, 1, 16_384, warning);
        int retainedFiles = integer(config, "logging.retained-files", 5, 1, 100, warning);
        int flushMillis = integer(config, "logging.flush-interval-ms", 1_000, 50, 60_000, warning);
        int shutdownMillis = integer(config, "logging.shutdown-timeout-ms", 5_000, 250, 60_000, warning);

        int maxStackFrames = integer(config, "diagnostics.max-stack-frames", 80, 8, 512, warning);
        int maxListeners = integer(config, "diagnostics.max-event-listeners", 128, 1, 4_096, warning);
        int maxEntityDetails = integer(config, "diagnostics.max-entity-details", 64, 0, 4_096, warning);
        int correlationTtlMillis = integer(
                config, "diagnostics.correlation-ttl-ms", 15_000, 100, 300_000, warning);
        int maxCorrelationEntries = integer(
                config, "diagnostics.max-correlation-entries", 32_768, 128, 1_000_000, warning);

        WriterSettings writer = new WriterSettings(
                queueCapacity,
                maxOpenFiles,
                Math.multiplyExact((long) maxFileSizeMiB, 1024L * 1024L),
                retainedFiles,
                Duration.ofMillis(flushMillis),
                Duration.ofMillis(shutdownMillis));
        DiagnosticSettings diagnostics = new DiagnosticSettings(
                config.getBoolean("diagnostics.capture-stack-traces", true),
                maxStackFrames,
                maxListeners,
                maxEntityDetails,
                config.getBoolean("diagnostics.include-player-identities", false),
                correlationTtlMillis,
                maxCorrelationEntries);
        return new PluginSettings(writer, diagnostics);
    }

    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int integer(
            FileConfiguration config,
            String path,
            int defaultValue,
            int minimum,
            int maximum,
            Consumer<String> warning) {
        int configured = config.getInt(path, defaultValue);
        int safe = clamp(configured, minimum, maximum);
        if (safe != configured) {
            warning.accept(path + "=" + configured + " is outside [" + minimum + ", " + maximum
                    + "]; using " + safe);
        }
        return safe;
    }
}
