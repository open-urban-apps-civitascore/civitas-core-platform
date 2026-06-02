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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import org.junit.jupiter.api.Test;

class SagaHandlerRegistryTest {

  @Test
  void shouldRegisterAndRetrieveHandler() {
    SagaCommandHandler frostHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);

    assertSame(frostHandler, registry.getHandler("frost"));
  }

  @Test
  void shouldRegisterMultipleHandlers() {
    SagaCommandHandler frostHandler = mock(SagaCommandHandler.class);
    SagaCommandHandler apisixHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    when(apisixHandler.adapter()).thenReturn("apisix");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);

    assertSame(frostHandler, registry.getHandler("frost"));
    assertSame(apisixHandler, registry.getHandler("apisix"));
  }

  @Test
  void shouldThrowForUnknownAdapter() {
    SagaHandlerRegistry registry = new SagaHandlerRegistry();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> registry.getHandler("unknown"));
    assertTrue(ex.getMessage().contains("unknown"));
  }

  @Test
  void shouldRejectDuplicateAdapterRegistration() {
    SagaCommandHandler frostHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    SagaCommandHandler anotherFrost = mock(SagaCommandHandler.class);
    when(anotherFrost.adapter()).thenReturn("frost");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> registry.register(anotherFrost));
    assertTrue(ex.getMessage().contains("frost"));
  }

  @Test
  void shouldReturnRegisteredAdapterNames() {
    SagaCommandHandler frostHandler = mock(SagaCommandHandler.class);
    SagaCommandHandler apisixHandler = mock(SagaCommandHandler.class);
    when(frostHandler.adapter()).thenReturn("frost");
    when(apisixHandler.adapter()).thenReturn("apisix");

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);

    assertEquals(2, registry.adapterNames().size());
    assertTrue(registry.adapterNames().contains("frost"));
    assertTrue(registry.adapterNames().contains("apisix"));
  }
}
