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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.Context;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AbstractSagaDelegateTest {

  /** Minimal concrete delegate to exercise the shared {@code resolveRegistry} helper. */
  private static final class TestDelegate extends AbstractSagaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
      // no-op
    }
  }

  @Test
  void resolveRegistry_whenBeanMissing_throwsWithClearMessage() {
    TestDelegate delegate = new TestDelegate();
    DelegateExecution execution = mock(DelegateExecution.class);

    try (MockedStatic<Context> ctx = mockStatic(Context.class)) {
      ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
      when(config.getBeans()).thenReturn(new HashMap<>());
      ctx.when(Context::getProcessEngineConfiguration).thenReturn(config);

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> delegate.resolveRegistry(execution));
      assertTrue(ex.getMessage().contains("sagaHandlerRegistry"));
    }
  }

  @Test
  void resolveRegistry_whenBeanWrongType_throwsWithClearMessage() {
    TestDelegate delegate = new TestDelegate();
    DelegateExecution execution = mock(DelegateExecution.class);

    try (MockedStatic<Context> ctx = mockStatic(Context.class)) {
      ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
      Map<Object, Object> beans = new HashMap<>();
      beans.put("sagaHandlerRegistry", "not-a-registry");
      when(config.getBeans()).thenReturn(beans);
      ctx.when(Context::getProcessEngineConfiguration).thenReturn(config);

      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> delegate.resolveRegistry(execution));
      assertTrue(ex.getMessage().contains("sagaHandlerRegistry"));
    }
  }
}
