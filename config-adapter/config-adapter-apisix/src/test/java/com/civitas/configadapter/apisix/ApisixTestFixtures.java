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

import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.RouteConfigValue;
import com.civitas.configadapter.model.apisix.UpstreamNodes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Shared test fixtures for ApisixAdapter unit and integration tests. */
final class ApisixTestFixtures {

  private ApisixTestFixtures() {}

  /** Default upstream configuration map used for createUpstreamDirectly() in integration tests. */
  static Map<String, Object> defaultUpstreamConfig() {
    return Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
  }

  /** Default upstream ConfigValue for adapter tests. */
  static ApisixConfigValue defaultUpstreamConfigValue() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setType("roundrobin");
    value.setNodes(UpstreamNodes.ofMap(Map.of("backend1:8080", 1)));
    return value;
  }

  // ---- Private helpers ----

  private static Metadata fixedMetadata() {
    return new Metadata(
        "msg-123", OffsetDateTime.now(), "test-source", "corr-123", "v1.0.0", "test-result-topic");
  }

  private static Metadata randomMetadata() {
    return new Metadata(
        UUID.randomUUID().toString(),
        OffsetDateTime.now(),
        "test.source",
        UUID.randomUUID().toString(),
        "1.0",
        "result.topic");
  }

  private static ConfigEvent createEvent(
      Metadata metadata, String target, Operation op, ConfigValue value, String configPath) {
    Payload payload = new Payload("apisix", target, op, new Config(configPath, value));
    return new ConfigEvent(metadata, payload);
  }

  // ---- Fixed-ID factories (unit tests) ----

  /**
   * Creates an upstream {@link ConfigEvent} with fixed metadata IDs ({@code "msg-123"} / {@code
   * "corr-123"}). Use in unit tests where deterministic IDs simplify assertion.
   */
  static ConfigEvent upstreamEvent(Operation op, String target, ApisixConfigValue val) {
    return createEvent(fixedMetadata(), target, op, val, null);
  }

  /**
   * Creates a route {@link ConfigEvent} with fixed metadata IDs ({@code "msg-123"} / {@code
   * "corr-123"}). Use in unit tests where deterministic IDs simplify assertion.
   */
  static ConfigEvent routeEvent(Operation op, String target, RouteConfigValue val) {
    return createEvent(fixedMetadata(), target, op, val, null);
  }

  // ---- Random-ID factories (integration tests) ----

  /**
   * Creates an upstream {@link ConfigEvent} with random UUID metadata IDs. Use in integration tests
   * to avoid ID collisions between concurrent test runs.
   */
  static ConfigEvent upstreamEventRandomIds(String target, Operation op, ApisixConfigValue val) {
    return createEvent(randomMetadata(), target, op, val, target);
  }

  /**
   * Creates a route {@link ConfigEvent} with random UUID metadata IDs. Use in integration tests to
   * avoid ID collisions between concurrent test runs.
   */
  static ConfigEvent routeEventRandomIds(String target, Operation op, RouteConfigValue val) {
    return createEvent(randomMetadata(), target, op, val, target);
  }
}
