/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineSerializerTest {

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

  @Test
  @DisplayName("throws FatalAdapterException when ENC() values present but master key is empty")
  void toYaml_encryptedValueWithEmptyKey_throwsFatalAdapterException() {
    PipelineSerializer serializer = new PipelineSerializer(new byte[0]);

    assertThrows(
        FatalAdapterException.class,
        () -> serializer.toYaml(Map.of("password", "ENC(encrypted-data)"), "test-pipeline"));
  }

  @Test
  @DisplayName("succeeds when no ENC() values and master key is empty")
  void toYaml_noEncryptedValueWithEmptyKey_succeeds() {
    PipelineSerializer serializer = new PipelineSerializer(new byte[0]);

    assertDoesNotThrow(() -> serializer.toYaml(Map.of("input", Map.of()), "test-pipeline"));
  }

  @Test
  @DisplayName("produces valid YAML from a simple map")
  void toYaml_simpleMap_producesValidYaml() throws FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);

    String yaml = serializer.toYaml(Map.of("input", Map.of("type", "generate")), "test-pipeline");

    assertTrue(yaml.contains("input"));
    assertTrue(yaml.contains("type"));
    assertTrue(yaml.contains("generate"));
  }

  @Test
  @DisplayName("null map produces empty YAML document")
  void toYaml_nullMap_producesEmptyYaml() throws FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);

    String yaml = serializer.toYaml(null, "test-pipeline");

    assertTrue(yaml.contains("{}") || yaml.trim().isEmpty());
  }

  @Test
  @DisplayName("close() → subsequent toYaml() throws IllegalStateException")
  void close_thenToYaml_throwsIllegalStateException() {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);
    serializer.close();

    assertThrows(
        IllegalStateException.class,
        () -> serializer.toYaml(Map.of("input", Map.of()), "test-pipeline"));
  }

  @Test
  @DisplayName("invalid Base64 in ENC() value → FatalAdapterException")
  void toYaml_invalidBase64InEncValue_throwsFatalAdapterException() {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("password", "ENC(not!!!valid-base64)");

    assertThrows(FatalAdapterException.class, () -> serializer.toYaml(data, "test-pipeline"));
  }

  @Test
  @DisplayName("decrypts ENC() values with valid key and context")
  void toYaml_encryptedValue_decryptsSuccessfully()
      throws GeneralSecurityException, FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);
    String encrypted = CredentialEncryptor.encrypt("my-password", STRETCHED_KEY, "test-pipeline");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("password", "ENC(" + encrypted + ")");
    data.put("username", "admin");

    String yaml = serializer.toYaml(data, "test-pipeline");

    assertTrue(yaml.contains("my-password"));
    assertTrue(yaml.contains("admin"));
  }

  @Test
  @DisplayName(
      "decrypts credentials encrypted by portal-backend using DATASOURCE_CREDENTIAL_CONTEXT")
  void toYaml_portalBackendEncryptedValue_decryptsWithSharedContext()
      throws GeneralSecurityException, FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);

    // Encrypt with the same context that portal-backend uses (EncryptionConfig.CREDENTIAL_CONTEXT)
    String encrypted =
        CredentialEncryptor.encrypt(
            "my-password", STRETCHED_KEY, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("password", "ENC(" + encrypted + ")");

    // Decrypt with the shared constant — must match portal-backend's encryption context
    String yaml = serializer.toYaml(data, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT);

    assertTrue(yaml.contains("my-password"));
  }

  @Test
  @DisplayName("decryption fails when credential context does not match encryption context")
  void toYaml_contextMismatch_throwsFatalAdapterException() throws GeneralSecurityException {
    PipelineSerializer serializer = new PipelineSerializer(STRETCHED_KEY);

    String encrypted =
        CredentialEncryptor.encrypt(
            "my-password", STRETCHED_KEY, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("password", "ENC(" + encrypted + ")");

    // Using a different context (e.g. a pipeline ID) must fail
    assertThrows(FatalAdapterException.class, () -> serializer.toYaml(data, "some-pipeline-id"));
  }
}
