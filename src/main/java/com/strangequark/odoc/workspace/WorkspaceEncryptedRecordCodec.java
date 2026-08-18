package com.strangequark.odoc.workspace;

import com.strangequark.odoc.encryption.EncryptedRecord;
import java.util.Base64;

/** Compact persistence codec for workspace metadata envelopes. */
final class WorkspaceEncryptedRecordCodec {
    private WorkspaceEncryptedRecordCodec() {}

    static String encode(EncryptedRecord record) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return record.formatVersion() + "." + record.algorithm() + "." + record.keyVersion() + "."
                + encoder.encodeToString(record.nonce()) + "." + encoder.encodeToString(record.ciphertext());
    }

    static EncryptedRecord decode(String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split("\\.", -1);
        if (parts.length != 5) throw new IllegalArgumentException("invalid encrypted record envelope");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            return new EncryptedRecord(
                    Integer.parseInt(parts[0]), parts[1], Integer.parseInt(parts[2]),
                    decoder.decode(parts[3]), decoder.decode(parts[4]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid encrypted record envelope", exception);
        }
    }
}
