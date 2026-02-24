/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
import java.util.Map;

/** Shared test fixtures for FROST adapter tests. */
final class FrostTestFixtures {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
  private abstract static class NoTypeInfoMixin {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper().addMixIn(ConfigValue.class, NoTypeInfoMixin.class);

  private FrostTestFixtures() {}

  /**
   * Converts a raw map to a {@link FrostConfigValue} using Jackson's {@code convertValue}. This
   * approach (instead of a direct constructor call) ensures the same deserialization behavior as
   * production code, including {@code additionalProperties} capture for unknown keys.
   */
  static FrostConfigValue buildFrostConfigValue(Map<String, Object> map) {
    if (map == null) return null;
    return MAPPER.convertValue(map, FrostConfigValue.class);
  }
}
