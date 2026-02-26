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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Decrypts credentials encrypted with AES-256-GCM using PBKDF2 key derivation. Compliant with BSI
 * TR-02102.
 *
 * <p>Encrypted values are prefixed with {@code ENC(} and suffixed with {@code )}. The encrypted
 * payload is Base64-encoded and contains the IV (12 bytes) prepended to the ciphertext.
 *
 * <p>Environment variables:
 *
 * <ul>
 *   <li>{@code CIVITAS_MASTER_KEY} — 256-bit master key (hex-encoded)
 *   <li>{@code CIVITAS_MASTER_SALT} — 16+ byte salt, hex-encoded (NIST SP 800-132)
 * </ul>
 */
public final class CredentialDecryptor {

  private CredentialDecryptor() {}

  /**
   * Decrypts a Base64-encoded encrypted value. The encoded payload contains the 12-byte IV
   * prepended to the AES-GCM ciphertext.
   *
   * @param encryptedBase64 the Base64-encoded payload (IV + ciphertext)
   * @param masterKey the master key bytes
   * @param salt the salt bytes
   * @return the decrypted plaintext string
   * @throws IllegalArgumentException if {@code encryptedBase64} is {@code null} or empty, or if
   *     {@code masterKey} or {@code salt} is {@code null} or empty
   * @throws GeneralSecurityException if decryption fails
   */
  public static String decrypt(String encryptedBase64, byte[] masterKey, byte[] salt)
      throws GeneralSecurityException {
    if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
      throw new IllegalArgumentException("Encrypted value cannot be null or empty");
    }

    byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
    byte[] ciphertext = null;
    byte[] plaintext = null;
    try {
      if (decoded.length < CryptoUtils.GCM_IV_LENGTH_BYTES + 1) {
        throw new GeneralSecurityException("Encrypted data too short");
      }

      byte[] iv = new byte[CryptoUtils.GCM_IV_LENGTH_BYTES];
      System.arraycopy(decoded, 0, iv, 0, CryptoUtils.GCM_IV_LENGTH_BYTES);

      ciphertext = new byte[decoded.length - CryptoUtils.GCM_IV_LENGTH_BYTES];
      System.arraycopy(decoded, CryptoUtils.GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

      SecretKey key = CryptoUtils.deriveKey(masterKey, salt);
      Cipher cipher = Cipher.getInstance(CryptoUtils.ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(CryptoUtils.GCM_TAG_LENGTH_BITS, iv));
      plaintext = cipher.doFinal(ciphertext);

      return new String(plaintext, StandardCharsets.UTF_8);
    } finally {
      Arrays.fill(decoded, (byte) 0);
      if (ciphertext != null) {
        Arrays.fill(ciphertext, (byte) 0);
      }
      if (plaintext != null) {
        Arrays.fill(plaintext, (byte) 0);
      }
    }
  }

  /**
   * Recursively decrypts all String values in a map that start with {@code ENC(} and end with
   * {@code )}. Non-encrypted values and non-String values are passed through unchanged. Nested maps
   * are processed recursively.
   *
   * @param map the map containing potentially encrypted values
   * @param masterKey the master key bytes
   * @param salt the salt bytes
   * @return a new map with decrypted values
   * @throws GeneralSecurityException if any decryption fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> decryptMapValues(
      Map<String, Object> map, byte[] masterKey, byte[] salt) throws GeneralSecurityException {
    if (map == null) {
      return Map.of();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String s && CryptoUtils.isEncrypted(s)) {
        String encPayload =
            s.substring(
                CryptoUtils.ENC_PREFIX.length(), s.length() - CryptoUtils.ENC_SUFFIX.length());
        result.put(entry.getKey(), decrypt(encPayload, masterKey, salt));
      } else if (value instanceof Map<?, ?> nested) {
        result.put(entry.getKey(), decryptMapValues((Map<String, Object>) nested, masterKey, salt));
      } else {
        result.put(entry.getKey(), value);
      }
    }
    return result;
  }

  /**
   * Checks whether any value in the given map (or nested maps) is an encrypted credential prefixed
   * with {@code ENC(} and suffixed with {@code )}.
   *
   * @param map the map to inspect (may be {@code null})
   * @return {@code true} if at least one encrypted value is found
   */
  @SuppressWarnings("unchecked")
  public static boolean containsEncryptedValues(Map<String, Object> map) {
    if (map == null) {
      return false;
    }
    for (Object value : map.values()) {
      if (value instanceof String s && CryptoUtils.isEncrypted(s)) {
        return true;
      }
      if (value instanceof Map<?, ?> nested
          && containsEncryptedValues((Map<String, Object>) nested)) {
        return true;
      }
    }
    return false;
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
   * Loads a hex-encoded key from the given environment variable. Returns an empty byte array if the
   * variable is not set or blank.
   *
   * @param envVarName the environment variable name
   * @return the decoded key bytes, or an empty array if the variable is absent/blank
   */
  public static byte[] loadKeyFromEnv(String envVarName) {
    String hexValue = System.getenv(envVarName);
    if (hexValue == null || hexValue.isBlank()) {
      return new byte[0];
    }
    return hexStringToBytes(hexValue);
  }
}
