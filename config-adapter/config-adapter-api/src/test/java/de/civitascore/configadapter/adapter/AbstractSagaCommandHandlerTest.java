/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler.SagaApiException;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbstractSagaCommandHandlerTest {

  private static final String ADAPTER_NAME = "test-adapter";

  @Test
  @DisplayName("adapter() returns the name passed to constructor")
  void adapterReturnsConstructorName() {
    try (TestHandler handler = new TestHandler()) {
      assertEquals(ADAPTER_NAME, handler.adapter());
    }
  }

  @Test
  @DisplayName("initialize() stores config and calls doInitialize()")
  void initializeStoresConfigAndDelegates() {
    try (TestHandler handler = new TestHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);

      handler.initialize(config);

      assertNotNull(handler.config);
      assertEquals(1, handler.doInitializeCallCount);
    }
  }

  @Test
  @DisplayName("initialize() creates JAX-RS client")
  void initializeCreatesClient() {
    try (TestHandler handler = new TestHandler()) {
      handler.initialize(mock(AdapterConfig.class));

      assertNotNull(handler.client());
    }
  }

  @Test
  @DisplayName("initialize() propagates exception from doInitialize()")
  void initializePropagatesException() {
    try (TestHandler handler = new TestHandler()) {
      handler.throwOnInit = new IllegalArgumentException("bad config");

      assertThrows(
          IllegalArgumentException.class, () -> handler.initialize(mock(AdapterConfig.class)));
    }
  }

  @Nested
  @DisplayName("Config property helpers")
  class ConfigProperties {

    @Test
    @DisplayName("getProperty() prefixes key with adapter name")
    void getPropertyPrefixesWithAdapterName() {
      try (TestHandler handler = new TestHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("test-adapter.url")).thenReturn("http://localhost");
        handler.initialize(config);

        assertEquals("http://localhost", handler.getProperty("url"));
      }
    }

    @Test
    @DisplayName("getProperty() with default prefixes key with adapter name")
    void getPropertyWithDefaultPrefixesWithAdapterName() {
      try (TestHandler handler = new TestHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("test-adapter.url", "default")).thenReturn("default");
        handler.initialize(config);

        assertEquals("default", handler.getProperty("url", "default"));
      }
    }
  }

  @Nested
  @DisplayName("handle()")
  class Handle {

    @Test
    @DisplayName("delegates to doHandle() and returns result on success")
    void delegatesToDoHandle() {
      try (TestHandler handler = createInitializedHandler()) {
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "CREATE");

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("returns STEP_FAILED when doHandle() throws for forward step")
    void returnsFailureOnExceptionForForwardStep() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.throwOnHandle = new RuntimeException("boom");
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "CREATE");

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED when doHandle() throws for compensate step")
    void returnsCompensationFailureOnExceptionForCompensateStep() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.throwOnHandle = new RuntimeException("boom");
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "UNDO");

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  @DisplayName("unknownOperation()")
  class UnknownOperationTests {

    @Test
    @DisplayName("returns STEP_FAILED for forward unknown operation")
    void returnsFailureForForwardUnknownOperation() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.returnUnknown = true;
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "BOGUS");

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED for compensate unknown operation")
    void returnsCompensationFailureForCompensateUnknownOperation() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.returnUnknown = true;
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "BOGUS");

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("Error classification")
  class ErrorClassification {

    @Test
    @DisplayName("classifies ProcessingException as network error")
    void classifiesProcessingExceptionAsNetworkError() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.throwOnHandle = new ProcessingException("Connection refused");
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "CREATE");

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().startsWith("Network error:"));
      }
    }

    @Test
    @DisplayName("classifies generic exception with operation-prefixed message")
    void classifiesGenericException() {
      try (TestHandler handler = createInitializedHandler()) {
        handler.throwOnHandle = new RuntimeException("something broke");
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "CREATE");

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("something broke"));
      }
    }
  }

  @Nested
  @DisplayName("checkResponse()")
  class CheckResponseTests {

    @Test
    @DisplayName("succeeds for 2xx status codes")
    void succeedsFor2xx() {
      try (TestHandler handler = createInitializedHandler()) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);

        handler.checkResponse(response, "test-op");
      }
    }

    @Test
    @DisplayName("throws SagaApiException for 4xx status codes")
    void throwsFor4xx() {
      try (TestHandler handler = createInitializedHandler()) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(400);
        when(response.readEntity(String.class)).thenReturn("Bad Request");

        SagaApiException ex =
            assertThrows(SagaApiException.class, () -> handler.checkResponse(response, "test-op"));
        assertEquals(400, ex.statusCode());
      }
    }

    @Test
    @DisplayName("throws SagaApiException for 5xx status codes")
    void throwsFor5xx() {
      try (TestHandler handler = createInitializedHandler()) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(500);
        when(response.readEntity(String.class)).thenReturn("Server Error");

        SagaApiException ex =
            assertThrows(SagaApiException.class, () -> handler.checkResponse(response, "test-op"));
        assertEquals(500, ex.statusCode());
      }
    }
  }

  @Nested
  @DisplayName("JAX-RS client lifecycle")
  class ClientLifecycle {

    @Test
    @DisplayName("setClient() replaces the client")
    void setClientReplacesClient() {
      try (TestHandler handler = createInitializedHandler()) {
        Client mockClient = mock(Client.class);

        handler.setClient(mockClient);

        assertEquals(mockClient, handler.client());
      }
    }

    @Test
    @DisplayName("close() closes the client")
    void closeClosesClient() {
      try (TestHandler handler = createInitializedHandler()) {
        Client mockClient = mock(Client.class);
        handler.setClient(mockClient);

        handler.close();

        verify(mockClient).close();
      }
    }
  }

  private TestHandler createInitializedHandler() {
    TestHandler handler = new TestHandler();
    handler.initialize(mock(AdapterConfig.class));
    return handler;
  }

  private SagaCommandMessage createCommand(String type, String operation) {
    return new SagaCommandMessage(
        type, "msg-1", "saga-1", "step-1", ADAPTER_NAME, operation, Map.of());
  }

  static class TestHandler extends AbstractSagaCommandHandler {

    int doInitializeCallCount;
    RuntimeException throwOnInit;
    RuntimeException throwOnHandle;
    boolean returnUnknown;

    TestHandler() {
      super(ADAPTER_NAME);
    }

    @Override
    protected Client createClient() {
      return mock(Client.class);
    }

    @Override
    protected void doInitialize(AdapterConfig config) {
      doInitializeCallCount++;
      if (throwOnInit != null) {
        throw throwOnInit;
      }
    }

    @Override
    protected SagaCommandResult doHandle(SagaCommandMessage command) {
      if (throwOnHandle != null) {
        throw throwOnHandle;
      }
      if (returnUnknown) {
        return unknownOperation(command);
      }
      return SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }
  }
}
