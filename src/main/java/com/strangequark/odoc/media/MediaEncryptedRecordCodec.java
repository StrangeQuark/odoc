package com.strangequark.odoc.media;

import com.strangequark.odoc.encryption.EncryptedRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Compact binary envelope for one bounded managed-media record stored in object storage. */
final class MediaEncryptedRecordCodec {
    private static final int MAGIC = 0x4f444d31; // ODM1
    private static final int MAX_CIPHERTEXT_BYTES = 26 * 1024 * 1024;

    private MediaEncryptedRecordCodec() {}

    static byte[] encode(EncryptedRecord record) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(MAGIC);
            data.writeInt(record.formatVersion());
            data.writeInt(record.keyVersion());
            data.write(record.nonce());
            byte[] ciphertext = record.ciphertext();
            data.writeInt(ciphertext.length);
            data.write(ciphertext);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode media envelope.", exception);
        }
    }

    static EncryptedRecord decode(byte[] encoded) {
        if (encoded == null || encoded.length < 40 || encoded.length > MAX_CIPHERTEXT_BYTES + 64) {
            throw new IllegalArgumentException("invalid media envelope");
        }
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (data.readInt() != MAGIC) throw new IllegalArgumentException("invalid media envelope");
            int formatVersion = data.readInt();
            int keyVersion = data.readInt();
            byte[] nonce = data.readNBytes(12);
            int ciphertextLength = data.readInt();
            if (nonce.length != 12 || ciphertextLength < 16 || ciphertextLength > MAX_CIPHERTEXT_BYTES
                    || ciphertextLength != data.available()) {
                throw new IllegalArgumentException("invalid media envelope");
            }
            return new EncryptedRecord(formatVersion, EncryptedRecord.ALGORITHM, keyVersion, nonce,
                    data.readNBytes(ciphertextLength));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid media envelope", exception);
        }
    }
}
