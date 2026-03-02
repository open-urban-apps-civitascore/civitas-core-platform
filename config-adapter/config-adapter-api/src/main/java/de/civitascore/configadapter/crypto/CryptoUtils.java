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
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Package-private utilities and constants shared between {@link CredentialDecryptor} and {@link
 * CredentialEncryptor}.
 *
 * <p>Key derivation uses a two-phase approach:
 *
 * <ol>
 *   <li><b>PBKDF2</b> (one-time stretch) — stretches the raw master key via {@link
 *       CryptoKeyLoader#stretchMasterKey(byte[])} with a static salt. This is single-target key
 *       stretching: the salt is intentionally static because the goal is CPU cost, not multi-target
 *       resistance (we have exactly one master key per deployment).
 *   <li><b>HKDF-Expand</b> (per-credential isolation) — derives a unique AES-256 key for each
 *       credential context via {@link #hkdfExpand(byte[], String)}. Different contexts (e.g.
 *       pipeline IDs) produce different keys, so compromising one ciphertext does not help decrypt
 *       another.
 * </ol>
 */
final class CryptoUtils {

  static final String ALGORITHM = "AES/GCM/NoPadding";
  static final int GCM_TAG_LENGTH_BITS = 128;
  static final int GCM_IV_LENGTH_BYTES = 12;
  static final String ENC_PREFIX = "ENC(";
  static final String ENC_SUFFIX = ")";

  /** Version byte prepended to the ciphertext payload for crypto agility. */
  static final byte PAYLOAD_VERSION = 0x01;

  private static final int STRETCHED_KEY_LENGTH = 32;

  private CryptoUtils() {}

  static boolean isEncrypted(String value) {
    return value.startsWith(ENC_PREFIX)
        && value.endsWith(ENC_SUFFIX)
        && value.length() > ENC_PREFIX.length() + ENC_SUFFIX.length();
  }

  /**
   * HKDF-Expand (RFC 5869 Section 2.3) using HMAC-SHA256 for one-block expansion (32 bytes). The
   * context string provides per-credential key isolation.
   *
   * @param stretchedKey 32-byte output of {@link CryptoKeyLoader#stretchMasterKey(byte[])}
   * @param context a non-null, non-empty context string (e.g. pipeline ID)
   * @return an AES-256 {@link SecretKey} unique to the given context
   * @throws GeneralSecurityException if HMAC computation fails
   * @throws IllegalArgumentException if {@code context} is null or empty
   */
  static SecretKey hkdfExpand(byte[] stretchedKey, String context) throws GeneralSecurityException {
    if (stretchedKey == null || stretchedKey.length != STRETCHED_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "Stretched key must be exactly "
              + STRETCHED_KEY_LENGTH
              + " bytes, got "
              + (stretchedKey == null ? "null" : stretchedKey.length));
    }
    if (context == null || context.isEmpty()) {
      throw new IllegalArgumentException("Credential context must not be null or empty");
    }

    byte[] info = context.getBytes(StandardCharsets.UTF_8);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(stretchedKey, "HmacSHA256"));
    mac.update(info);
    mac.update((byte) 0x01); // counter byte for single-block expand

    byte[] okm = mac.doFinal();
    try {
      // SecretKeySpec clones the byte array internally (JCE design).
      return new SecretKeySpec(okm, "AES");
    } finally {
      Arrays.fill(okm, (byte) 0);
    }
  }
}
