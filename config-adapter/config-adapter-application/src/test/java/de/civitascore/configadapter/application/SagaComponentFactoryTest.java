/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.configuration.AppConfig;
import java.util.Map;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaComponentFactoryTest {

  private final SagaComponentFactory factory = new SagaComponentFactory();

  private AppConfig configWith(Map<String, String> props) {
    return new AppConfig(new MapConfiguration(props));
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("Should reject the removed legacy custom orchestrator engine")
    void customEngine_rejected() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "custom",
                  "kafka.bootstrap.servers", "localhost:9092"));

      assertThrows(
          IllegalArgumentException.class,
          () -> factory.create(config),
          "The legacy custom orchestrator was removed — 'custom' must be rejected");
    }

    @Test
    @DisplayName("Should reject unknown orchestrator.engine values instead of silently degrading")
    void unknownEngine_failsFast() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "flowabel",
                  "kafka.bootstrap.servers", "localhost:9092"));

      assertThrows(IllegalArgumentException.class, () -> factory.create(config));
    }

    @Test
    @DisplayName("Should fail-fast on the (default) flowable engine when JDBC config is missing")
    void flowableWithoutJdbcConfig_failsFast() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "flowable",
                  "kafka.bootstrap.servers", "localhost:9092"));

      assertThrows(
          IllegalStateException.class,
          () -> factory.create(config),
          "Flowable mode without JDBC config must fail-fast, not silently degrade");
    }

    @Test
    @DisplayName(
        "Should surface the real cause instead of mislabeling every init failure as a JDBC problem")
    void flowableInitFailure_surfacesRealCause() {
      // Bare config: the required saga handlers (frost/apisix) cannot initialize and are dropped,
      // so FlowableSagaOrchestrator.initialize() fails at validateRequiredHandlers — BEFORE it ever
      // touches the database. The operator-facing message must therefore reflect that real cause
      // and not steer everyone to flowable.jdbc.* (the #1368 debugging trap where a missing
      // apisix.api.host surfaced as a bogus "check flowable.jdbc" error).
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "flowable",
                  "kafka.bootstrap.servers", "localhost:9092"));

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> factory.create(config));

      assertNotNull(ex.getCause(), "the underlying cause must be preserved for diagnosis");
      assertTrue(
          ex.getMessage().contains(ex.getCause().getMessage()),
          "wrapper must surface the real cause, but was: " + ex.getMessage());
    }
  }
}
