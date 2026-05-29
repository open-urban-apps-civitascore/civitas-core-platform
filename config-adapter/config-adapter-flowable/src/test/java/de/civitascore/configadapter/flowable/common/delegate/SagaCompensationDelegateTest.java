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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaCompensationDelegateTest {

  @Mock private DelegateExecution execution;
  @Mock private SagaCommandHandler frostHandler;

  private SagaHandlerRegistry registry;
  private SagaCompensationDelegate delegate;

  @BeforeEach
  void setUp() {
    registry = new SagaHandlerRegistry();
    when(frostHandler.adapter()).thenReturn("frost");
    registry.register(frostHandler);

    delegate = new SagaCompensationDelegate();
    delegate.setAdapterName(mockExpression("frost"));
    delegate.setOperation(mockExpression("DELETE_PROJECT"));
    delegate.setStepId(mockExpression("create-project"));
    delegate.setSagaHandlerRegistry(registry);
  }

  @Test
  void shouldBuildCompensationCommandFromStoredData() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("compensationData_create-project", Map.of("projectId", "proj-789"));
    variables.put("projectId", "proj-789");
    variables.put("baseUrl", "http://frost/v1.1");
    when(execution.getVariables()).thenReturn(variables);

    SagaCommandResult result = SagaCommandResult.compensationSuccess("saga-123", "create-project");
    when(frostHandler.handle(any())).thenReturn(result);

    delegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler).handle(captor.capture());

    SagaCommandMessage command = captor.getValue();
    assertEquals("COMPENSATE_STEP", command.type());
    assertEquals("saga-123", command.sagaId());
    assertEquals("create-project", command.stepId());
    assertEquals("frost", command.adapter());
    assertEquals("DELETE_PROJECT", command.operation());
    assertEquals("proj-789", command.payload().get("projectId"));
  }

  @Test
  void shouldNotThrowOnCompensationFailure() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("compensationData_create-project", Map.of("projectId", "proj-789"));
    variables.put("projectId", "proj-789");
    when(execution.getVariables()).thenReturn(variables);
    when(execution.getVariable("compensationErrors")).thenReturn(null);

    SagaCommandResult result =
        SagaCommandResult.compensationFailure("saga-123", "create-project", "Service unavailable");
    when(frostHandler.handle(any())).thenReturn(result);

    // Must NOT throw — best-effort compensation
    assertDoesNotThrow(() -> delegate.execute(execution));
  }

  @Test
  void shouldCollectCompensationErrorsInVariable() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("compensationData_create-project", Map.of("projectId", "proj-789"));
    variables.put("projectId", "proj-789");
    when(execution.getVariables()).thenReturn(variables);
    when(execution.getVariable("compensationErrors")).thenReturn(new ArrayList<>());

    SagaCommandResult result =
        SagaCommandResult.compensationFailure("saga-123", "create-project", "Service unavailable");
    when(frostHandler.handle(any())).thenReturn(result);

    delegate.execute(execution);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(execution).setVariable(eq("compensationErrors"), captor.capture());
    assertTrue(captor.getValue() instanceof List);
  }

  @Test
  void shouldNotThrowAndCollectErrorWhenHandlerThrows() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("compensationData_create-project", Map.of("projectId", "proj-789"));
    variables.put("projectId", "proj-789");
    when(execution.getVariables()).thenReturn(variables);
    when(execution.getVariable("compensationErrors")).thenReturn(new ArrayList<>());
    when(frostHandler.handle(any())).thenThrow(new RuntimeException("handler exploded"));

    // Best-effort: a throwing handler must not break the compensation chain.
    assertDoesNotThrow(() -> delegate.execute(execution));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(execution).setVariable(eq("compensationErrors"), captor.capture());
    assertTrue(captor.getValue() instanceof List);
  }

  @Test
  void shouldIncludeResultDataInCompensationPayload() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("compensationData_create-project", Map.of("projectId", "proj-789"));
    variables.put("projectId", "proj-789");
    variables.put("baseUrl", "http://frost/v1.1");
    when(execution.getVariables()).thenReturn(variables);

    SagaCommandResult result = SagaCommandResult.compensationSuccess("saga-123", "create-project");
    when(frostHandler.handle(any())).thenReturn(result);

    delegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler).handle(captor.capture());

    Map<String, Object> payload = captor.getValue().payload();
    assertEquals("proj-789", payload.get("projectId"));
    assertEquals("http://frost/v1.1", payload.get("baseUrl"));
  }

  private Expression mockExpression(String value) {
    return FlowableTestSupport.mockExpression(value);
  }
}
