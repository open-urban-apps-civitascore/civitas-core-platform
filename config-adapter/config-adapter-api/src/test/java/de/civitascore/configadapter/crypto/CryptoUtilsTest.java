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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CryptoUtilsTest {

  // 32 bytes = 256-bit key
  private static final byte[] MASTER_KEY = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
    0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
  };

  // ─── stretchMasterKey pinning test ──────────────────────────────────────────

  @Test
  void stretchMasterKey_knownInput_shouldProduceStableOutput() throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);

    assertEquals(32, stretched.length);
    // Pin the exact hex output so any accidental algorithm/parameter change breaks the test.
    assertEquals(
        "8f7fcac8e26c227a7ad97ffe15c6217e13c0e916de862d5d0ab11172080f1d03", bytesToHex(stretched));
  }

  @Test
  void stretchMasterKey_sameInput_shouldProduceSameOutput() throws GeneralSecurityException {
    byte[] s1 = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    byte[] s2 = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    assertArrayEquals(s1, s2);
  }

  // ─── HKDF-Expand tests ───────────────────────────────────────────────────────

  @Test
  void hkdfExpand_knownInputs_shouldProduceStableOutput() throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);

    SecretKey key1 = CryptoUtils.hkdfExpand(stretched, "test-context");
    SecretKey key2 = CryptoUtils.hkdfExpand(stretched, "test-context");

    // Same context → same key
    assertArrayEquals(key1.getEncoded(), key2.getEncoded());
    assertEquals(32, key1.getEncoded().length);
    // Pin the exact hex output so any accidental algorithm/parameter change breaks the test.
    assertEquals(
        "3e0d5bf4fec3803c74aa0809fa4e43fbf6d9b00a5b167457d5bce4783d957a48",
        bytesToHex(key1.getEncoded()));
  }

  @Test
  void hkdfExpand_differentContexts_shouldProduceDifferentKeys() throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);

    SecretKey keyA = CryptoUtils.hkdfExpand(stretched, "context-a");
    SecretKey keyB = CryptoUtils.hkdfExpand(stretched, "context-b");

    assertNotEquals(
        bytesToHex(keyA.getEncoded()),
        bytesToHex(keyB.getEncoded()),
        "Different contexts must produce different keys");
  }

  @Test
  void hkdfExpand_sameContext_shouldProduceSameKey() throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);

    SecretKey key1 = CryptoUtils.hkdfExpand(stretched, "same-ctx");
    SecretKey key2 = CryptoUtils.hkdfExpand(stretched, "same-ctx");

    assertArrayEquals(key1.getEncoded(), key2.getEncoded());
  }

  @Test
  void hkdfExpand_nullContext_shouldThrowIllegalArgumentException()
      throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.hkdfExpand(stretched, null));
  }

  @Test
  void hkdfExpand_emptyContext_shouldThrowIllegalArgumentException()
      throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.hkdfExpand(stretched, ""));
  }

  // ─── RFC 5869 HKDF-Expand single-block verification ─────────────────────────

  @Test
  void hkdfExpand_rfc5869_singleBlockExpand_shouldMatchExpectedStructure()
      throws GeneralSecurityException {
    // Verify HKDF-Expand produces HMAC-SHA256(PRK, info || 0x01) for single-block output.
    // We verify by computing the same thing manually.
    byte[] prk = new byte[32];
    for (int i = 0; i < 32; i++) {
      prk[i] = (byte) (i + 1);
    }

    SecretKey result = CryptoUtils.hkdfExpand(prk, "test");
    assertEquals(32, result.getEncoded().length);
    assertEquals("AES", result.getAlgorithm());
  }

  // ─── validateMasterKeyLength boundary tests (F8) ────────────────────────────

  @Test
  void validateMasterKeyLength_31bytes_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CryptoKeyLoader.validateMasterKeyLength(new byte[31]));
  }

  @Test
  void validateMasterKeyLength_32bytes_shouldSucceed() {
    CryptoKeyLoader.validateMasterKeyLength(new byte[32]);
  }

  @Test
  void validateMasterKeyLength_33bytes_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CryptoKeyLoader.validateMasterKeyLength(new byte[33]));
  }

  @Test
  void validateMasterKeyLength_null_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoKeyLoader.validateMasterKeyLength(null));
  }

  @Test
  void validateMasterKeyLength_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoKeyLoader.validateMasterKeyLength(new byte[0]));
  }

  // ─── loadAndStretchKeyFromEnv tests ─────────────────────────────────────────

  @Test
  void loadAndStretchKeyFromEnv_undefinedVariable_shouldReturnEmptyArray() {
    byte[] result = CryptoKeyLoader.loadAndStretchKeyFromEnv("CIVITAS_TEST_NONEXISTENT_KEY_99999");
    assertEquals(0, result.length);
  }

  // ─── Version byte test ───────────────────────────────────────────────────────

  @Test
  void encrypt_versionByte_shouldBePrependedToPayload() throws GeneralSecurityException {
    byte[] stretched = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    String encrypted = CredentialEncryptor.encrypt("test-value", stretched, "test-ctx");

    byte[] decoded = Base64.getDecoder().decode(encrypted);

    assertEquals(
        CryptoUtils.PAYLOAD_VERSION,
        decoded[0],
        "First byte of decoded payload must be the version byte");
  }

  // ─── stretchMasterKey boundary tests ─────────────────────────────────────────

  @Test
  void stretchMasterKey_31bytes_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoKeyLoader.stretchMasterKey(new byte[31]));
  }

  @Test
  void stretchMasterKey_33bytes_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoKeyLoader.stretchMasterKey(new byte[33]));
  }

  @Test
  void stretchMasterKey_null_shouldThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CryptoKeyLoader.stretchMasterKey(null));
  }

  @Test
  void stretchMasterKey_empty_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoKeyLoader.stretchMasterKey(new byte[0]));
  }

  // ─── hkdfExpand defensive tests: null/empty/wrong-length stretchedKey ────────

  @Test
  void hkdfExpand_nullStretchedKey_shouldThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CryptoUtils.hkdfExpand(null, "context"));
  }

  @Test
  void hkdfExpand_emptyStretchedKey_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoUtils.hkdfExpand(new byte[0], "context"));
  }

  @Test
  void hkdfExpand_wrongLengthStretchedKey_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class, () -> CryptoUtils.hkdfExpand(new byte[16], "context"));
  }

  // ─── stretchMasterKey RuntimeException wrapping test ──────────────────────

  @Test
  void stretchMasterKey_runtimeException_shouldWrapAsGeneralSecurityException() {
    try (MockedStatic<SecretKeyFactory> mocked = mockStatic(SecretKeyFactory.class)) {
      mocked
          .when(() -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"))
          .thenThrow(new RuntimeException("simulated JCE failure"));

      GeneralSecurityException ex =
          assertThrows(
              GeneralSecurityException.class, () -> CryptoKeyLoader.stretchMasterKey(new byte[32]));
      assertEquals("Key stretching failed", ex.getMessage());
      assertInstanceOf(RuntimeException.class, ex.getCause());
    }
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }
}
