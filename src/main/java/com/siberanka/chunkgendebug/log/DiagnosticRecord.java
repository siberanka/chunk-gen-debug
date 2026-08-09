package com.siberanka.chunkgendebug.log;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DiagnosticRecord(Map<String, Object> fields) {
    public DiagnosticRecord {
        fields = Map.copyOf(fields);
    }

    public static Builder builder(String kind, String eventId) {
        return new Builder(kind, eventId);
    }

    public static final class Builder {
        private final LinkedHashMap<String, Object> fields = new LinkedHashMap<>();

        private Builder(String kind, String eventId) {
            fields.put("schema", 1);
            fields.put("timestamp", Instant.now().toString());
            fields.put("kind", Objects.requireNonNull(kind, "kind"));
            fields.put("eventId", Objects.requireNonNull(eventId, "eventId"));
        }

        public Builder put(String key, Object value) {
            if (value != null) {
                fields.put(key, value);
            }
            return this;
        }

        public DiagnosticRecord build() {
            return new DiagnosticRecord(fields);
        }
    }
}
