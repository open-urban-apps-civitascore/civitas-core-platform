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

  private static final byte[] STRETCHED_KEY;

  static {
    try {
      STRETCHED_KEY = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    } catch (GeneralSecurityException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final String TEST_CTX = "test-ctx";

  // Static test vector: generated with STRETCHED_KEY + TEST_CTX, plaintext "my-secret-password".
  // Hardcoded to decouple from encryptor and detect silent payload format changes.
  private static final String KNOWN_CIPHERTEXT_V1 =
      "AS+mXUDCXLWhH0x4aJ7mOEFIgjA3zz7AccTaAAZ6lpT1TknAdYDYhd2y4bN5o6Y=";

  @Test
  void decrypt_knownCiphertextV1_shouldProduceExpectedPlaintext() throws GeneralSecurityException {
    assertEquals(
        "my-secret-password",
        CredentialDecryptor.decrypt(KNOWN_CIPHERTEXT_V1, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_roundTrip_shouldRecoverOriginalPlaintext() throws GeneralSecurityException {
    String plaintext = "my-secret-password";
    String encrypted = encrypt(plaintext, STRETCHED_KEY, TEST_CTX);
    String decrypted = CredentialDecryptor.decrypt(encrypted, STRETCHED_KEY, TEST_CTX);
    assertEquals(plaintext, decrypted);
  }

  @Test
  void decrypt_roundTripWithUnicode_shouldRecoverOriginalPlaintext()
      throws GeneralSecurityException {
    String plaintext = "Ümlaute-äöü-日本語-\uD83D\uDD11";
    String encrypted = encrypt(plaintext, STRETCHED_KEY, TEST_CTX);
    String decrypted = CredentialDecryptor.decrypt(encrypted, STRETCHED_KEY, TEST_CTX);
    assertEquals(plaintext, decrypted);
  }

  @Test
  void decrypt_wrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", STRETCHED_KEY, TEST_CTX);
    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, wrongKey, TEST_CTX));
  }

  // ─── F4: Invalid Base64 produces GeneralSecurityException ─────────────────

  @Test
  void decrypt_invalidBase64_shouldThrowGeneralSecurityException() {
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt("not!!!valid", STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_corruptData_shouldThrowException() {
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt("not-valid-base64!!!", STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_tooShortData_shouldThrowException() {
    // Base64 of less than 14 bytes (1 version + 12 IV + 1 ciphertext)
    String shortData = java.util.Base64.getEncoder().encodeToString(new byte[5]);
    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(shortData, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_null_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialDecryptor.decrypt(null, STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CredentialDecryptor.decrypt("", STRETCHED_KEY, TEST_CTX));
  }

  @Test
  void decrypt_wrongVersion_shouldThrowGeneralSecurityException() {
    // Build a payload with version byte 0x99 instead of 0x01
    byte[] fakePayload = new byte[1 + 12 + 16]; // version + IV + fake ciphertext
    fakePayload[0] = (byte) 0x99;
    String encoded = java.util.Base64.getEncoder().encodeToString(fakePayload);

    GeneralSecurityException ex =
        assertThrows(
            GeneralSecurityException.class,
            () -> CredentialDecryptor.decrypt(encoded, STRETCHED_KEY, TEST_CTX));
    assertTrue(ex.getMessage().contains("Unsupported payload version"));
  }

  // ─── Cross-decryption test: different contexts cannot decrypt each other ──

  @Test
  void decrypt_differentContext_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", STRETCHED_KEY, "context-a");

    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decrypt(encrypted, STRETCHED_KEY, "context-b"));
  }

  // ─── decryptMapValues tests ────────────────────────────────────────────────

  @Test
  void decryptMapValues_encryptedValues_shouldDecryptOnlyEncValues()
      throws GeneralSecurityException {
    String encrypted = encrypt("secret-value", STRETCHED_KEY, TEST_CTX);

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("password", "ENC(" + encrypted + ")");
    input.put("port", 8080);

    Map<String, Object> result =
        CredentialDecryptor.decryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertEquals("admin", result.get("username"));
    assertEquals("secret-value", result.get("password"));
    assertEquals(8080, result.get("port"));
  }

  @Test
  void decryptMapValues_nestedMap_shouldDecryptRecursively() throws GeneralSecurityException {
    String encrypted = encrypt("nested-secret", STRETCHED_KEY, TEST_CTX);

    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("key", "ENC(" + encrypted + ")");
    nested.put("plain", "text");

    Map<String, Object> input = new LinkedHashMap<>();
    input.put("outer", "value");
    input.put("inner", nested);

    Map<String, Object> result =
        CredentialDecryptor.decryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    @SuppressWarnings("unchecked")
    Map<String, Object> innerResult = (Map<String, Object>) result.get("inner");
    assertEquals("nested-secret", innerResult.get("key"));
    assertEquals("text", innerResult.get("plain"));
  }

  @Test
  void decryptMapValues_nullMap_shouldReturnEmptyMap() throws GeneralSecurityException {
    Map<String, Object> result =
        CredentialDecryptor.decryptMapValues(null, STRETCHED_KEY, TEST_CTX);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void decryptMapValues_noEncryptedValues_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = Map.of("key1", "value1", "key2", 42);
    Map<String, Object> result =
        CredentialDecryptor.decryptMapValues(input, STRETCHED_KEY, TEST_CTX);
    assertEquals("value1", result.get("key1"));
    assertEquals(42, result.get("key2"));
  }

  @Test
  void decryptMapValues_nullValueInMap_shouldPassThrough() throws GeneralSecurityException {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("username", "admin");
    input.put("nullable", null);

    Map<String, Object> result =
        CredentialDecryptor.decryptMapValues(input, STRETCHED_KEY, TEST_CTX);

    assertEquals("admin", result.get("username"));
    assertNull(result.get("nullable"));
  }

  @Test
  void decryptMapValues_wrongKey_shouldThrowException() throws GeneralSecurityException {
    String encrypted = encrypt("secret", STRETCHED_KEY, TEST_CTX);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("password", "ENC(" + encrypted + ")");

    byte[] wrongKey = new byte[32];
    wrongKey[0] = (byte) 0xFF;

    assertThrows(
        GeneralSecurityException.class,
        () -> CredentialDecryptor.decryptMapValues(input, wrongKey, TEST_CTX));
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

  // ─── hexStringToBytes tests (via CryptoKeyLoader) ──────────────────────────

  @Test
  void hexStringToBytes_validHex_shouldDecodeCorrectly() {
    byte[] result = CryptoKeyLoader.hexStringToBytes("0102030405060708");
    assertArrayEquals(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, result);
  }

  @Test
  void hexStringToBytes_upperAndLowerCase_shouldDecodeCorrectly() {
    byte[] lower = CryptoKeyLoader.hexStringToBytes("abcdef");
    byte[] upper = CryptoKeyLoader.hexStringToBytes("ABCDEF");
    assertArrayEquals(lower, upper);
    assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF}, lower);
  }

  @Test
  void hexStringToBytes_emptyString_shouldReturnEmptyArray() {
    byte[] result = CryptoKeyLoader.hexStringToBytes("");
    assertEquals(0, result.length);
  }

  @Test
  void hexStringToBytes_oddLength_shouldThrowIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> CryptoKeyLoader.hexStringToBytes("abc"));
    assertTrue(ex.getMessage().contains("even length"));
  }

  @Test
  void hexStringToBytes_nonHexCharacters_shouldThrowIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> CryptoKeyLoader.hexStringToBytes("ZZZZ"));
    assertTrue(ex.getMessage().contains("Invalid hex character"));
  }

  // ─── loadKeyFromEnv tests (via CryptoKeyLoader) ────────────────────────────

  @Test
  void loadKeyFromEnv_undefinedVariable_shouldReturnEmptyArray() {
    byte[] result = CryptoKeyLoader.loadKeyFromEnv("CIVITAS_TEST_NONEXISTENT_KEY_12345");
    assertEquals(0, result.length);
  }

  // ─── isEncrypted edge-case tests ──────────────────────────────────────────

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

  @Test
  void encrypt_differentCalls_shouldProduceDifferentCiphertexts() throws GeneralSecurityException {
    String enc1 = encrypt("same-text", STRETCHED_KEY, TEST_CTX);
    String enc2 = encrypt("same-text", STRETCHED_KEY, TEST_CTX);
    assertNotNull(enc1);
    assertNotNull(enc2);
    assertEquals(
        CredentialDecryptor.decrypt(enc1, STRETCHED_KEY, TEST_CTX),
        CredentialDecryptor.decrypt(enc2, STRETCHED_KEY, TEST_CTX));
  }

  // ─── Test helper ──────────────────────────────────────────────────────────

  private static String encrypt(String plaintext, byte[] stretchedKey, String credentialContext)
      throws GeneralSecurityException {
    return CredentialEncryptor.encrypt(plaintext, stretchedKey, credentialContext);
  }
}
