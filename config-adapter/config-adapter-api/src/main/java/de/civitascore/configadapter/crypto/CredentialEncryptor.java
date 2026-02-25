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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts credentials with AES-256-GCM using PBKDF2 key derivation. Compliant with BSI TR-02102.
 * Counterpart to {@link CredentialDecryptor}.
 *
 * <p>Each invocation generates a random IV, so encrypting the same plaintext twice produces
 * different ciphertext. The output format (Base64-encoded IV + ciphertext) is directly compatible
 * with {@link CredentialDecryptor#decrypt}.
 */
public final class CredentialEncryptor {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private CredentialEncryptor() {}

  /**
   * Encrypts a plaintext string with AES-256-GCM. Returns a Base64-encoded payload containing the
   * 12-byte IV prepended to the ciphertext.
   *
   * @param plaintext the value to encrypt (must not be {@code null} or empty)
   * @param masterKey the master key bytes
   * @param salt the salt bytes
   * @return Base64-encoded payload (IV + ciphertext)
   * @throws IllegalArgumentException if {@code plaintext} is {@code null} or empty, or if {@code
   *     masterKey} or {@code salt} is {@code null} or empty
   * @throws GeneralSecurityException if encryption fails
   */
  public static String encrypt(String plaintext, byte[] masterKey, byte[] salt)
      throws GeneralSecurityException {
    if (plaintext == null || plaintext.isEmpty()) {
      throw new IllegalArgumentException("Plaintext cannot be null or empty");
    }

    byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = null;
    byte[] combined = null;
    byte[] iv = new byte[CryptoUtils.GCM_IV_LENGTH_BYTES];
    try {
      SecretKey key = CryptoUtils.deriveKey(masterKey, salt);
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(CryptoUtils.ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(CryptoUtils.GCM_TAG_LENGTH_BITS, iv));
      ciphertext = cipher.doFinal(plaintextBytes);

      combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

      return Base64.getEncoder().encodeToString(combined);
    } finally {
      Arrays.fill(plaintextBytes, (byte) 0);
      Arrays.fill(iv, (byte) 0);
      if (ciphertext != null) {
        Arrays.fill(ciphertext, (byte) 0);
      }
      if (combined != null) {
        Arrays.fill(combined, (byte) 0);
      }
    }
  }

  /**
   * Recursively encrypts all String values in a map. Each String value is wrapped as {@code
   * ENC(base64payload)}. Empty strings are considered non-secret and passed through unchanged.
   * Values that are already encrypted (matching {@code ENC(...)}) are skipped. Nested maps are
   * processed recursively. Non-String values are passed through unchanged.
   *
   * @param map the map containing values to encrypt (may be {@code null})
   * @param masterKey the master key bytes
   * @param salt the salt bytes
   * @return the same map instance with values encrypted in-place
   * @throws GeneralSecurityException if any encryption fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> encryptMapValues(
      Map<String, Object> map, byte[] masterKey, byte[] salt) throws GeneralSecurityException {
    if (map == null) {
      return Map.of();
    }

    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> nested) {
        encryptMapValues((Map<String, Object>) nested, masterKey, salt);
      } else if (value instanceof String s && !s.isEmpty() && !CryptoUtils.isEncrypted(s)) {
        entry.setValue(
            CryptoUtils.ENC_PREFIX + encrypt(s, masterKey, salt) + CryptoUtils.ENC_SUFFIX);
      }
    }
    return map;
  }
}
