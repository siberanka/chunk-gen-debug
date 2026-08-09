package com.siberanka.chunkgendebug.correlation;

import java.util.Map;

public record CausalMarker(long createdNanos, String type, Map<String, Object> details) {
    public CausalMarker {
        details = Map.copyOf(details);
    }
}
