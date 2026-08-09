package com.siberanka.chunkgendebug.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonEncoderTest {
    @Test
    void encodesNestedValuesAndEscapesLogInjectionCharacters() {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("message", "line1\nline2\t\u001b[31m\u202e");
        value.put("values", List.of(1, true, "x"));

        String encoded = JsonEncoder.encode(value);

        assertEquals("{\"message\":\"line1\\nline2\\t\\u001b[31m\\u202e\",\"values\":[1,true,\"x\"]}", encoded);
        assertFalse(encoded.contains("\n"));
        assertFalse(encoded.contains("\u001b"));
    }

    @Test
    void recordBuilderProducesStableRequiredFields() {
        DiagnosticRecord record = DiagnosticRecord.builder("chunk_load", "event-1")
                .put("chunk", Map.of("x", 2, "z", -4))
                .build();

        assertEquals(1, record.fields().get("schema"));
        assertEquals("chunk_load", record.fields().get("kind"));
        assertEquals("event-1", record.fields().get("eventId"));
        assertTrue(record.fields().containsKey("timestamp"));
    }
}
