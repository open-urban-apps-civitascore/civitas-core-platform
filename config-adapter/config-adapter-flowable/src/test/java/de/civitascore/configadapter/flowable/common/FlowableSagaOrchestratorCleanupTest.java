/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlowableSagaOrchestratorCleanupTest {

  @Test
  void initialize_withoutJdbcConfig_throwsWithClearMessage() {
    FlowableSagaOrchestrator sut = orchestratorWith(null, null, null);

    IllegalStateException ex = assertThrows(IllegalStateException.class, sut::initialize);
    assertTrue(ex.getMessage().contains("flowable.jdbc"));
  }

  @Test
  void close_afterFailedInitialize_doesNotThrow() {
    FlowableSagaOrchestrator sut = orchestratorWith(null, null, null);

    assertThrows(Exception.class, sut::initialize);
    assertDoesNotThrow(sut::close);
  }

  @Test
  void close_isIdempotent() {
    FlowableSagaOrchestrator sut =
        orchestratorWith(
            "jdbc:h2:mem:idempotent-" + UUID.randomUUID().toString().substring(0, 8), "sa", "");

    assertDoesNotThrow(sut::initialize);

    assertDoesNotThrow(sut::close);
    assertDoesNotThrow(sut::close, "Second close() must not throw");
  }

  @Test
  void close_afterSuccessfulInitialize_doesNotThrow() {
    FlowableSagaOrchestrator sut =
        orchestratorWith(
            "jdbc:h2:mem:success-" + UUID.randomUUID().toString().substring(0, 8), "sa", "");

    assertDoesNotThrow(sut::initialize);

    assertDoesNotThrow(sut::close);
  }

  @Test
  void initialize_withoutRequiredHandlers_throwsWithClearMessage() {
    AdapterConfig config =
        new AdapterConfig() {
          @Override
          public String getProperty(String key) {
            return switch (key) {
              case "flowable.jdbc.url" -> "jdbc:h2:mem:no-handlers";
              case "flowable.jdbc.username" -> "sa";
              case "flowable.jdbc.password" -> "";
              default -> null;
            };
          }

          @Override
          public String getProperty(String key, String defaultValue) {
            String value = getProperty(key);
            return value != null ? value : defaultValue;
          }
        };
    FlowableSagaOrchestrator sut = new FlowableSagaOrchestrator(config, Map.of());

    IllegalStateException ex = assertThrows(IllegalStateException.class, sut::initialize);
    assertTrue(ex.getMessage().contains("frost"));
    assertTrue(ex.getMessage().contains("apisix"));
    assertTrue(ex.getMessage().contains("redpanda"));
  }

  @Test
  void initialize_withoutRedpandaHandler_throwsMentioningRedpanda() {
    AdapterConfig config =
        new AdapterConfig() {
          @Override
          public String getProperty(String key) {
            return switch (key) {
              case "flowable.jdbc.url" -> "jdbc:h2:mem:no-redpanda";
              case "flowable.jdbc.username" -> "sa";
              case "flowable.jdbc.password" -> "";
              default -> null;
            };
          }

          @Override
          public String getProperty(String key, String defaultValue) {
            String value = getProperty(key);
            return value != null ? value : defaultValue;
          }
        };

    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");

    FlowableSagaOrchestrator sut =
        new FlowableSagaOrchestrator(config, Map.of("frost", frost, "apisix", apisix));

    IllegalStateException ex = assertThrows(IllegalStateException.class, sut::initialize);
    assertTrue(
        ex.getMessage().contains("redpanda"),
        "Error must specifically mention the missing redpanda handler");
  }

  @Test
  void constructor_defensivelyCopiesHandlersMap() {
    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");
    SagaCommandHandler redpanda = mock(SagaCommandHandler.class);
    when(redpanda.adapter()).thenReturn("redpanda");

    java.util.Map<String, SagaCommandHandler> mutableMap =
        new java.util.HashMap<>(Map.of("frost", frost, "apisix", apisix, "redpanda", redpanda));

    FlowableSagaOrchestrator sut = orchestratorWith("jdbc:h2:mem:copy-test", "sa", "", mutableMap);

    // Mutate the original map AFTER construction
    mutableMap.clear();

    // Must still work — handlers were defensively copied
    assertDoesNotThrow(sut::initialize);
    sut.close();
  }

  private FlowableSagaOrchestrator orchestratorWith(
      String url, String user, String pass, java.util.Map<String, SagaCommandHandler> handlers) {
    AdapterConfig config =
        new AdapterConfig() {
          @Override
          public String getProperty(String key) {
            return switch (key) {
              case "flowable.jdbc.url" -> url;
              case "flowable.jdbc.username" -> user;
              case "flowable.jdbc.password" -> pass;
              default -> null;
            };
          }

          @Override
          public String getProperty(String key, String defaultValue) {
            String value = getProperty(key);
            return value != null ? value : defaultValue;
          }
        };
    return new FlowableSagaOrchestrator(config, handlers);
  }

  private FlowableSagaOrchestrator orchestratorWith(String url, String user, String pass) {
    AdapterConfig config =
        new AdapterConfig() {
          @Override
          public String getProperty(String key) {
            return switch (key) {
              case "flowable.jdbc.url" -> url;
              case "flowable.jdbc.username" -> user;
              case "flowable.jdbc.password" -> pass;
              default -> null;
            };
          }

          @Override
          public String getProperty(String key, String defaultValue) {
            String value = getProperty(key);
            return value != null ? value : defaultValue;
          }
        };
    return new FlowableSagaOrchestrator(config, minimalHandlers());
  }

  private Map<String, SagaCommandHandler> minimalHandlers() {
    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");
    SagaCommandHandler redpanda = mock(SagaCommandHandler.class);
    when(redpanda.adapter()).thenReturn("redpanda");
    return Map.of("frost", frost, "apisix", apisix, "redpanda", redpanda);
  }
}
