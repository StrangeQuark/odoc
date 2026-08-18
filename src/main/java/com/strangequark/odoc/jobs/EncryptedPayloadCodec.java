package com.strangequark.odoc.jobs;

import com.strangequark.odoc.encryption.EncryptedRecord;
import java.util.Base64;

/** Compact, versioned persistence representation for encrypted job/outbox payloads. */
public final class EncryptedPayloadCodec {
    private EncryptedPayloadCodec() {}

    public static String encode(EncryptedRecord record) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return record.formatVersion() + "." + record.algorithm() + "." + record.keyVersion() + "."
                + encoder.encodeToString(record.nonce()) + "." + encoder.encodeToString(record.ciphertext());
    }

    public static EncryptedRecord decode(String value) {
        String[] fields = value == null ? new String[0] : value.split("\\.", -1);
        if (fields.length != 5) throw new IllegalArgumentException("Invalid encrypted payload envelope.");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            return new EncryptedRecord(
                    Integer.parseInt(fields[0]), fields[1], Integer.parseInt(fields[2]),
                    decoder.decode(fields[3]), decoder.decode(fields[4]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid encrypted payload envelope.", exception);
        }
    }
}
