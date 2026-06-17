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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.HashMap;
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
  void initialize_withoutRequiredHandlers_throwsWithClearMessage() {
    AdapterConfig config = jdbcConfig("jdbc:h2:mem:no-handlers", "sa", "");
    FlowableSagaOrchestrator sut = new FlowableSagaOrchestrator(config, Map.of());

    IllegalStateException ex = assertThrows(IllegalStateException.class, sut::initialize);
    // Only the unconditional adapters are required at startup.
    assertTrue(ex.getMessage().contains("frost"));
    assertTrue(ex.getMessage().contains("apisix"));
  }

  @Test
  void initialize_withoutRedpandaHandler_succeeds() {
    // redpanda is the conditional pipeline adapter — a deployment without it (e.g. mid NiFi
    // migration) must still boot; only pipeline sagas need it, and they fail gracefully per step.
    AdapterConfig config = jdbcConfig("jdbc:h2:mem:no-redpanda", "sa", "");

    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");

    FlowableSagaOrchestrator sut =
        new FlowableSagaOrchestrator(config, Map.of("frost", frost, "apisix", apisix));

    assertDoesNotThrow(sut::initialize);
    sut.close();
  }

  @Test
  void start_withoutInitialize_throwsWithClearMessage() {
    FlowableSagaOrchestrator sut = orchestratorWith("jdbc:h2:mem:no-init", "sa", "");

    IllegalStateException ex = assertThrows(IllegalStateException.class, sut::start);
    assertTrue(ex.getMessage().contains("initialize"));
  }

  @Test
  void constructor_defensivelyCopiesHandlersMap() {
    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");
    SagaCommandHandler redpanda = mock(SagaCommandHandler.class);
    when(redpanda.adapter()).thenReturn("nifi");

    Map<String, SagaCommandHandler> mutableMap =
        new HashMap<>(Map.of("frost", frost, "apisix", apisix, "nifi", redpanda));

    FlowableSagaOrchestrator sut = orchestratorWith("jdbc:h2:mem:copy-test", "sa", "", mutableMap);

    // Mutate the original map AFTER construction
    mutableMap.clear();

    // Must still work — handlers were defensively copied
    assertDoesNotThrow(sut::initialize);
    sut.close();
  }

  private FlowableSagaOrchestrator orchestratorWith(
      String url, String user, String pass, Map<String, SagaCommandHandler> handlers) {
    return new FlowableSagaOrchestrator(jdbcConfig(url, user, pass), handlers);
  }

  private FlowableSagaOrchestrator orchestratorWith(String url, String user, String pass) {
    return new FlowableSagaOrchestrator(jdbcConfig(url, user, pass), minimalHandlers());
  }

  private AdapterConfig jdbcConfig(String url, String user, String pass) {
    Map<String, String> props = new HashMap<>();
    if (url != null) {
      props.put("flowable.jdbc.url", url);
    }
    if (user != null) {
      props.put("flowable.jdbc.username", user);
    }
    if (pass != null) {
      props.put("flowable.jdbc.password", pass);
    }
    AdapterConfig config = mock(AdapterConfig.class);
    when(config.getProperty(anyString())).thenAnswer(inv -> props.get(inv.getArgument(0)));
    when(config.getProperty(anyString(), anyString()))
        .thenAnswer(inv -> props.getOrDefault(inv.getArgument(0), inv.getArgument(1)));
    return config;
  }

  private Map<String, SagaCommandHandler> minimalHandlers() {
    SagaCommandHandler frost = mock(SagaCommandHandler.class);
    when(frost.adapter()).thenReturn("frost");
    SagaCommandHandler apisix = mock(SagaCommandHandler.class);
    when(apisix.adapter()).thenReturn("apisix");
    SagaCommandHandler redpanda = mock(SagaCommandHandler.class);
    when(redpanda.adapter()).thenReturn("nifi");
    return Map.of("frost", frost, "apisix", apisix, "nifi", redpanda);
  }
}
