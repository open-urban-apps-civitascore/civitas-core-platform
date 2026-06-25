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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialResolverTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  private String enc(String plaintext, byte[] key) throws Exception {
    return "ENC("
        + CredentialEncryptor.encrypt(
            plaintext, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
        + ")";
  }

  @Test
  void decryptsEncryptedValuesAndPassesThroughPlainOnes() throws Exception {
    byte[] key = stretchedKey();
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("password", enc("s3cr3t", key));
    props.put("host", "db.example.org");

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      Map<String, Object> decrypted = resolver.decrypt(props);
      assertEquals("s3cr3t", decrypted.get("password"));
      assertEquals("db.example.org", decrypted.get("host"));
    }
  }

  @Test
  void mapWithoutEncryptedValuesIsReturnedUnchanged() throws Exception {
    Map<String, Object> props = Map.of("host", "db", "port", 5432);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      assertSame(props, resolver.decrypt(props));
    }
  }

  @Test
  void missingKeyButEncryptedValuesIsFatal() throws Exception {
    byte[] key = stretchedKey();
    Map<String, Object> props = Map.of("password", enc("x", key));

    try (CredentialResolver resolver = new CredentialResolver(new byte[0])) {
      assertThrows(FatalAdapterException.class, () -> resolver.decrypt(props));
    }
  }

  @Test
  void emptyPropsYieldEmptyMap() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      assertEquals(Map.of(), resolver.decrypt(Map.of()));
    }
  }
}
