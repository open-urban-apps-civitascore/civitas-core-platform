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
package com.civitas.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the health check class hierarchy: {@link HealthCheck} and its nested types. */
class HealthCheckTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  // ===== HealthCheck (top-level) =====

  @Test
  void healthCheck_withActiveOnly_shouldSerializeCorrectly() {
    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("http");
    active.setHttpPath("/health");

    HealthCheck checks = new HealthCheck();
    checks.setActive(active);

    Map<String, Object> map = checks.toApiMap();
    assertNotNull(map.get("active"));
    assertNull(map.get("passive"));
  }

  @Test
  void healthCheck_withPassiveOnly_shouldSerializeCorrectly() {
    PassiveHealthCheck passive = new PassiveHealthCheck();
    passive.setType("http");

    HealthCheck checks = new HealthCheck();
    checks.setPassive(passive);

    Map<String, Object> map = checks.toApiMap();
    assertNull(map.get("active"));
    assertNotNull(map.get("passive"));
  }

  @Test
  void healthCheck_withBoth_shouldSerializeFull() {
    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("http");
    PassiveHealthCheck passive = new PassiveHealthCheck();
    passive.setType("http");

    HealthCheck checks = new HealthCheck();
    checks.setActive(active);
    checks.setPassive(passive);

    Map<String, Object> map = checks.toApiMap();
    assertNotNull(map.get("active"));
    assertNotNull(map.get("passive"));
  }

  @Test
  void healthCheck_empty_shouldReturnEmptyMap() {
    HealthCheck checks = new HealthCheck();
    Map<String, Object> map = checks.toApiMap();
    assertTrue(map.isEmpty());
  }

  // ===== ActiveHealthCheck =====

  @Test
  void activeHealthCheck_allFields_shouldSerializeCorrectly() {
    HealthyCondition healthy = new HealthyCondition();
    healthy.setInterval(2);
    healthy.setSuccesses(1);

    UnhealthyCondition unhealthy = new UnhealthyCondition();
    unhealthy.setInterval(1);
    unhealthy.setHttpFailures(3);

    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("http");
    active.setHttpPath("/health");
    active.setTimeout(5);
    active.setConcurrency(10);
    active.setHealthy(healthy);
    active.setUnhealthy(unhealthy);

    Map<String, Object> map = active.toApiMap();
    assertEquals("http", map.get("type"));
    assertEquals("/health", map.get("http_path"));
    assertEquals(5, map.get("timeout"));
    assertEquals(10, map.get("concurrency"));
    assertNotNull(map.get("healthy"));
    assertNotNull(map.get("unhealthy"));
  }

  @Test
  void activeHealthCheck_httpPathDefault_shouldHandleNull() {
    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("tcp");

    Map<String, Object> map = active.toApiMap();
    assertEquals("tcp", map.get("type"));
    assertNull(map.get("http_path"));
  }

  // ===== PassiveHealthCheck =====

  @Test
  void passiveHealthCheck_allFields_shouldSerializeCorrectly() {
    HealthyCondition healthy = new HealthyCondition();
    healthy.setHttpStatuses(List.of(200, 201));
    healthy.setSuccesses(3);

    UnhealthyCondition unhealthy = new UnhealthyCondition();
    unhealthy.setHttpStatuses(List.of(500));
    unhealthy.setHttpFailures(3);

    PassiveHealthCheck passive = new PassiveHealthCheck();
    passive.setType("http");
    passive.setHealthy(healthy);
    passive.setUnhealthy(unhealthy);

    Map<String, Object> map = passive.toApiMap();
    assertEquals("http", map.get("type"));
    assertNotNull(map.get("healthy"));
    assertNotNull(map.get("unhealthy"));
  }

  // ===== HealthyCondition =====

  @Test
  void healthyCondition_allFields_shouldSerializeCorrectly() {
    HealthyCondition healthy = new HealthyCondition();
    healthy.setInterval(2);
    healthy.setSuccesses(1);
    healthy.setHttpStatuses(List.of(200, 302));

    Map<String, Object> map = healthy.toApiMap();
    assertEquals(2, map.get("interval"));
    assertEquals(1, map.get("successes"));
    assertEquals(List.of(200, 302), map.get("http_statuses"));
  }

  @Test
  void healthyCondition_noFields_shouldReturnEmptyMap() {
    HealthyCondition healthy = new HealthyCondition();
    Map<String, Object> map = healthy.toApiMap();
    assertTrue(map.isEmpty());
  }

  // ===== UnhealthyCondition =====

  @Test
  void unhealthyCondition_allFields_shouldSerializeCorrectly() {
    UnhealthyCondition unhealthy = new UnhealthyCondition();
    unhealthy.setInterval(1);
    unhealthy.setHttpFailures(3);
    unhealthy.setTcpFailures(2);
    unhealthy.setTimeouts(5);
    unhealthy.setHttpStatuses(List.of(429, 500, 503));

    Map<String, Object> map = unhealthy.toApiMap();
    assertEquals(1, map.get("interval"));
    assertEquals(3, map.get("http_failures"));
    assertEquals(2, map.get("tcp_failures"));
    assertEquals(5, map.get("timeouts"));
    assertEquals(List.of(429, 500, 503), map.get("http_statuses"));
  }

  @Test
  void unhealthyCondition_noFields_shouldReturnEmptyMap() {
    UnhealthyCondition unhealthy = new UnhealthyCondition();
    Map<String, Object> map = unhealthy.toApiMap();
    assertTrue(map.isEmpty());
  }

  // ===== JSON round-trip =====

  @Test
  @SuppressWarnings("unchecked")
  void jsonDeserialization_fromApisixExample_shouldParseComplete() throws Exception {
    String json =
        """
                {
                  "active": {
                    "type": "http",
                    "http_path": "/health",
                    "timeout": 5,
                    "concurrency": 10,
                    "healthy": {
                      "interval": 2,
                      "successes": 1,
                      "http_statuses": [200, 302]
                    },
                    "unhealthy": {
                      "interval": 1,
                      "http_failures": 3,
                      "tcp_failures": 2,
                      "timeouts": 5,
                      "http_statuses": [429, 500, 503]
                    }
                  },
                  "passive": {
                    "type": "http",
                    "healthy": {
                      "http_statuses": [200, 201],
                      "successes": 3
                    },
                    "unhealthy": {
                      "http_statuses": [500],
                      "http_failures": 3
                    }
                  }
                }
                """;

    HealthCheck checks = objectMapper.readValue(json, HealthCheck.class);

    // Active checks
    assertNotNull(checks.getActive());
    assertEquals("http", checks.getActive().getType());
    assertEquals("/health", checks.getActive().getHttpPath());
    assertEquals(5, checks.getActive().getTimeout());
    assertEquals(10, checks.getActive().getConcurrency());

    // Active healthy
    assertNotNull(checks.getActive().getHealthy());
    assertEquals(2, checks.getActive().getHealthy().getInterval());
    assertEquals(1, checks.getActive().getHealthy().getSuccesses());
    assertEquals(List.of(200, 302), checks.getActive().getHealthy().getHttpStatuses());

    // Active unhealthy
    assertNotNull(checks.getActive().getUnhealthy());
    assertEquals(1, checks.getActive().getUnhealthy().getInterval());
    assertEquals(3, checks.getActive().getUnhealthy().getHttpFailures());
    assertEquals(2, checks.getActive().getUnhealthy().getTcpFailures());
    assertEquals(5, checks.getActive().getUnhealthy().getTimeouts());
    assertEquals(List.of(429, 500, 503), checks.getActive().getUnhealthy().getHttpStatuses());

    // Passive checks
    assertNotNull(checks.getPassive());
    assertEquals("http", checks.getPassive().getType());

    // Passive healthy
    assertNotNull(checks.getPassive().getHealthy());
    assertEquals(List.of(200, 201), checks.getPassive().getHealthy().getHttpStatuses());
    assertEquals(3, checks.getPassive().getHealthy().getSuccesses());

    // Passive unhealthy
    assertNotNull(checks.getPassive().getUnhealthy());
    assertEquals(List.of(500), checks.getPassive().getUnhealthy().getHttpStatuses());
    assertEquals(3, checks.getPassive().getUnhealthy().getHttpFailures());
  }

  @Test
  void jsonRoundTrip_fullConfig_shouldPreserveStructure() throws Exception {
    HealthyCondition activeHealthy = new HealthyCondition();
    activeHealthy.setInterval(2);
    activeHealthy.setSuccesses(1);

    UnhealthyCondition activeUnhealthy = new UnhealthyCondition();
    activeUnhealthy.setInterval(1);
    activeUnhealthy.setHttpFailures(3);

    ActiveHealthCheck active = new ActiveHealthCheck();
    active.setType("http");
    active.setHttpPath("/health");
    active.setTimeout(5);
    active.setHealthy(activeHealthy);
    active.setUnhealthy(activeUnhealthy);

    HealthCheck original = new HealthCheck();
    original.setActive(active);

    String json = objectMapper.writeValueAsString(original);
    HealthCheck deserialized = objectMapper.readValue(json, HealthCheck.class);

    assertEquals(original, deserialized);
  }

  @Test
  void activeHealthCheck_jsonRoundTrip_withIntegerTimeout_shouldPreserveValue() throws Exception {
    ActiveHealthCheck original = new ActiveHealthCheck();
    original.setType("http");
    original.setTimeout(5);

    String json = objectMapper.writeValueAsString(original);
    ActiveHealthCheck deserialized = objectMapper.readValue(json, ActiveHealthCheck.class);

    assertEquals(original, deserialized);
  }

  @Test
  void activeHealthCheck_jsonRoundTrip_withDecimalTimeout_shouldPreserveValue() throws Exception {
    ActiveHealthCheck original = new ActiveHealthCheck();
    original.setType("http");
    original.setTimeout(0.5);

    String json = objectMapper.writeValueAsString(original);
    ActiveHealthCheck deserialized = objectMapper.readValue(json, ActiveHealthCheck.class);

    assertEquals(0.5, deserialized.getTimeout().doubleValue());
  }

  // ===== Number type semantics (Integer vs Double) =====

  @Test
  void activeHealthCheck_integerVsDoubleTimeout_shouldNotBeEqual() {
    ActiveHealthCheck withInteger = new ActiveHealthCheck();
    withInteger.setType("http");
    withInteger.setTimeout(5);

    ActiveHealthCheck withDouble = new ActiveHealthCheck();
    withDouble.setType("http");
    withDouble.setTimeout(5.0);

    assertInstanceOf(Integer.class, withInteger.getTimeout());
    assertInstanceOf(Double.class, withDouble.getTimeout());
    assertNotEquals(
        withInteger, withDouble, "Integer(5) and Double(5.0) are not equal via Number.equals()");
  }

  @Test
  void activeHealthCheck_jsonRoundTrip_integerVsDoubleTimeout_shouldPreserveType()
      throws Exception {
    String intJson =
        """
        {"type": "http", "timeout": 5}
        """;
    String doubleJson =
        """
        {"type": "http", "timeout": 5.0}
        """;

    ActiveHealthCheck fromInt = objectMapper.readValue(intJson, ActiveHealthCheck.class);
    ActiveHealthCheck fromDouble = objectMapper.readValue(doubleJson, ActiveHealthCheck.class);

    assertInstanceOf(Integer.class, fromInt.getTimeout());
    assertInstanceOf(Double.class, fromDouble.getTimeout());
    assertNotEquals(
        fromInt,
        fromDouble,
        "JSON integer 5 deserializes to Integer, JSON 5.0 deserializes to Double");
  }

  // ===== Additional properties =====

  @Test
  void additionalProperties_onAllClasses_shouldBeUnmodifiable() {
    HealthCheck hc = new HealthCheck();
    hc.handleUnknownProperty("extra", "val");
    assertThrows(
        UnsupportedOperationException.class, () -> hc.getAdditionalProperties().put("k", "v"));

    ActiveHealthCheck ahc = new ActiveHealthCheck();
    ahc.handleUnknownProperty("extra", "val");
    assertThrows(
        UnsupportedOperationException.class, () -> ahc.getAdditionalProperties().put("k", "v"));

    PassiveHealthCheck phc = new PassiveHealthCheck();
    phc.handleUnknownProperty("extra", "val");
    assertThrows(
        UnsupportedOperationException.class, () -> phc.getAdditionalProperties().put("k", "v"));

    HealthyCondition hyCond = new HealthyCondition();
    hyCond.handleUnknownProperty("extra", "val");
    assertThrows(
        UnsupportedOperationException.class, () -> hyCond.getAdditionalProperties().put("k", "v"));

    UnhealthyCondition uhCond = new UnhealthyCondition();
    uhCond.handleUnknownProperty("extra", "val");
    assertThrows(
        UnsupportedOperationException.class, () -> uhCond.getAdditionalProperties().put("k", "v"));
  }
}
