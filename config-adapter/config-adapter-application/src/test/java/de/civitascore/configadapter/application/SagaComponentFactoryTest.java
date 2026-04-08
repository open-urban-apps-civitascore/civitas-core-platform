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
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertTrue(components.commandConsumer().isEmpty());
    }

    @Test
    @DisplayName("Should use default bootstrap servers when not configured")
    void defaultBootstrapServers() {
      AppConfig config = configWith(Map.of("kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertTrue(components.orchestrator().isEmpty());
    }

    @Test
    @DisplayName("Should return orchestrator Optional.empty when Kafka is unavailable")
    void orchestratorFailsGracefully() {
      AppConfig config =
          configWith(
              Map.of(
                  "kafka.bootstrap.servers", "localhost:19999",
                  "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertTrue(components.orchestrator().isEmpty());
    }

    @Test
    @DisplayName("Should return consistent SagaComponents record")
    void consistentRecord() {
      AppConfig config =
          configWith(
              Map.of(
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      SagaComponents components = factory.create(config);

      assertNotNull(components);
      assertNotNull(components.commandConsumer());
      assertNotNull(components.orchestrator());
    }
  }
}
