package com.strangequark.odoc.auth;

import com.strangequark.odoc.encryption.EncryptedRecord;
import java.util.Base64;

/** Compact persistence codec for the versioned record envelope; plaintext never enters this form. */
final class EncryptedRecordCodec {
    private EncryptedRecordCodec() {}

    static String encode(EncryptedRecord record) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return record.formatVersion() + "." + record.algorithm() + "." + record.keyVersion() + "."
                + encoder.encodeToString(record.nonce()) + "." + encoder.encodeToString(record.ciphertext());
    }

    static EncryptedRecord decode(String value) {
        String[] fields = value.split("\\.", -1);
        if (fields.length != 5) throw new IllegalArgumentException("Invalid encrypted record envelope.");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            return new EncryptedRecord(
                    Integer.parseInt(fields[0]), fields[1], Integer.parseInt(fields[2]),
                    decoder.decode(fields[3]), decoder.decode(fields[4]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid encrypted record envelope.", exception);
        }
    }
}
