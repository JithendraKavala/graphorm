package com.graphorm.core.pagination;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Opaque Base64 URL-safe codec for keyset pagination cursors.
 * Serializes and deserializes composite keyset column values (e.g. [created_at, id])
 * to and from URL-safe Base64 strings without trailing padding.
 */
public final class CursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorCodec() {}

    /**
     * Encodes a list of column values into an opaque Base64 cursor string.
     *
     * @param values the values forming the keyset tuple
     * @return opaque Base64 cursor string
     */
    public static String encode(List<Object> values) {
        Objects.requireNonNull(values, "Values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot encode empty values list into cursor");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            appendValue(sb, values.get(i));
        }
        sb.append("]");

        return ENCODER.encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes one or more column values into an opaque Base64 cursor string.
     *
     * @param values the values forming the keyset tuple
     * @return opaque Base64 cursor string
     */
    public static String encode(Object... values) {
        Objects.requireNonNull(values, "Values must not be null");
        return encode(Arrays.asList(values));
    }

    /**
     * Decodes an opaque Base64 cursor string back into its original column values.
     *
     * @param cursor the opaque cursor string
     * @return list of decoded values
     * @throws IllegalArgumentException if the cursor is null, blank, or malformed
     */
    public static List<Object> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be null or blank");
        }

        String json;
        try {
            byte[] bytes = DECODER.decode(cursor.trim());
            json = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            // Try standard base64 decoder as fallback
            try {
                byte[] bytes = Base64.getDecoder().decode(cursor.trim());
                json = new String(bytes, StandardCharsets.UTF_8).trim();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid Base64 cursor: " + cursor, ex);
            }
        }

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Malformed cursor payload: " + json);
        }

        return parseJsonArray(json.substring(1, json.length() - 1).trim());
    }

    /**
     * Decodes an opaque Base64 cursor and returns the single value.
     *
     * @param cursor the opaque cursor string
     * @return the single decoded value
     * @throws IllegalArgumentException if cursor contains more or less than one value
     */
    public static Object decodeSingle(String cursor) {
        List<Object> values = decode(cursor);
        if (values.size() != 1) {
            throw new IllegalArgumentException("Expected single value in cursor but found " + values.size());
        }
        return values.getFirst();
    }

    private static void appendValue(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Number || val instanceof Boolean) {
            sb.append(val);
        } else {
            // String, Instant, UUID, Date, etc. - quote and escape
            sb.append("\"");
            escapeString(sb, val.toString());
            sb.append("\"");
        }
    }

    private static void escapeString(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
    }

    private static List<Object> parseJsonArray(String content) {
        List<Object> result = new ArrayList<>();
        if (content.isEmpty()) {
            return result;
        }

        int len = content.length();
        int i = 0;
        while (i < len) {
            while (i < len && (Character.isWhitespace(content.charAt(i)) || content.charAt(i) == ',')) {
                i++;
            }
            if (i >= len) break;

            char c = content.charAt(i);
            if (c == '"') {
                // Parse string
                i++;
                StringBuilder str = new StringBuilder();
                while (i < len) {
                    char sc = content.charAt(i);
                    if (sc == '\\' && i + 1 < len) {
                        i++;
                        char esc = content.charAt(i);
                        if (esc == 'n') str.append('\n');
                        else if (esc == 'r') str.append('\r');
                        else if (esc == 't') str.append('\t');
                        else str.append(esc);
                    } else if (sc == '"') {
                        i++;
                        break;
                    } else {
                        str.append(sc);
                    }
                    i++;
                }
                result.add(str.toString());
            } else {
                // Parse literal (number, boolean, null)
                int start = i;
                while (i < len && content.charAt(i) != ',' && !Character.isWhitespace(content.charAt(i))) {
                    i++;
                }
                String token = content.substring(start, i);
                if ("null".equals(token)) {
                    result.add(null);
                } else if ("true".equalsIgnoreCase(token)) {
                    result.add(Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(token)) {
                    result.add(Boolean.FALSE);
                } else {
                    // Try parsing as number
                    try {
                        if (token.contains(".")) {
                            result.add(Double.parseDouble(token));
                        } else {
                            result.add(Long.parseLong(token));
                        }
                    } catch (NumberFormatException e) {
                        result.add(token);
                    }
                }
            }
        }

        return result;
    }
}
