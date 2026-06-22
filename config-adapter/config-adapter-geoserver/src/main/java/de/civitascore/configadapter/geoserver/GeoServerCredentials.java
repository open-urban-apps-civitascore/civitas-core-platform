/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * Credential handling for the GeoServer adapter, following the project credential convention:
 * credentials may be supplied encrypted as {@code ENC(...)} values and are decrypted
 * in-memory only at the moment they are needed. Plaintext values pass through unchanged for
 * backward compatibility.
 *
 * <p>The master key is read from the {@value #MASTER_KEY_ENV} environment variable. Encrypted
 * values must have been produced with the {@link CredentialEncryptor#DATASOURCE_CREDENTIAL_CONTEXT}
 * context.
 */
final class GeoServerCredentials {

  /** Environment variable holding the hex-encoded master key shared across config adapters. */
  static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private GeoServerCredentials() {}

  /**
   * Decrypts an {@code ENC(...)}-wrapped credential, or returns the value unchanged if it is null
   * or plaintext.
   *
   * @param value the (possibly encrypted) credential value
   * @param stretchedKey the stretched master key; an empty array means no key is configured
   * @return the plaintext credential
   * @throws IllegalStateException if an encrypted value cannot be decrypted
   */
  static String decrypt(String value, byte[] stretchedKey) {
    if (value == null) {
      return null;
    }
    try {
      Map<String, Object> decrypted =
          CredentialDecryptor.decryptMapValues(
              Map.of("value", value),
              stretchedKey,
              CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT);
      return (String) decrypted.get("value");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt GeoServer credential", e);
    }
  }
}
