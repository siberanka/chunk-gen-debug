package com.siberanka.chunkgendebug.log;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

public final class JsonEncoder {
    private JsonEncoder() {
    }

    public static String encode(Object value) {
        StringBuilder output = new StringBuilder(512);
        append(output, value);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(output, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Enum<?> enumValue) {
            appendString(output, enumValue.name());
        } else if (value instanceof Map<?, ?> map) {
            appendMap(output, map);
        } else if (value instanceof Collection<?> collection) {
            appendCollection(output, collection);
        } else if (value.getClass().isArray()) {
            appendArray(output, value);
        } else {
            appendString(output, value.toString());
        }
    }

    private static void appendMap(StringBuilder output, Map<?, ?> map) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                output.append(',');
            }
            first = false;
            appendString(output, String.valueOf(entry.getKey()));
            output.append(':');
            append(output, entry.getValue());
        }
        output.append('}');
    }

    private static void appendCollection(StringBuilder output, Collection<?> collection) {
        output.append('[');
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                output.append(',');
            }
            first = false;
            append(output, item);
        }
        output.append(']');
    }

    private static void appendArray(StringBuilder output, Object array) {
        output.append('[');
        for (int index = 0; index < Array.getLength(array); index++) {
            if (index > 0) {
                output.append(',');
            }
            append(output, Array.get(array, index));
        }
        output.append(']');
    }

    private static void appendString(StringBuilder output, String text) {
        output.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20 || character == 0x7f
                            || (character >= 0x202a && character <= 0x202e)
                            || (character >= 0x2066 && character <= 0x2069)) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }
}
