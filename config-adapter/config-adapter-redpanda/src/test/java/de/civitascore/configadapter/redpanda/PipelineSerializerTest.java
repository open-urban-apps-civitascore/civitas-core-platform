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

import de.civitascore.configadapter.exception.FatalAdapterException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineSerializerTest {

  private static final byte[] MASTER_KEY = new byte[32];
  private static final byte[] SALT = new byte[16];

  @Test
  @DisplayName("throws FatalAdapterException when ENC() values present but master key is empty")
  void toYaml_encryptedValueWithEmptyKey_throwsFatalAdapterException() {
    PipelineSerializer serializer = new PipelineSerializer(new byte[0], new byte[0]);

    assertThrows(
        FatalAdapterException.class,
        () -> serializer.toYaml(Map.of("password", "ENC(encrypted-data)")));
  }

  @Test
  @DisplayName("succeeds when no ENC() values and master key is empty")
  void toYaml_noEncryptedValueWithEmptyKey_succeeds() {
    PipelineSerializer serializer = new PipelineSerializer(new byte[0], new byte[0]);

    assertDoesNotThrow(() -> serializer.toYaml(Map.of("input", Map.of())));
  }

  @Test
  @DisplayName("produces valid YAML from a simple map")
  void toYaml_simpleMap_producesValidYaml() throws FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(MASTER_KEY, SALT);

    String yaml = serializer.toYaml(Map.of("input", Map.of("type", "generate")));

    assertTrue(yaml.contains("input"));
    assertTrue(yaml.contains("type"));
    assertTrue(yaml.contains("generate"));
  }

  @Test
  @DisplayName("null map produces empty YAML document")
  void toYaml_nullMap_producesEmptyYaml() throws FatalAdapterException {
    PipelineSerializer serializer = new PipelineSerializer(MASTER_KEY, SALT);

    String yaml = serializer.toYaml(null);

    assertTrue(yaml.contains("{}") || yaml.trim().isEmpty());
  }
}
