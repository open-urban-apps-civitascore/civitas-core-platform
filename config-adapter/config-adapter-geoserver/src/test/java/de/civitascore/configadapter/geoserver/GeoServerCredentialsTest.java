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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.Test;

class GeoServerCredentialsTest {

  private static final byte[] MASTER_KEY = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
    0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
  };

  private static final byte[] STRETCHED_KEY;

  static {
    try {
      STRETCHED_KEY = CryptoKeyLoader.stretchMasterKey(MASTER_KEY);
    } catch (GeneralSecurityException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static String enc(String plaintext) throws GeneralSecurityException {
    return "ENC("
        + CredentialEncryptor.encrypt(
            plaintext, STRETCHED_KEY, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
        + ")";
  }

  @Test
  void decryptsEncryptedValue() throws GeneralSecurityException {
    assertEquals("s3cret", GeoServerCredentials.decrypt(enc("s3cret"), STRETCHED_KEY));
  }

  @Test
  void passesPlaintextThrough() {
    assertEquals("plain-password", GeoServerCredentials.decrypt("plain-password", STRETCHED_KEY));
  }

  @Test
  void passesPlaintextThroughEvenWithoutKey() {
    assertEquals("plain-password", GeoServerCredentials.decrypt("plain-password", new byte[0]));
  }

  @Test
  void returnsNullForNull() {
    assertNull(GeoServerCredentials.decrypt(null, STRETCHED_KEY));
  }

  @Test
  void throwsWhenEncryptedButNoKey() {
    assertThrows(
        IllegalStateException.class,
        () -> GeoServerCredentials.decrypt("ENC(not-decryptable)", new byte[0]));
  }
}
