/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.delegate;

import java.util.Map;

/**
 * Maps inter-step field names that differ between adapter handlers. For example, FROST produces
 * {@code baseUrl} but APISIX expects {@code upstreamUrl} and Redpanda expects {@code targetUrl}.
 *
 * <p>This replicates the mapping logic from {@code SagaPayloadBuilder.applyAdapterMappings()} in
 * the custom orchestrator. Extracted to its own class to keep delegates adapter-agnostic (SRP).
 */
final class AdapterFieldMappings {

  private static final String BASE_URL = "baseUrl";

  private AdapterFieldMappings() {}

  /**
   * Applies adapter-specific field mappings to the payload.
   *
   * @param payload the mutable payload map
   * @param adapter the target adapter name
   */
  static void apply(Map<String, Object> payload, String adapter) {
    switch (adapter) {
      case "apisix" -> {
        if (payload.containsKey(BASE_URL) && !payload.containsKey("upstreamUrl")) {
          payload.put("upstreamUrl", payload.get(BASE_URL));
        }
      }
      case "redpanda" -> {
        if (payload.containsKey(BASE_URL) && !payload.containsKey("targetUrl")) {
          payload.put("targetUrl", payload.get(BASE_URL));
        }
      }
      default -> {}
    }
  }
}
