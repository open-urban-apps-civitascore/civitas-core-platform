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
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts credentials with AES-256-GCM using PBKDF2 + HKDF key derivation. Compliant with BSI
 * TR-02102. Counterpart to {@link CredentialDecryptor}.
 *
 * <p>Payload format: {@code Base64(version[1] || IV[12] || ciphertext)}. The version byte enables
 * future crypto agility. Each invocation generates a random IV, so encrypting the same plaintext
 * twice produces different ciphertext.
 */
public final class CredentialEncryptor {

  /**
   * The credential context used by portal-backend when encrypting datasource connector secrets.
   * Config-adapter modules that decrypt these values must use the same context to derive the
   * correct AES key via HKDF-Expand.
   */
  public static final String DATASOURCE_CREDENTIAL_CONTEXT = "portal-backend:datasource-connector";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private CredentialEncryptor() {}

  /**
   * Encrypts a plaintext string with AES-256-GCM. Returns a Base64-encoded payload containing the
   * version byte, 12-byte IV, and ciphertext.
   *
   * @param plaintext the value to encrypt (must not be {@code null} or empty)
   * @param stretchedKey the stretched key bytes (output of {@link
   *     CryptoKeyLoader#stretchMasterKey})
   * @param credentialContext a non-empty context string for per-credential key isolation
   * @return Base64-encoded payload (version + IV + ciphertext)
   * @throws IllegalArgumentException if {@code plaintext} is {@code null} or empty, or if {@code
   *     credentialContext} is {@code null} or empty
   * @throws GeneralSecurityException if encryption fails
   */
  public static String encrypt(String plaintext, byte[] stretchedKey, String credentialContext)
      throws GeneralSecurityException {
    if (plaintext == null || plaintext.isEmpty()) {
      throw new IllegalArgumentException("Plaintext cannot be null or empty");
    }

    byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    byte[] ciphertext = null;
    byte[] combined = null;
    byte[] iv = new byte[CryptoUtils.GCM_IV_LENGTH_BYTES];
    try {
      SecretKey key = CryptoUtils.hkdfExpand(stretchedKey, credentialContext);
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(CryptoUtils.ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(CryptoUtils.GCM_TAG_LENGTH_BITS, iv));
      ciphertext = cipher.doFinal(plaintextBytes);

      // version[1] || IV[12] || ciphertext
      combined = new byte[1 + iv.length + ciphertext.length];
      combined[0] = CryptoUtils.PAYLOAD_VERSION;
      System.arraycopy(iv, 0, combined, 1, iv.length);
      System.arraycopy(ciphertext, 0, combined, 1 + iv.length, ciphertext.length);

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
   * Recursively encrypts all String values in a map, returning a <b>new</b> map. Each String value
   * is wrapped as {@code ENC(base64payload)}. Empty strings are considered non-secret and passed
   * through unchanged. Values that are already encrypted (matching {@code ENC(...)}) are skipped.
   * Nested maps are processed recursively. Non-String values are passed through unchanged.
   *
   * @param map the map containing values to encrypt (may be {@code null})
   * @param stretchedKey the stretched key bytes
   * @param credentialContext a non-empty context string for per-credential key isolation
   * @return a new map with encrypted values (original map is not modified)
   * @throws GeneralSecurityException if any encryption fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> encryptMapValues(
      Map<String, Object> map, byte[] stretchedKey, String credentialContext)
      throws GeneralSecurityException {
    if (map == null) {
      return Map.of();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> nested) {
        result.put(
            entry.getKey(),
            encryptMapValues((Map<String, Object>) nested, stretchedKey, credentialContext));
      } else if (value instanceof String s && !s.isEmpty() && !CryptoUtils.isEncrypted(s)) {
        result.put(
            entry.getKey(),
            CryptoUtils.ENC_PREFIX
                + encrypt(s, stretchedKey, credentialContext)
                + CryptoUtils.ENC_SUFFIX);
      } else {
        result.put(entry.getKey(), value);
      }
    }
    return result;
  }
}
