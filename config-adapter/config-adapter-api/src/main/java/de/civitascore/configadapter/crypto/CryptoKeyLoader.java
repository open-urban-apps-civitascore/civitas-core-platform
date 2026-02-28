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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Public facade for crypto key operations needed by adapter modules outside the {@code crypto}
 * package. Owns key loading, hex decoding, validation, and PBKDF2 stretching.
 */
public final class CryptoKeyLoader {

  static final int EXPECTED_KEY_LENGTH_BYTES = 32;
  static final int PBKDF2_ITERATIONS = 600_000;
  static final int KEY_LENGTH_BITS = 256;

  /**
   * Intentionally static PBKDF2 salt (32 bytes, all zeros). This is single-target key stretching:
   * we have exactly one master key per deployment, so the salt provides no multi-target resistance
   * benefit. The CPU cost from PBKDF2 iterations is what matters.
   */
  static final byte[] STATIC_PBKDF2_SALT = new byte[32];

  private CryptoKeyLoader() {}

  /**
   * Loads a hex-encoded key from the given environment variable. Returns an empty byte array if the
   * variable is not set or blank. If the key is present, validates that it decodes to exactly 32
   * bytes.
   *
   * @param envVarName the environment variable name
   * @return the decoded key bytes, or an empty array if the variable is absent/blank
   * @throws IllegalArgumentException if the decoded key is not exactly 32 bytes
   */
  public static byte[] loadKeyFromEnv(String envVarName) {
    String hexValue = System.getenv(envVarName);
    if (hexValue == null || hexValue.isBlank()) {
      return new byte[0];
    }
    byte[] key = hexStringToBytes(hexValue);
    if (key.length != EXPECTED_KEY_LENGTH_BYTES) {
      throw new IllegalArgumentException(
          envVarName
              + " must decode to exactly "
              + EXPECTED_KEY_LENGTH_BYTES
              + " bytes, got "
              + key.length);
    }
    return key;
  }

  /**
   * Converts a hex-encoded string to a byte array.
   *
   * @param hex the hex-encoded string (must have even length, only hex characters)
   * @return the decoded byte array
   * @throws IllegalArgumentException if the string has odd length or contains non-hex characters
   */
  public static byte[] hexStringToBytes(String hex) {
    if (hex.length() % 2 != 0) {
      throw new IllegalArgumentException("Hex string must have even length, got " + hex.length());
    }
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      int high = Character.digit(hex.charAt(i), 16);
      int low = Character.digit(hex.charAt(i + 1), 16);
      if (high == -1 || low == -1) {
        throw new IllegalArgumentException(
            "Invalid hex character at index " + (high == -1 ? i : i + 1));
      }
      data[i / 2] = (byte) ((high << 4) + low);
    }
    return data;
  }

  /**
   * Stretches a 32-byte master key via PBKDF2WithHmacSHA256 with a static salt. The result is a
   * 32-byte stretched key suitable for use with credential encryption/decryption.
   *
   * @param masterKey exactly 32 bytes of raw key material
   * @return 32-byte stretched key
   * @throws GeneralSecurityException if key derivation fails
   * @throws IllegalArgumentException if {@code masterKey} is not exactly 32 bytes
   */
  public static byte[] stretchMasterKey(byte[] masterKey) throws GeneralSecurityException {
    validateMasterKeyLength(masterKey);

    // Use ISO-8859-1 to preserve all 256 byte values as char values 0x00–0xFF,
    // avoiding the hex re-encoding that doubles the key material length.
    char[] keyChars = new String(masterKey, StandardCharsets.ISO_8859_1).toCharArray();
    PBEKeySpec spec = null;
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      spec = new PBEKeySpec(keyChars, STATIC_PBKDF2_SALT, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
      byte[] derived = factory.generateSecret(spec).getEncoded();
      return derived;
    } catch (RuntimeException e) {
      throw new GeneralSecurityException("Key stretching failed", e);
    } finally {
      if (spec != null) {
        spec.clearPassword();
      }
      Arrays.fill(keyChars, '\0');
    }
  }

  /**
   * Loads a hex-encoded master key from the given environment variable, validates its length, and
   * stretches it via PBKDF2. Returns an empty byte array if the variable is not set or blank. The
   * raw master key bytes are zeroed after stretching.
   *
   * <p>Callers are responsible for zeroing the returned stretched key after use.
   *
   * @param envVarName the environment variable name
   * @return 32-byte stretched key, or empty array if the variable is absent/blank
   * @throws IllegalStateException if key stretching fails
   */
  public static byte[] loadAndStretchKeyFromEnv(String envVarName) {
    byte[] masterKey = loadKeyFromEnv(envVarName);
    if (masterKey.length == 0) {
      return new byte[0];
    }
    try {
      return stretchMasterKey(masterKey);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to stretch master key from " + envVarName, e);
    } finally {
      Arrays.fill(masterKey, (byte) 0);
    }
  }

  /**
   * Validates that the master key is exactly 32 bytes (256 bits).
   *
   * @param masterKey the key bytes to validate
   * @throws IllegalArgumentException if the key is null, empty, or not 32 bytes
   */
  public static void validateMasterKeyLength(byte[] masterKey) {
    if (masterKey == null || masterKey.length != EXPECTED_KEY_LENGTH_BYTES) {
      throw new IllegalArgumentException(
          "Master key must be exactly "
              + EXPECTED_KEY_LENGTH_BYTES
              + " bytes, got "
              + (masterKey == null ? "null" : masterKey.length));
    }
  }
}
