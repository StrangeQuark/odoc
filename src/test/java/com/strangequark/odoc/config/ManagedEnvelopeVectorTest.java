package com.strangequark.odoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
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

    @Test
    void verifiesTheSharedSignedCapabilityManifestVectorAndRejectsTampering() throws Exception {
        JsonNode vector = readVector("/crypto/zero-knowledge-capability-manifest-v1.json");
        String canonicalPayload = vector.required("canonicalPayload").asText();
        assertThat(vector.required("manifest").required("signature").asText())
                .isEqualTo(vector.required("signatureBase64Url").asText());
        assertThat(verifyEd25519(vector, canonicalPayload.getBytes(StandardCharsets.UTF_8))).isTrue();

        byte[] tampered = canonicalPayload.getBytes(StandardCharsets.UTF_8);
        tampered[0] ^= 1;
        assertThat(verifyEd25519(vector, tampered)).isFalse();
    }

    @Test
    void verifiesChunkedObjectManifestAndRejectsOrderLengthAndScopeTampering() throws Exception {
        JsonNode vector = readVector("/crypto/chunked-object-v1.json");
        byte[] key = decode(vector, "keyMaterial");
        assertThat(new String(decryptBytes(key, decode(vector, "manifestNonce"),
                vector.required("manifestAad").asText().getBytes(StandardCharsets.UTF_8),
                decode(vector, "manifestCiphertextAndTag")), StandardCharsets.UTF_8))
                .isEqualTo(vector.required("manifestPlaintext").asText());

        List<JsonNode> chunks = chunkNodes(vector);
        assertThat(new String(decryptChunks(vector, chunks), StandardCharsets.UTF_8))
                .isEqualTo(vector.required("plaintext").asText());
        assertThat(new String(decryptChunks(vector, chunks).clone(), StandardCharsets.UTF_8)
                .substring(9, 22)).isEqualTo("9abcdefghijkl");

        assertThatThrownBy(() -> decryptChunks(vector, chunks.subList(0, chunks.size() - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decryptChunk(vector, chunks.get(1), 0))
                .isInstanceOf(Exception.class);

        ObjectNode tamperedChunk = (ObjectNode) chunks.get(0).deepCopy();
        String encoded = tamperedChunk.required("ciphertextAndTag").asText();
        tamperedChunk.put("ciphertextAndTag", encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A"));
        assertThatThrownBy(() -> decryptChunk(vector, tamperedChunk, 0)).isInstanceOf(Exception.class);

        ObjectNode crossScope = (ObjectNode) vector.deepCopy();
        crossScope.put("securityScope", "workspace:wrong-scope");
        assertThatThrownBy(() -> decryptChunk(crossScope, chunks.get(0), 0)).isInstanceOf(Exception.class);
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

    private static boolean verifyEd25519(JsonNode vector, byte[] payload) throws Exception {
        byte[] publicKey = Base64.getDecoder().decode(vector.required("publicKeySpkiBase64").asText());
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(publicKey)));
        verifier.update(payload);
        return verifier.verify(decode(vector, "signatureBase64Url"));
    }

    private static List<JsonNode> chunkNodes(JsonNode vector) {
        return java.util.stream.StreamSupport.stream(vector.required("chunks").spliterator(), false).toList();
    }

    private static byte[] decryptChunks(JsonNode vector, List<JsonNode> chunks) throws Exception {
        if (chunks.size() != vector.required("chunkCount").asInt()) {
            throw new IllegalArgumentException("Chunk count does not match authenticated manifest.");
        }
        ByteArrayOutputStream plaintext = new ByteArrayOutputStream();
        for (int index = 0; index < chunks.size(); index++) {
            if (chunks.get(index).required("index").asInt() != index) {
                throw new IllegalArgumentException("Chunk sequence is not ordered.");
            }
            plaintext.writeBytes(decryptChunk(vector, chunks.get(index), index));
        }
        if (plaintext.size() != vector.required("plaintextLength").asInt()) {
            throw new IllegalArgumentException("Chunk plaintext length does not match manifest.");
        }
        return plaintext.toByteArray();
    }

    private static byte[] decryptChunk(JsonNode vector, JsonNode chunk, int expectedIndex) throws Exception {
        byte[] nonce = new byte[12];
        System.arraycopy(decode(vector, "noncePrefix"), 0, nonce, 0, 8);
        nonce[8] = (byte) (expectedIndex >>> 24);
        nonce[9] = (byte) (expectedIndex >>> 16);
        nonce[10] = (byte) (expectedIndex >>> 8);
        nonce[11] = (byte) expectedIndex;
        String associatedData = "odoc-chunk-v1|object=" + vector.required("objectId").asText()
                + "|index=" + expectedIndex + "|count=" + vector.required("chunkCount").asInt()
                + "|length=" + vector.required("plaintextLength").asInt()
                + "|scope=" + vector.required("securityScope").asText()
                + "|purpose=" + vector.required("purpose").asText()
                + "|version=" + vector.required("version").asInt()
                + "|key=" + vector.required("keyVersion").asInt();
        return decryptBytes(decode(vector, "keyMaterial"), nonce,
                associatedData.getBytes(StandardCharsets.UTF_8), decode(chunk, "ciphertextAndTag"));
    }
}
