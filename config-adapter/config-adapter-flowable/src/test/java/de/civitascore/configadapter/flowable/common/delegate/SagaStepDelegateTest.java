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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.flowable.FlowableTestSupport;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import java.util.HashMap;
import java.util.Map;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.BpmnError;
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
class SagaStepDelegateTest {

  @Mock private DelegateExecution execution;
  @Mock private SagaCommandHandler frostHandler;

  private SagaHandlerRegistry registry;
  private SagaStepDelegate delegate;

  @BeforeEach
  void setUp() {
    registry = new SagaHandlerRegistry();
    when(frostHandler.adapter()).thenReturn("frost");
    registry.register(frostHandler);

    delegate = new SagaStepDelegate();
    delegate.setAdapterName(mockExpression("frost"));
    delegate.setOperation(mockExpression("CREATE_PROJECT"));
    delegate.setStepId(mockExpression("create-project"));
    delegate.setSagaHandlerRegistry(registry);
  }

  @Test
  void shouldBuildCommandFromProcessVariablesAndCallHandler() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("datasetName", "Test Dataset");
    variables.put("description", "A test");
    when(execution.getVariables()).thenReturn(variables);

    SagaCommandResult result =
        SagaCommandResult.success(
            "saga-123",
            "create-project",
            Map.of("projectId", "proj-789", "baseUrl", "http://frost/v1.1/Projects(1)"),
            Map.of("projectId", "proj-789"));
    when(frostHandler.handle(any())).thenReturn(result);

    delegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(frostHandler).handle(captor.capture());

    SagaCommandMessage command = captor.getValue();
    assertEquals("EXECUTE_STEP", command.type());
    assertEquals("saga-123", command.sagaId());
    assertEquals("create-project", command.stepId());
    assertEquals("frost", command.adapter());
    assertEquals("CREATE_PROJECT", command.operation());
    assertEquals("ds-456", command.payload().get("datasetId"));
    assertEquals("Test Dataset", command.payload().get("datasetName"));
  }

  @Test
  void shouldWriteResultDataToProcessVariables() {
    when(execution.getVariables()).thenReturn(Map.of("sagaId", "saga-123", "datasetId", "ds-456"));

    SagaCommandResult result =
        SagaCommandResult.success(
            "saga-123",
            "create-project",
            Map.of("projectId", "proj-789", "baseUrl", "http://frost/v1.1"),
            Map.of("projectId", "proj-789"));
    when(frostHandler.handle(any())).thenReturn(result);

    delegate.execute(execution);

    verify(execution).setVariable("projectId", "proj-789");
    verify(execution).setVariable("baseUrl", "http://frost/v1.1");
    verify(execution)
        .setVariable("compensationData_create-project", Map.of("projectId", "proj-789"));
  }

  @Test
  void shouldThrowBpmnErrorOnHandlerFailure() {
    when(execution.getVariables()).thenReturn(Map.of("sagaId", "saga-123", "datasetId", "ds-456"));

    SagaCommandResult result =
        SagaCommandResult.failure("saga-123", "create-project", "Connection refused");
    when(frostHandler.handle(any())).thenReturn(result);

    BpmnError error = assertThrows(BpmnError.class, () -> delegate.execute(execution));
    assertEquals("STEP_FAILED", error.getErrorCode());
    assertTrue(error.getMessage().contains("Connection refused"));
  }

  @Test
  void shouldApplyAdapterMappingBaseUrlToUpstreamUrlForApisix() {
    SagaCommandHandler apisixHandler = mock(SagaCommandHandler.class);
    when(apisixHandler.adapter()).thenReturn("apisix");
    when(apisixHandler.fieldAliases()).thenReturn(Map.of("baseUrl", "upstreamUrl"));
    registry.register(apisixHandler);

    SagaStepDelegate apisixDelegate = new SagaStepDelegate();
    apisixDelegate.setAdapterName(mockExpression("apisix"));
    apisixDelegate.setOperation(mockExpression("CREATE_ROUTE"));
    apisixDelegate.setStepId(mockExpression("create-route"));
    apisixDelegate.setSagaHandlerRegistry(registry);

    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("baseUrl", "http://frost/v1.1/Projects(1)");
    when(execution.getVariables()).thenReturn(variables);

    SagaCommandResult result =
        SagaCommandResult.success(
            "saga-123", "create-route", Map.of("routeId", "r-1"), Map.of("routeId", "r-1"));
    when(apisixHandler.handle(any())).thenReturn(result);

    apisixDelegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(apisixHandler).handle(captor.capture());

    assertEquals("http://frost/v1.1/Projects(1)", captor.getValue().payload().get("upstreamUrl"));
  }

  @Test
  void shouldApplyAdapterMappingBaseUrlToTargetUrlForRedpanda() {
    SagaCommandHandler redpandaHandler = mock(SagaCommandHandler.class);
    when(redpandaHandler.adapter()).thenReturn("nifi");
    when(redpandaHandler.fieldAliases()).thenReturn(Map.of("baseUrl", "targetUrl"));
    registry.register(redpandaHandler);

    SagaStepDelegate redpandaDelegate = new SagaStepDelegate();
    redpandaDelegate.setAdapterName(mockExpression("nifi"));
    redpandaDelegate.setOperation(mockExpression("DEPLOY_PIPELINES"));
    redpandaDelegate.setStepId(mockExpression("deploy-pipelines"));
    redpandaDelegate.setSagaHandlerRegistry(registry);

    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-123");
    variables.put("datasetId", "ds-456");
    variables.put("baseUrl", "http://frost/v1.1/Projects(1)");
    when(execution.getVariables()).thenReturn(variables);

    SagaCommandResult result =
        SagaCommandResult.success(
            "saga-123", "deploy-pipelines", Map.of("pipelineIds", "p-1"), Map.of());
    when(redpandaHandler.handle(any())).thenReturn(result);

    redpandaDelegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(redpandaHandler).handle(captor.capture());

    assertEquals("http://frost/v1.1/Projects(1)", captor.getValue().payload().get("targetUrl"));
  }

  private Expression mockExpression(String value) {
    return FlowableTestSupport.mockExpression(value);
  }
}
