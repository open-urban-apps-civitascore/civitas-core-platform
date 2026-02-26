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

  // 16 bytes salt (NIST SP 800-132)
  private static final byte[] SALT = {
    0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
    0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50
  };

  @Test
  void encrypt_shouldProduceDecryptableOutput() throws GeneralSecurityException {
    String plaintext = "my-secret-password";

    String encrypted = CredentialEncryptor.encrypt(plaintext, MASTER_KEY, SALT);

    assertNotNull(encrypted);
    assertFalse(encrypted.isEmpty());
    assertEquals(plaintext, CredentialDecryptor.decrypt(encrypted, MASTER_KEY, SALT));
  }

  @Test
  void encrypt_unicode_shouldRoundTrip() throws GeneralSecurityException {
    String plaintext = "Ümlaute-äöü-日本語-\uD83D\uDD11";

    String encrypted = CredentialEncryptor.encrypt(plaintext, MASTER_KEY, SALT);
    String decrypted = CredentialDecryptor.decrypt(encrypted, MASTER_KEY, SALT);

    assertEquals(plaintext, decrypted);
  }

  @Test
  void encrypt_samePlaintext_shouldProduceDifferentCiphertexts() throws GeneralSecurityException {
    String plaintext = "same-text";

    String enc1 = CredentialEncryptor.encrypt(plaintext, MASTER_KEY, SALT);
    String enc2 = CredentialEncryptor.encrypt(plaintext, MASTER_KEY, SALT);

    assertNotEquals(enc1, enc2, "Different IVs should produce different ciphertexts");
    assertEquals(
        CredentialDecryptor.decrypt(enc1, MASTER_KEY, SALT),
        CredentialDecryptor.decrypt(enc2, MASTER_KEY, SALT));
  }

  @Test
  void encrypt_null_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CredentialEncryptor.encrypt(null, MASTER_KEY, SALT));
  }

  @Test
  void encrypt_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CredentialEncryptor.encrypt("", MASTER_KEY, SALT));
  }

  @Test
  void encryptMapValues_shouldEncryptAllStringValues() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("password", "secret");
    input.put("port", 8080);

    Map<String, Object> result = CredentialEncryptor.encryptMapValues(input, MASTER_KEY, SALT);

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

    Map<String, Object> result = CredentialEncryptor.encryptMapValues(input, MASTER_KEY, SALT);

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

    Map<String, Object> result = CredentialEncryptor.encryptMapValues(input, MASTER_KEY, SALT);

    assertEquals(alreadyEncrypted, result.get("password"));
    assertTrue(((String) result.get("plain")).startsWith("ENC("));
  }

  @Test
  void encryptMapValues_nullMap_shouldReturnEmptyMap() throws GeneralSecurityException {
    Map<String, Object> result = CredentialEncryptor.encryptMapValues(null, MASTER_KEY, SALT);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void encryptMapValues_nullValueInMap_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", "secret");
    input.put("nullable", null);

    Map<String, Object> result = CredentialEncryptor.encryptMapValues(input, MASTER_KEY, SALT);

    assertTrue(((String) result.get("password")).startsWith("ENC("));
    assertNull(result.get("nullable"));
  }

  @Test
  void encrypt_nullMasterKey_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CredentialEncryptor.encrypt("secret", null, SALT));
  }

  @Test
  void encrypt_nullSalt_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("secret", MASTER_KEY, null));
  }

  @Test
  void encrypt_emptyMasterKey_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("secret", new byte[0], SALT));
  }

  @Test
  void encrypt_emptySalt_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialEncryptor.encrypt("secret", MASTER_KEY, new byte[0]));
  }

  @Test
  void encryptMapValues_emptyStringValue_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("description", "");
    input.put("password", "secret");

    Map<String, Object> result = CredentialEncryptor.encryptMapValues(input, MASTER_KEY, SALT);

    assertEquals("", result.get("description"));
    assertTrue(((String) result.get("password")).startsWith("ENC("));
  }

  @Test
  void encrypt_decryptWithWrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = CredentialEncryptor.encrypt("secret", MASTER_KEY, SALT);

    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;

    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, wrongKey, SALT));
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
        CredentialEncryptor.encryptMapValues(original, MASTER_KEY, SALT);
    Map<String, Object> decrypted =
        CredentialDecryptor.decryptMapValues(encrypted, MASTER_KEY, SALT);

    assertEquals("admin", decrypted.get("username"));
    assertEquals("my-secret", decrypted.get("password"));
    assertEquals(8080, decrypted.get("port"));

    @SuppressWarnings("unchecked")
    Map<String, Object> decryptedNested = (Map<String, Object>) decrypted.get("connection");
    assertEquals("db-secret", decryptedNested.get("dbPassword"));
  }
}
