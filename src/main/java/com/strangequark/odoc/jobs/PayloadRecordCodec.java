package com.strangequark.odoc.jobs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Bounded string-map codec for internal durable payloads; it deliberately has no arbitrary JSON/object support. */
public final class PayloadRecordCodec {
    private PayloadRecordCodec() {}

    public static byte[] encode(Map<String, ?> payload) {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, ?> entry : new TreeMap<>(payload).entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9]{0,63}")) {
                throw new IllegalArgumentException("Invalid durable payload key.");
            }
            String value = String.valueOf(entry.getValue());
            if (value.length() > 512) throw new IllegalArgumentException("Durable payload value is too long.");
            encoded.append(entry.getKey()).append(':')
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, Object> decode(byte[] encoded) {
        Map<String, Object> result = new LinkedHashMap<>();
        String source = new String(encoded, StandardCharsets.UTF_8);
        if (source.length() > 16_384) throw new IllegalArgumentException("Durable payload is too large.");
        for (String line : source.split("\\n", -1)) {
            if (line.isEmpty()) continue;
            int separator = line.indexOf(':');
            if (separator < 1 || !line.substring(0, separator).matches("[A-Za-z][A-Za-z0-9]{0,63}")
                    || result.put(line.substring(0, separator), new String(Base64.getUrlDecoder().decode(line.substring(separator + 1)), StandardCharsets.UTF_8)) != null) {
                throw new IllegalArgumentException("Invalid durable payload.");
            }
        }
        return result;
    }
}
