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

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Package-private utilities and constants shared between {@link CredentialDecryptor} and {@link
 * CredentialEncryptor}.
 */
final class CryptoUtils {

  static final String ALGORITHM = "AES/GCM/NoPadding";
  static final int GCM_TAG_LENGTH_BITS = 128;
  static final int GCM_IV_LENGTH_BYTES = 12;
  static final int PBKDF2_ITERATIONS = 600_000;
  static final int KEY_LENGTH_BITS = 256;
  static final int MIN_SALT_LENGTH_BYTES = 16;
  static final String ENC_PREFIX = "ENC(";
  static final String ENC_SUFFIX = ")";

  private CryptoUtils() {}

  static boolean isEncrypted(String value) {
    return value.startsWith(ENC_PREFIX)
        && value.endsWith(ENC_SUFFIX)
        && value.length() > ENC_PREFIX.length() + ENC_SUFFIX.length();
  }

  /**
   * Derives an AES-256 key from the master key and salt using PBKDF2WithHmacSHA256.
   *
   * @param masterKey the master key bytes
   * @param salt the salt bytes
   * @return the derived SecretKey
   * @throws GeneralSecurityException if key derivation fails
   */
  static SecretKey deriveKey(byte[] masterKey, byte[] salt) throws GeneralSecurityException {
    if (masterKey == null || masterKey.length == 0) {
      throw new IllegalArgumentException("Master key must not be null or empty");
    }
    if (salt == null || salt.length < MIN_SALT_LENGTH_BYTES) {
      throw new IllegalArgumentException(
          "Salt must be at least "
              + MIN_SALT_LENGTH_BYTES
              + " bytes, got "
              + (salt == null ? "null" : salt.length));
    }
    char[] keyChars = bytesToHexChars(masterKey);
    PBEKeySpec spec = null;
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      spec = new PBEKeySpec(keyChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
      byte[] derivedKey = factory.generateSecret(spec).getEncoded();
      // SecretKeySpec clones the byte array internally (JCE design).
      // We wipe our copy; the JCE-held copy cannot be cleared — known JDK limitation.
      SecretKeySpec secretKey = new SecretKeySpec(derivedKey, "AES");
      Arrays.fill(derivedKey, (byte) 0);
      return secretKey;
    } catch (RuntimeException e) {
      throw new GeneralSecurityException("Key derivation failed", e);
    } finally {
      if (spec != null) {
        spec.clearPassword();
      }
      Arrays.fill(keyChars, '\0');
    }
  }

  private static char[] bytesToHexChars(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      hexChars[i * 2] = Character.forDigit(v >>> 4, 16);
      hexChars[i * 2 + 1] = Character.forDigit(v & 0x0F, 16);
    }
    return hexChars;
  }
}
