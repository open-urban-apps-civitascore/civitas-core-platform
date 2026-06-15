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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaStepDelegatePayloadLeakTest {

  @Mock private DelegateExecution execution;

  @Test
  void internalVariables_notIncludedInPayload() {
    SagaCommandHandler handler = mock(SagaCommandHandler.class);
    when(handler.adapter()).thenReturn("frost");
    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(handler);

    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "s-1");
    variables.put("datasetId", "ds-1");
    variables.put("datasetName", "Test");
    variables.put("compensationErrors", new ArrayList<>(List.of(Map.of("step", "x"))));
    variables.put("failedStep", "some-step");
    variables.put("sagaError", "some error");
    variables.put("compensationData_create-project", Map.of("projectId", "p1"));

    when(execution.getVariables()).thenReturn(variables);
    when(handler.handle(any()))
        .thenReturn(SagaCommandResult.success("s-1", "step", Map.of(), Map.of()));

    SagaStepDelegate delegate = new SagaStepDelegate();
    delegate.setAdapterName(FlowableTestSupport.mockExpression("frost"));
    delegate.setOperation(FlowableTestSupport.mockExpression("CREATE_PROJECT"));
    delegate.setStepId(FlowableTestSupport.mockExpression("create-project"));
    delegate.setSagaHandlerRegistry(registry);

    delegate.execute(execution);

    ArgumentCaptor<SagaCommandMessage> captor = ArgumentCaptor.forClass(SagaCommandMessage.class);
    verify(handler).handle(captor.capture());

    Map<String, Object> payload = captor.getValue().payload();
    assertFalse(
        payload.containsKey("compensationErrors"),
        "compensationErrors should not leak into adapter payload");
    assertFalse(
        payload.containsKey("failedStep"), "failedStep should not leak into adapter payload");
    assertFalse(payload.containsKey("sagaError"), "sagaError should not leak into adapter payload");
    assertFalse(
        payload.containsKey("compensationData_create-project"),
        "compensationData_ should not leak into adapter payload");
  }
}
