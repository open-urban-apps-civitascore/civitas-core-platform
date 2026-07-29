/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.credentials;

import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

/**
 * Decrypts {@code ENC(...)} credentials at deploy time, reusing the shared crypto utilities. The
 * decrypted plaintext is destined only for NiFi <em>sensitive</em> component properties pushed over
 * REST — never the uploaded snapshot, environment variables, or a Parameter Context that a flow
 * could read via Expression Language. The stretched key is held only for the resolver's lifetime
 * and zeroed on {@link #close()}.
 */
public class CredentialResolver implements AutoCloseable {

  private final byte[] stretchedKey;
  private final String credentialContext;

  /**
   * Creates a resolver using the standard datasource credential context.
   *
   * @param stretchedKey the stretched master key (empty array if no key is configured)
   */
  public CredentialResolver(byte[] stretchedKey) {
    this(stretchedKey, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT);
  }

  /**
   * Creates a resolver using an explicit credential context.
   *
   * @param stretchedKey the stretched master key (empty array if no key is configured)
   * @param credentialContext the HKDF credential context
   */
  public CredentialResolver(byte[] stretchedKey, String credentialContext) {
    this.stretchedKey = stretchedKey.clone();
    this.credentialContext = credentialContext;
  }

  /**
   * Decrypts every {@code ENC(...)} value in the given property map. Maps without encrypted values
   * are returned unchanged.
   *
   * @param properties the (possibly encrypted) properties
   * @return a map with all credentials decrypted
   * @throws FatalAdapterException if a key is required but absent, or decryption fails
   */
  public Map<String, Object> decrypt(Map<String, Object> properties) throws FatalAdapterException {
    if (properties == null || properties.isEmpty()) {
      return Map.of();
    }
    if (!CredentialDecryptor.containsEncryptedValues(properties)) {
      return properties;
    }
    if (stretchedKey.length == 0) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR,
          "encrypted credentials present but CIVITAS_MASTER_KEY is not configured");
    }
    try {
      return CredentialDecryptor.decryptMapValues(properties, stretchedKey, credentialContext);
    } catch (GeneralSecurityException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, e, "credential decryption failed");
    }
  }

  @Override
  public void close() {
    Arrays.fill(stretchedKey, (byte) 0);
  }
}
