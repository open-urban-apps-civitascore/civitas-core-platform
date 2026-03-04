/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialEncryptorTest {

  // 32 bytes = 256-bit key (same test data as CredentialDecryptorTest)
  private static final byte[] MASTER_KEY = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
    0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
  };

  private static final byte[] STRETCHED_KEY;

  static {
    try {
      STRETCHED_KEY = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    } catch (GeneralSecurityException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final String TEST_CTX = "test-ctx";

  @Test
  void encrypt_shouldProduceDecryptableOutput() throws GeneralSecurityException {
    String plaintext = "my-secret-password";

    String encrypted = CredentialEncryptor.encrypt(plaintext, STRETCHED_KEY, TEST_CTX);

    assertNotNull(encrypted);
    assertFalse(encrypted.isEmpty());
    assertEquals(plaintext, CredentialDecryptor.decrypt(encrypted, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void encrypt_unicode_shouldRoundTrip() throws GeneralSecurityException {
    String plaintext = "Ümlaute-äöü-日本語-\uD83D\uDD11";

    String encrypted = CredentialEncryptor.encrypt(plaintext, STRETCHED_KEY, TEST_CTX);
    String decrypted = CredentialDecryptor.decrypt(encrypted, STRETCHED_KEY, TEST_CTX);

    assertEquals(plaintext, decrypted);
  }

  @Test
  void encrypt_samePlaintext_shouldProduceDifferentCiphertexts() throws GeneralSecurityException {
    String plaintext = "same-text";

    String enc1 = CredentialEncryptor.encrypt(plaintext, STRETCHED_KEY, TEST_CTX);
    String enc2 = CredentialEncryptor.encrypt(plaintext, STRETCHED_KEY, TEST_CTX);

    assertNotEquals(enc1, enc2, "Different IVs should produce different ciphertexts");
    assertEquals(
        CredentialDecryptor.decrypt(enc1, STRETCHED_KEY, TEST_CTX),
        CredentialDecryptor.decrypt(enc2, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void encrypt_null_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt(null, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void encrypt_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("", STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void encrypt_nullContext_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("secret", STRETCHED_KEY, null));
  }

  @Test
  void encrypt_emptyContext_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("secret", STRETCHED_KEY, ""));
  }

  @Test
  void encryptMapValues_shouldEncryptAllStringValues() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("password", "secret");
    input.put("port", 8080);

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertTrue(((String) result.get("username")).startsWith("ENC("));
    assertTrue(((String) result.get("username")).endsWith(")"));
    assertTrue(((String) result.get("password")).startsWith("ENC("));
    assertTrue(((String) result.get("password")).endsWith(")"));
    assertEquals(8080, result.get("port"));
  }

  @Test
  void encryptMapValues_nestedMap_shouldEncryptRecursively() throws GeneralSecurityException {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("key", "nested-secret");
    nested.put("plain", "text");

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("outer", "value");
    input.put("inner", nested);

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertTrue(((String) result.get("outer")).startsWith("ENC("));

    @SuppressWarnings("unchecked")
    Map<String, Object> innerResult = (Map<String, Object>) result.get("inner");
    assertTrue(((String) innerResult.get("key")).startsWith("ENC("));
    assertTrue(((String) innerResult.get("plain")).startsWith("ENC("));
  }

  @Test
  void encryptMapValues_alreadyEncrypted_shouldSkip() throws GeneralSecurityException {
    String alreadyEncrypted = "ENC(someBase64Data)";

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", alreadyEncrypted);
    input.put("plain", "not-encrypted");

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertEquals(alreadyEncrypted, result.get("password"));
    assertTrue(((String) result.get("plain")).startsWith("ENC("));
  }

  @Test
  void encryptMapValues_nullMap_shouldReturnEmptyMap() throws GeneralSecurityException {
    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(null, STRETCHED_KEY, TEST_CTX);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void encryptMapValues_nullValueInMap_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", "secret");
    input.put("nullable", null);

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertTrue(((String) result.get("password")).startsWith("ENC("));
    assertNull(result.get("nullable"));
  }

  @Test
  void encryptMapValues_emptyStringValue_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("description", "");
    input.put("password", "secret");

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertEquals("", result.get("description"));
    assertTrue(((String) result.get("password")).startsWith("ENC("));
  }

  @Test
  void encrypt_decryptWithWrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = CredentialEncryptor.encrypt("secret", STRETCHED_KEY, TEST_CTX);

    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;

    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, wrongKey, TEST_CTX));
  }

  @Test
  void encryptMapValues_roundTripWithDecryptMapValues() throws GeneralSecurityException {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("dbPassword", "db-secret");

    Map<String, Object> original = new LinkedHashMap<>();
    original.put("username", "admin");
    original.put("password", "my-secret");
    original.put("port", 8080);
    original.put("connection", nested);

    Map<String, Object> encrypted =
        CredentialEncryptor.encryptMapValues(original, STRETCHED_KEY, TEST_CTX);
    Map<String, Object> decrypted =
        CredentialDecryptor.decryptMapValues(encrypted, STRETCHED_KEY, TEST_CTX);

    assertEquals("admin", decrypted.get("username"));
    assertEquals("my-secret", decrypted.get("password"));
    assertEquals(8080, decrypted.get("port"));

    @SuppressWarnings("unchecked")
    Map<String, Object> decryptedNested = (Map<String, Object>) decrypted.get("connection");
    assertEquals("db-secret", decryptedNested.get("dbPassword"));
  }

  // ─── F3: encryptMapValues returns new map, original unmodified ────────────

  @Test
  void encryptMapValues_shouldReturnNewMap_originalUnmodified() throws GeneralSecurityException {
    Map<String, Object> original = new LinkedHashMap<>();
    original.put("password", "secret");
    original.put("port", 8080);

    Map<String, Object> result =
        CredentialEncryptor.encryptMapValues(original, STRETCHED_KEY, TEST_CTX);

    assertNotSame(original, result, "encryptMapValues must return a new map");
    assertEquals("secret", original.get("password"), "Original map must not be modified");
    assertTrue(
        ((String) result.get("password")).startsWith("ENC("),
        "Result map must contain encrypted values");
  }
}
