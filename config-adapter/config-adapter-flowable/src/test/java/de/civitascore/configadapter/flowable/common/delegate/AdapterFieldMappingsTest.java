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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdapterFieldMappingsTest {

  @Test
  void apply_apisixAdapter_mapsBaseUrlToUpstreamUrl() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("baseUrl", "http://frost/v1.1");

    AdapterFieldMappings.apply(payload, "apisix");

    assertEquals("http://frost/v1.1", payload.get("upstreamUrl"));
  }

  @Test
  void apply_redpandaAdapter_mapsBaseUrlToTargetUrl() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("baseUrl", "http://frost/v1.1");

    AdapterFieldMappings.apply(payload, "redpanda");

    assertEquals("http://frost/v1.1", payload.get("targetUrl"));
  }

  @Test
  void apply_frostAdapter_doesNotModifyPayload() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("baseUrl", "http://frost/v1.1");

    AdapterFieldMappings.apply(payload, "frost");

    assertNull(payload.get("upstreamUrl"));
    assertNull(payload.get("targetUrl"));
  }

  @Test
  void apply_apisixAdapter_doesNotOverrideExistingUpstreamUrl() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("baseUrl", "http://frost/v1.1");
    payload.put("upstreamUrl", "http://custom/url");

    AdapterFieldMappings.apply(payload, "apisix");

    assertEquals("http://custom/url", payload.get("upstreamUrl"));
  }

  @Test
  void apply_payloadWithoutBaseUrl_doesNothing() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("datasetId", "ds-123");

    AdapterFieldMappings.apply(payload, "apisix");

    assertFalse(payload.containsKey("upstreamUrl"));
  }

  @Test
  void apply_unknownAdapter_doesNotModifyPayload() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("baseUrl", "http://frost/v1.1");
    int originalSize = payload.size();

    AdapterFieldMappings.apply(payload, "unknown-adapter");

    assertEquals(originalSize, payload.size());
  }
}
