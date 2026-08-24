package com.strangequark.odoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime feasibility vector for the future managed-encryption adapter.
 * It deliberately lives in test scope: production persistence remains plaintext
 * until P1-115 supplies the reviewed KMS/envelope implementation.
 */
class ManagedEnvelopeVectorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void decryptsTheSharedAesGcmEnvelopeVectorAndRejectsTampering() throws Exception {
        JsonNode vector = readVector();
        byte[] key = decode(vector, "keyMaterial");
        byte[] nonce = decode(vector, "nonce");
        byte[] ciphertextAndTag = decode(vector, "ciphertextAndTag");
        byte[] associatedData = vector.required("associatedData").asText().getBytes(StandardCharsets.UTF_8);

        assertThat(decrypt(key, nonce, associatedData, ciphertextAndTag))
                .isEqualTo(vector.required("plaintext").asText());

        byte[] alteredAssociatedData = associatedData.clone();
        alteredAssociatedData[0] ^= 1;
        assertThatThrownBy(() -> decrypt(key, nonce, alteredAssociatedData, ciphertextAndTag))
                .isInstanceOf(Exception.class);

        byte[] alteredCiphertext = ciphertextAndTag.clone();
        alteredCiphertext[0] ^= 1;
        assertThatThrownBy(() -> decrypt(key, nonce, associatedData, alteredCiphertext))
                .isInstanceOf(Exception.class);
    }

    @Test
    void unwrapsTheSharedAesKeyWrapVectorAndRejectsTampering() throws Exception {
        JsonNode vector = readVector("/crypto/key-wrap-v1.json");
        byte[] kek = decode(vector, "keyEncryptionKey");
        byte[] wrappedDek = decode(vector, "wrappedDek");

        assertThat(unwrap(kek, wrappedDek)).isEqualTo(decode(vector, "plaintextDek"));

        byte[] alteredWrappedDek = wrappedDek.clone();
        alteredWrappedDek[0] ^= 1;
        assertThatThrownBy(() -> unwrap(kek, alteredWrappedDek)).isInstanceOf(Exception.class);
    }

    private static JsonNode readVector() throws Exception {
        return readVector("/crypto/envelope-v1.json");
    }

    private static JsonNode readVector(String path) throws Exception {
        try (InputStream input = ManagedEnvelopeVectorTest.class
                .getResourceAsStream(path)) {
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static byte[] decode(JsonNode vector, String field) {
        return Base64.getUrlDecoder().decode(vector.required(field).asText());
    }

    private static String decrypt(byte[] key, byte[] nonce, byte[] associatedData, byte[] ciphertextAndTag)
            throws Exception {
        return new String(decryptBytes(key, nonce, associatedData, ciphertextAndTag), StandardCharsets.UTF_8);
    }

    private static byte[] decryptBytes(byte[] key, byte[] nonce, byte[] associatedData, byte[] ciphertextAndTag)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(associatedData);
        return cipher.doFinal(ciphertextAndTag);
    }

    private static byte[] unwrap(byte[] kek, byte[] wrappedDek) throws Exception {
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"));
        return cipher.doFinal(wrappedDek);
    }

}
