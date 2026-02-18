/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.UpstreamNodes;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter upstream operations. */
class ApisixAdapterUpstreamTest extends AbstractApisixAdapterTest {

  @Test
  void testCreateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(200, "{\"success\":true}");

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");
    upstreamConfig.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 1, "backend2:8080", 1)));
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(Operation.CREATE, "upstreams", upstreamConfig);

    adapter.processConfigEvent("de.civitascore.api.backend.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX upstream created successfully", result.message());
  }

  @Test
  void testCreateFailureHttpError() {
    givenMockPostReturns(400, "{\"error\":\"Invalid configuration\"}");

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("invalid");
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(Operation.CREATE, "upstreams", upstreamConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.backend.created", event));

    assertEquals(AdapterErrorCode.APISIX_UPSTREAM_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 400"));
  }

  @Test
  void testCreateFailureException() {
    when(mockBuilder.post(any(Entity.class)))
        .thenThrow(new ProcessingException("Connection refused"));

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(Operation.CREATE, "upstreams", upstreamConfig);

    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.backend.created", event));

    assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
  }

  @Test
  void testUpdateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"success\":true}");

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");
    upstreamConfig.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 2, "backend2:8080", 1)));
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(
            Operation.UPDATE, "upstreams/test-upstream-id", upstreamConfig);

    adapter.processConfigEvent("de.civitascore.api.backend.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX upstream updated successfully", result.message());
  }

  @Test
  void testDeleteSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockDeleteReturns(200, "{\"success\":true}");

    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(
            Operation.DELETE, "upstreams/test-upstream-id", (ApisixConfigValue) null);

    adapter.processConfigEvent("de.civitascore.api.backend.deleted", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX upstream deleted successfully", result.message());
  }

  @Test
  void testUnknownResourceType() {
    ApisixConfigValue config = new ApisixConfigValue();
    config.setType("roundrobin");
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(Operation.CREATE, "services/test-service", config);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.backend.created", event));

    assertEquals(AdapterErrorCode.INVALID_RESOURCE_TYPE, exception.getErrorCode());
  }

  @Test
  void testWithoutEventPublisher() throws FatalAdapterException, RetryableAdapterException {
    adapter.setEventPublisher(null);
    givenMockPostReturns(200, "{\"success\":true}");

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");
    ConfigEvent event =
        ApisixTestFixtures.upstreamEvent(Operation.CREATE, "upstreams", upstreamConfig);

    // Should not throw even without publisher
    adapter.processConfigEvent("de.civitascore.api.backend.created", event);
  }

  @Test
  void testWithNullResultTopic() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(200, "{\"success\":true}");

    ApisixConfigValue upstreamConfig = new ApisixConfigValue();
    upstreamConfig.setType("roundrobin");

    Metadata metadata =
        new Metadata("msg-123", OffsetDateTime.now(), "test-source", "corr-123", "v1.0.0", null);
    Payload payload =
        new Payload("apisix", "upstreams", Operation.CREATE, new Config(null, upstreamConfig));
    ConfigEvent event = new ConfigEvent(metadata, payload);

    adapter.processConfigEvent("de.civitascore.api.backend.created", event);

    verify(mockPublisher, never()).publish(any(String.class), any(ConfigResultEvent.class));
  }
}
