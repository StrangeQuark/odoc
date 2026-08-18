package com.strangequark.odoc.encryption;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM record encryption with canonical, complete associated data. */
public class ManagedRecordEncryption {
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final DataEncryptionKeyProvider keys;
    private final SecureRandom secureRandom;

    public ManagedRecordEncryption(DataEncryptionKeyProvider keys) {
        this(keys, new SecureRandom());
    }

    ManagedRecordEncryption(DataEncryptionKeyProvider keys, SecureRandom secureRandom) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public EncryptedRecord encrypt(EncryptionContext context, byte[] plaintext) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plaintext, "plaintext");
        DataEncryptionKey key = keys.activeKey(context.scope(), context.purpose());
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key.key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(associatedData(context, key.version()));
            return new EncryptedRecord(
                    EncryptedRecord.FORMAT_VERSION,
                    EncryptedRecord.ALGORITHM,
                    key.version(),
                    nonce,
                    cipher.doFinal(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new ManagedEncryptionException("Unable to encrypt the record.", exception);
        }
    }

    public byte[] decrypt(EncryptionContext context, EncryptedRecord record) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(record, "record");
        try {
            DataEncryptionKey key = keys.key(context.scope(), context.purpose(), record.keyVersion());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key.key(), new GCMParameterSpec(GCM_TAG_BITS, record.nonce()));
            cipher.updateAAD(associatedData(context, record.keyVersion()));
            return cipher.doFinal(record.ciphertext());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // Do not expose whether the key, context, nonce, tag, or ciphertext was wrong.
            throw new ManagedEncryptionException("Unable to decrypt the record.", exception);
        }
    }

    private static byte[] associatedData(EncryptionContext context, int keyVersion) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                DataOutputStream data = new DataOutputStream(output)) {
            data.writeInt(EncryptedRecord.FORMAT_VERSION);
            writeString(data, EncryptedRecord.ALGORITHM);
            writeString(data, context.scope().kind().name());
            writeUuid(data, context.scope().id());
            writeUuid(data, context.resourceId());
            writeString(data, context.purpose().name());
            data.writeInt(context.plaintextSchemaVersion());
            data.writeInt(keyVersion);
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode encryption associated data.", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static void writeUuid(DataOutputStream output, java.util.UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
