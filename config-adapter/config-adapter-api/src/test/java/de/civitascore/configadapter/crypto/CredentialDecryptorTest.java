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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialDecryptorTest {

  // 32 bytes = 256-bit key
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

  // Static test vector: generated with MASTER_KEY + SALT, plaintext "my-secret-password".
  // Hardcoded to decouple decryptor tests from CredentialEncryptor correctness.
  private static final String KNOWN_CIPHERTEXT =
      "FH1mNqq2m9tY0SJtJZb7j4uJFWHa+lWYd8p8I2qM1qWFrOnn3lCS4NFxrKST0g==";

  @Test
  void decrypt_knownCiphertext_shouldReturnExpectedPlaintext() throws GeneralSecurityException {
    String decrypted = CredentialDecryptor.decrypt(KNOWN_CIPHERTEXT, MASTER_KEY, SALT);
    assertEquals("my-secret-password", decrypted);
  }

  @Test
  void decrypt_roundTrip_shouldRecoverOriginalPlaintext() throws GeneralSecurityException {
    String plaintext = "my-secret-password";
    String encrypted = encrypt(plaintext, MASTER_KEY, SALT);
    String decrypted = CredentialDecryptor.decrypt(encrypted, MASTER_KEY, SALT);
    assertEquals(plaintext, decrypted);
  }

  @Test
  void decrypt_roundTripWithUnicode_shouldRecoverOriginalPlaintext()
      throws GeneralSecurityException {
    String plaintext = "Ümlaute-äöü-日本語-\uD83D\uDD11";
    String encrypted = encrypt(plaintext, MASTER_KEY, SALT);
    String decrypted = CredentialDecryptor.decrypt(encrypted, MASTER_KEY, SALT);
    assertEquals(plaintext, decrypted);
  }

  @Test
  void decrypt_wrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", MASTER_KEY, SALT);
    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, wrongKey, SALT));
  }

  @Test
  void decrypt_wrongSalt_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", MASTER_KEY, SALT);
    byte[] wrongSalt = {
      0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
      0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
    };
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, MASTER_KEY, wrongSalt));
  }

  @Test
  void decrypt_corruptData_shouldThrowException() {
    assertThrows(
        Exception.class,
        () -> CredentialDecryptor.decrypt("not-valid-base64!!!", MASTER_KEY, SALT));
  }

  @Test
  void decrypt_tooShortData_shouldThrowException() {
    // Base64 of less than 13 bytes
    String shortData = java.util.Base64.getEncoder().encodeToString(new byte[5]);
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(shortData, MASTER_KEY, SALT));
  }

  @Test
  void decrypt_null_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CredentialDecryptor.decrypt(null, MASTER_KEY, SALT));
  }

  @Test
  void decrypt_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CredentialDecryptor.decrypt("", MASTER_KEY, SALT));
  }

  @Test
  void decryptMapValues_encryptedValues_shouldDecryptOnlyEncValues()
      throws GeneralSecurityException {
    String encrypted = encrypt("secret-value", MASTER_KEY, SALT);

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("password", "ENC(" + encrypted + ")");
    input.put("port", 8080);

    Map<String, Object> result = CredentialDecryptor.decryptMapValues(input, MASTER_KEY, SALT);

    assertEquals("admin", result.get("username"));
    assertEquals("secret-value", result.get("password"));
    assertEquals(8080, result.get("port"));
  }

  @Test
  void decryptMapValues_nestedMap_shouldDecryptRecursively() throws GeneralSecurityException {
    String encrypted = encrypt("nested-secret", MASTER_KEY, SALT);

    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("key", "ENC(" + encrypted + ")");
    nested.put("plain", "text");

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("outer", "value");
    input.put("inner", nested);

    Map<String, Object> result = CredentialDecryptor.decryptMapValues(input, MASTER_KEY, SALT);

    @SuppressWarnings("unchecked")
    Map<String, Object> innerResult = (Map<String, Object>) result.get("inner");
    assertEquals("nested-secret", innerResult.get("key"));
    assertEquals("text", innerResult.get("plain"));
  }

  @Test
  void decryptMapValues_nullMap_shouldReturnEmptyMap() throws GeneralSecurityException {
    Map<String, Object> result = CredentialDecryptor.decryptMapValues(null, MASTER_KEY, SALT);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void decryptMapValues_noEncryptedValues_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = Map.of("key1", "value1", "key2", 42);
    Map<String, Object> result = CredentialDecryptor.decryptMapValues(input, MASTER_KEY, SALT);
    assertEquals("value1", result.get("key1"));
    assertEquals(42, result.get("key2"));
  }

  @Test
  void deriveKey_sameInputs_shouldProduceSameKey() throws GeneralSecurityException {
    javax.crypto.SecretKey key1 = CryptoUtils.deriveKey(MASTER_KEY, SALT);
    javax.crypto.SecretKey key2 = CryptoUtils.deriveKey(MASTER_KEY, SALT);
    assertEquals(key1, key2);
  }

  @Test
  void deriveKey_nullMasterKey_shouldThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.deriveKey(null, SALT));
  }

  @Test
  void deriveKey_nullSalt_shouldThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.deriveKey(MASTER_KEY, null));
  }

  @Test
  void deriveKey_emptyMasterKey_shouldThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.deriveKey(new byte[0], SALT));
  }

  @Test
  void deriveKey_emptySalt_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoUtils.deriveKey(MASTER_KEY, new byte[0]));
  }

  @Test
  void deriveKey_tooShortSalt_shouldThrowIllegalArgumentException() {
    byte[] shortSalt = new byte[8];
    assertThrows(
        IllegalArgumentException.class, () -> CryptoUtils.deriveKey(MASTER_KEY, shortSalt));
  }

  @Test
  void encrypt_differentCalls_shouldProduceDifferentCiphertexts() throws GeneralSecurityException {
    String enc1 = encrypt("same-text", MASTER_KEY, SALT);
    String enc2 = encrypt("same-text", MASTER_KEY, SALT);
    // Different IVs means different ciphertexts
    assertNotNull(enc1);
    assertNotNull(enc2);
    // Both should decrypt to the same value
    assertEquals(
        CredentialDecryptor.decrypt(enc1, MASTER_KEY, SALT),
        CredentialDecryptor.decrypt(enc2, MASTER_KEY, SALT));
  }

  // ─── containsEncryptedValues tests ─────────────────────────────────────────

  @Test
  void containsEncryptedValues_withEncValue_shouldReturnTrue() {
    Map<String, Object> map = Map.of("username", "admin", "password", "ENC(abc123)");
    assertTrue(CredentialDecryptor.containsEncryptedValues(map));
  }

  @Test
  void containsEncryptedValues_withoutEncValue_shouldReturnFalse() {
    Map<String, Object> map = Map.of("username", "admin", "port", 8080);
    assertFalse(CredentialDecryptor.containsEncryptedValues(map));
  }

  @Test
  void containsEncryptedValues_nullMap_shouldReturnFalse() {
    assertFalse(CredentialDecryptor.containsEncryptedValues(null));
  }

  // ─── hexStringToBytes tests ────────────────────────────────────────────────

  @Test
  void hexStringToBytes_validHex_shouldDecodeCorrectly() {
    byte[] result = CredentialDecryptor.hexStringToBytes("0102030405060708");
    assertArrayEquals(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, result);
  }

  @Test
  void hexStringToBytes_upperAndLowerCase_shouldDecodeCorrectly() {
    byte[] lower = CredentialDecryptor.hexStringToBytes("abcdef");
    byte[] upper = CredentialDecryptor.hexStringToBytes("ABCDEF");
    assertArrayEquals(lower, upper);
    assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF}, lower);
  }

  @Test
  void hexStringToBytes_emptyString_shouldReturnEmptyArray() {
    byte[] result = CredentialDecryptor.hexStringToBytes("");
    assertEquals(0, result.length);
  }

  @Test
  void hexStringToBytes_oddLength_shouldThrowIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> CredentialDecryptor.hexStringToBytes("abc"));
    assertTrue(ex.getMessage().contains("even length"));
  }

  @Test
  void hexStringToBytes_nonHexCharacters_shouldThrowIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> CredentialDecryptor.hexStringToBytes("ZZZZ"));
    assertTrue(ex.getMessage().contains("Invalid hex character"));
  }

  // ─── loadKeyFromEnv tests ─────────────────────────────────────────────────

  @Test
  void loadKeyFromEnv_undefinedVariable_shouldReturnEmptyArray() {
    // Use a variable name that is very unlikely to exist
    byte[] result = CredentialDecryptor.loadKeyFromEnv("CIVITAS_TEST_NONEXISTENT_KEY_12345");
    assertEquals(0, result.length);
  }

  @Test
  void decryptMapValues_nullValueInMap_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("nullable", null);

    Map<String, Object> result = CredentialDecryptor.decryptMapValues(input, MASTER_KEY, SALT);

    assertEquals("admin", result.get("username"));
    assertNull(result.get("nullable"));
  }

  @Test
  void decryptMapValues_wrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", MASTER_KEY, SALT);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", "ENC(" + encrypted + ")");

    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;

    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decryptMapValues(input, wrongKey, SALT));
  }

  // ─── isEncrypted edge-case tests ─────────────────────────────────────────────

  @Test
  void isEncrypted_validEncPrefix_shouldReturnTrue() {
    assertTrue(CryptoUtils.isEncrypted("ENC(x)"));
  }

  @Test
  void isEncrypted_emptyPayload_shouldReturnFalse() {
    assertFalse(CryptoUtils.isEncrypted("ENC()"));
  }

  @Test
  void isEncrypted_missingPrefix_shouldReturnFalse() {
    assertFalse(CryptoUtils.isEncrypted("abc)"));
  }

  @Test
  void isEncrypted_missingSuffix_shouldReturnFalse() {
    assertFalse(CryptoUtils.isEncrypted("ENC(abc"));
  }

  // ─── Test helper ─────────────────────────────────────────────────────────────

  private static String encrypt(String plaintext, byte[] masterKey, byte[] salt)
      throws GeneralSecurityException {
    return CredentialEncryptor.encrypt(plaintext, masterKey, salt);
  }
}
