package com.strangequark.odoc.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ManagedRecordEncryptionTest {
    private static final SecurityScope INSTANCE_SCOPE =
            new SecurityScope(SecurityScopeKind.INSTANCE, UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UUID RESOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DataEncryptionKey KEY = new DataEncryptionKey(
            7,
            new SecretKeySpec(new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
            }, "AES"));

    private final ManagedRecordEncryption encryption = new ManagedRecordEncryption(new FixedKeys(KEY));

    @Test
    void roundTripsAndNeverStoresPlaintextInsideTheEnvelope() {
        byte[] plaintext = "classified-email@example.test".getBytes(StandardCharsets.UTF_8);
        EncryptedRecord record = encryption.encrypt(context(), plaintext);

        assertThat(record.keyVersion()).isEqualTo(7);
        assertThat(record.ciphertext()).isNotEqualTo(plaintext);
        assertThat(encryption.decrypt(context(), record)).isEqualTo(plaintext);
    }

    @Test
    void rejectsTamperingAndAnyAssociatedDataMismatch() {
        EncryptedRecord record = encryption.encrypt(context(), "sensitive".getBytes(StandardCharsets.UTF_8));
        byte[] tamperedCiphertext = record.ciphertext();
        tamperedCiphertext[tamperedCiphertext.length - 1] ^= 1;
        EncryptedRecord tampered = new EncryptedRecord(
                record.formatVersion(), record.algorithm(), record.keyVersion(), record.nonce(), tamperedCiphertext);

        assertThatThrownBy(() -> encryption.decrypt(context(), tampered)).isInstanceOf(ManagedEncryptionException.class);
        assertThatThrownBy(() -> encryption.decrypt(
                        new EncryptionContext(INSTANCE_SCOPE, UUID.randomUUID(), EncryptionPurpose.IDENTITY, 1), record))
                .isInstanceOf(ManagedEncryptionException.class);
        assertThatThrownBy(() -> encryption.decrypt(
                        new EncryptionContext(INSTANCE_SCOPE, RESOURCE_ID, EncryptionPurpose.SESSION, 1), record))
                .isInstanceOf(ManagedEncryptionException.class);
    }

    @Test
    void returnsDefensiveEnvelopeArrays() {
        EncryptedRecord record = encryption.encrypt(context(), "safe".getBytes(StandardCharsets.UTF_8));
        byte[] nonce = record.nonce();
        nonce[0] ^= 1;

        assertThat(encryption.decrypt(context(), record)).isEqualTo("safe".getBytes(StandardCharsets.UTF_8));
    }

    private static EncryptionContext context() {
        return new EncryptionContext(INSTANCE_SCOPE, RESOURCE_ID, EncryptionPurpose.IDENTITY, 1);
    }

    private static final class FixedKeys implements DataEncryptionKeyProvider {
        private final Map<Integer, DataEncryptionKey> keys;

        private FixedKeys(DataEncryptionKey key) {
            this.keys = Map.of(key.version(), key);
        }

        @Override
        public DataEncryptionKey activeKey(SecurityScope scope, EncryptionPurpose purpose) {
            return KEY;
        }

        @Override
        public DataEncryptionKey key(SecurityScope scope, EncryptionPurpose purpose, int version) {
            DataEncryptionKey key = keys.get(version);
            if (key == null) {
                throw new IllegalArgumentException("Unknown key version");
            }
            return key;
        }
    }
}
