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
    @DisplayName(
        "Should return empty commandConsumer when no SagaCommandHandler implementations exist")
    void noSagaHandlers() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "custom",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertTrue(components.commandConsumer().isEmpty());
    }

    @Test
    @DisplayName("Should not throw when using default bootstrap servers")
    void defaultBootstrapServers() {
      AppConfig config =
          configWith(Map.of("orchestrator.engine", "custom", "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertNotNull(components.orchestrator());
    }

    @Test
    @DisplayName("Should return consistent SagaComponents record")
    void consistentRecord() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "custom",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertNotNull(components.commandConsumer());
      assertNotNull(components.orchestrator());
    }

    @Test
    @DisplayName(
        "Should reject unknown orchestrator.engine values instead of silently using custom")
    void unknownEngine_failsFast() {
      AppConfig config =
          configWith(
              Map.of(
                  "orchestrator.engine", "flowabel",
                  "kafka.bootstrap.servers", "localhost:9092"));

      assertThrows(
          IllegalArgumentException.class,
          () -> factory.create(config),
          "Unknown orchestrator.engine must be rejected, not silently treated as custom");
    }

    @Test
    @DisplayName("Should fail-fast when orchestrator.engine=flowable but JDBC config is missing")
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
  }
}
