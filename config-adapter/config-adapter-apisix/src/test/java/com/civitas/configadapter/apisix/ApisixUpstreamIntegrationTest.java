/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.apisix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.UpstreamNodes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests for ApisixAdapter upstream operations. */
class ApisixUpstreamIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createUpstream() throws Exception {
    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");
    upstreamConfig.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 1)));

    ConfigEvent event =
        ApisixTestFixtures.upstreamEventRandomIds("upstreams", Operation.CREATE, upstreamConfig);

    adapter.processConfigEvent(Topics.BACKEND_CREATED.toString(), event);

    awaitUpstreamInApisix(2);
  }

  @Test
  void updateUpstream() throws Exception {
    String upstreamId = "test-upstream-update";
    createUpstreamDirectly(upstreamId, ApisixTestFixtures.defaultUpstreamConfig());

    ApisixConfigValue updatedConfig = new ApisixConfigValue();
    updatedConfig.setType("roundrobin");
    updatedConfig.setNodes(
        UpstreamNodes.ofMap(Map.of("backend1:8080", 2, "backend2:8080", 1, "backend3:8080", 1)));

    ConfigEvent event =
        ApisixTestFixtures.upstreamEventRandomIds(
            "upstreams/" + upstreamId, Operation.UPDATE, updatedConfig);

    adapter.processConfigEvent(Topics.BACKEND_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode upstream = getUpstreamFromApisix(upstreamId);
              assertNotNull(upstream);
              JsonNode nodes = upstream.get("value").get("nodes");
              assertEquals(3, nodes.size());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void deleteUpstream() throws Exception {
    String upstreamId = "test-upstream-delete";
    createUpstreamDirectly(upstreamId, ApisixTestFixtures.defaultUpstreamConfig());

    JsonNode upstream = getUpstreamFromApisix(upstreamId);
    assertNotNull(upstream, "Upstream should exist before delete");

    ConfigEvent event =
        ApisixTestFixtures.upstreamEventRandomIds(
            "upstreams/" + upstreamId, Operation.DELETE, (ApisixConfigValue) null);

    adapter.processConfigEvent(Topics.BACKEND_DELETED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              try {
                getUpstreamFromApisix(upstreamId);
                fail("Upstream should have been deleted but still exists");
              } catch (Exception e) {
                assertTrue(e.getMessage().contains("404") || e.getMessage().contains("not found"));
              }
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void handleInvalidUpstreamConfiguration() {
    ApisixConfigValue invalidConfig = new ApisixConfigValue();
    invalidConfig.setType("invalid-type");

    ConfigEvent event =
        ApisixTestFixtures.upstreamEventRandomIds("upstreams", Operation.CREATE, invalidConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.BACKEND_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_UPSTREAM_ERROR, exception.getErrorCode());
  }
}
