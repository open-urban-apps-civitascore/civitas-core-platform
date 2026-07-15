/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrostSinkAuthTest {

  @Test
  void bindsBasicAuthAndKeepsThePasswordSecret() throws Exception {
    PlanContext out = new PlanContext();

    FrostSinkAuth.basicAuth("nifi", "secret-password").bind(out);

    assertEquals("nifi", out.sinkProperties().get(FrostSinkStage.FROST_BASIC_AUTH_USERNAME));
    assertEquals(
        "secret-password",
        out.sensitive().get(FrostSinkStage.FROST_HTTP_PROCESSOR).get("Request Password"));
    assertFalse(out.sinkProperties().containsValue("secret-password"));
  }

  @Test
  void resolvesBasicAuthThroughSharedCredentialResolver() throws Exception {
    PlanContext out = new PlanContext();

    try (CredentialResolver resolver = new CredentialResolver(new byte[0])) {
      FrostSinkAuth.fromProperties(
              resolver,
              Map.of(
                  FrostSinkAuth.BASIC_AUTH_USERNAME,
                  "nifi",
                  FrostSinkAuth.BASIC_AUTH_PASSWORD,
                  "secret-password"))
          .bind(out);
    }

    assertEquals("nifi", out.sinkProperties().get(FrostSinkStage.FROST_BASIC_AUTH_USERNAME));
    assertEquals(
        "secret-password",
        out.sensitive().get(FrostSinkStage.FROST_HTTP_PROCESSOR).get("Request Password"));
    assertFalse(out.sinkProperties().containsValue("secret-password"));
  }

  @Test
  void leavesProcessorsUnauthenticatedWhenCredentialsAreMissing() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(new byte[0])) {
      FrostSinkAuth auth = FrostSinkAuth.fromProperties(resolver, Map.of());
      PlanContext out = new PlanContext();

      auth.bind(out);

      assertTrue(out.sinkProperties().isEmpty());
      assertTrue(out.sensitive().isEmpty());
    }
  }

  @Test
  void resolvesEncryptedBasicAuthPasswordThroughSharedCredentialResolver() throws Exception {
    byte[] key =
        CryptoKeyLoader.stretchMasterKey(
            CryptoKeyLoader.hexStringToBytes(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    String encryptedPassword =
        "ENC("
            + CredentialEncryptor.encrypt(
                "secret-key", key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";
    PlanContext out = new PlanContext();

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FrostSinkAuth.fromProperties(
              resolver,
              Map.of(
                  FrostSinkAuth.BASIC_AUTH_USERNAME,
                  "nifi",
                  FrostSinkAuth.BASIC_AUTH_PASSWORD,
                  encryptedPassword))
          .bind(out);
    }

    assertEquals(
        "secret-key",
        out.sensitive().get(FrostSinkStage.FROST_HTTP_PROCESSOR).get("Request Password"));
    assertFalse(out.sinkProperties().containsValue(encryptedPassword));
  }
}
