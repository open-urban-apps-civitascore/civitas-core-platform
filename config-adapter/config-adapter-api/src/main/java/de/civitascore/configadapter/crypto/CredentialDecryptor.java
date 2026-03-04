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
 * Decrypts credentials encrypted with AES-256-GCM using PBKDF2 + HKDF key derivation. Compliant
 * with BSI TR-02102.
 *
 * <p>Encrypted values are prefixed with {@code ENC(} and suffixed with {@code )}. The encrypted
 * payload is Base64-encoded and contains a version byte, IV (12 bytes), and ciphertext: {@code
 * Base64(version[1] || IV[12] || ciphertext)}.
 *
 * <p>Per-credential key isolation is achieved via HKDF-Expand with a credential context string, so
 * each credential is encrypted under a unique derived key.
 *
 * <p>Environment variable: {@code CIVITAS_MASTER_KEY} — 256-bit master key (hex-encoded, generate
 * with {@code openssl rand -hex 32}).
 */
public final class CredentialDecryptor {

  private CredentialDecryptor() {}

  /**
   * Decrypts a Base64-encoded encrypted value. The encoded payload contains a version byte, 12-byte
   * IV, and AES-GCM ciphertext.
   *
   * @param encryptedBase64 the Base64-encoded payload (version + IV + ciphertext)
   * @param stretchedKey the stretched key bytes (output of {@link
   *     CryptoKeyLoader#stretchMasterKey})
   * @param credentialContext a non-empty context string matching the one used during encryption
   * @return the decrypted plaintext string
   * @throws IllegalArgumentException if {@code encryptedBase64} is {@code null} or empty
   * @throws GeneralSecurityException if decryption fails or Base64 decoding fails
   */
  public static String decrypt(
      String encryptedBase64, byte[] stretchedKey, String credentialContext)
      throws GeneralSecurityException {
    if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
      throw new IllegalArgumentException("Encrypted value cannot be null or empty");
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encryptedBase64);
    } catch (IllegalArgumentException e) {
      throw new GeneralSecurityException("Invalid Base64 in encrypted value", e);
    }

    byte[] ciphertext = null;
    byte[] plaintext = null;
    byte[] iv = new byte[CryptoUtils.GCM_IV_LENGTH_BYTES];
    try {
      // Minimum: 1 (version) + 12 (IV) + 1 (at least 1 byte ciphertext)
      if (decoded.length < 1 + CryptoUtils.GCM_IV_LENGTH_BYTES + 1) {
        throw new GeneralSecurityException("Encrypted data too short");
      }

      byte version = decoded[0];
      if (version != CryptoUtils.PAYLOAD_VERSION) {
        throw new GeneralSecurityException("Unsupported payload version: " + (version & 0xFF));
      }

      System.arraycopy(decoded, 1, iv, 0, CryptoUtils.GCM_IV_LENGTH_BYTES);

      ciphertext = new byte[decoded.length - 1 - CryptoUtils.GCM_IV_LENGTH_BYTES];
      System.arraycopy(
          decoded, 1 + CryptoUtils.GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

      SecretKey key = CryptoUtils.hkdfExpand(stretchedKey, credentialContext);
      Cipher cipher = Cipher.getInstance(CryptoUtils.ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(CryptoUtils.GCM_TAG_LENGTH_BITS, iv));
      plaintext = cipher.doFinal(ciphertext);

      return new String(plaintext, StandardCharsets.UTF_8);
    } finally {
      Arrays.fill(decoded, (byte) 0);
      Arrays.fill(iv, (byte) 0);
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
   * @param stretchedKey the stretched key bytes
   * @param credentialContext a non-empty context string matching the one used during encryption
   * @return a new map with decrypted values
   * @throws GeneralSecurityException if any decryption fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> decryptMapValues(
      Map<String, Object> map, byte[] stretchedKey, String credentialContext)
      throws GeneralSecurityException {
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
        result.put(entry.getKey(), decrypt(encPayload, stretchedKey, credentialContext));
      } else if (value instanceof Map<?, ?> nested) {
        result.put(
            entry.getKey(),
            decryptMapValues((Map<String, Object>) nested, stretchedKey, credentialContext));
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
}
