/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RedpandaConnectClientTest {

  private static final byte[] STRETCHED_KEY = new byte[32];

  private Client mockClient;
  private Invocation.Builder mockBuilder;
  private RedpandaConnectClient redpandaClient;

  @BeforeEach
  void setUp() {
    mockClient = mock(Client.class);
    WebTarget mockTarget = mock(WebTarget.class);
    WebTarget mockStreamsTarget = mock(WebTarget.class);
    WebTarget mockIdTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(anyString())).thenReturn(mockTarget);
    when(mockTarget.path("streams")).thenReturn(mockStreamsTarget);
    when(mockStreamsTarget.path(anyString())).thenReturn(mockIdTarget);
    when(mockIdTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);

    redpandaClient = new RedpandaConnectClient("http://localhost:4195", STRETCHED_KEY, mockClient);
  }

  @Nested
  @DisplayName("createPipeline")
  class CreatePipeline {

    @Test
    @DisplayName("succeeds on HTTP 200")
    void createPipeline_http200_succeeds() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      assertDoesNotThrow(
          () -> redpandaClient.createPipeline("test-pipeline", Map.of("input", Map.of())));
    }

    @Test
    @DisplayName("throws FatalAdapterException on HTTP 400")
    void createPipeline_http400_throwsFatalAdapterException() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Bad Request");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      assertThrows(
          FatalAdapterException.class,
          () -> redpandaClient.createPipeline("test-pipeline", Map.of()));
    }

    @Test
    @DisplayName("throws RetryableAdapterException with REDPANDA_ERROR on HTTP 500")
    void createPipeline_http500_throwsRetryableWithCorrectErrorCode() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(500);
      when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      RetryableAdapterException exception =
          assertThrows(
              RetryableAdapterException.class,
              () -> redpandaClient.createPipeline("test-pipeline", Map.of()));

      assertEquals(AdapterErrorCode.REDPANDA_ERROR, exception.getErrorCode());
      assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("throws RetryableAdapterException on network error")
    void createPipeline_networkError_throwsRetryableAdapterException() {
      when(mockBuilder.post(any(Entity.class)))
          .thenThrow(new ProcessingException("Connection refused"));

      assertThrows(
          RetryableAdapterException.class,
          () -> redpandaClient.createPipeline("test-pipeline", Map.of()));
    }
  }

  @Nested
  @DisplayName("updatePipeline")
  class UpdatePipeline {

    @Test
    @DisplayName("succeeds on HTTP 200")
    void updatePipeline_http200_succeeds() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      assertDoesNotThrow(
          () -> redpandaClient.updatePipeline("test-pipeline", Map.of("input", Map.of())));
    }

    @Test
    @DisplayName("throws FatalAdapterException on HTTP 404")
    void updatePipeline_http404_throwsFatalAdapterException() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(404);
      when(mockResponse.readEntity(String.class)).thenReturn("Not Found");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      assertThrows(
          FatalAdapterException.class,
          () -> redpandaClient.updatePipeline("test-pipeline", Map.of()));
    }

    @Test
    @DisplayName("throws RetryableAdapterException on HTTP 503")
    void updatePipeline_http503_throwsRetryableAdapterException() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(503);
      when(mockResponse.readEntity(String.class)).thenReturn("Service Unavailable");
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

      assertThrows(
          RetryableAdapterException.class,
          () -> redpandaClient.updatePipeline("test-pipeline", Map.of()));
    }
  }

  @Nested
  @DisplayName("deletePipeline")
  class DeletePipeline {

    @Test
    @DisplayName("succeeds on HTTP 200")
    void deletePipeline_http200_succeeds() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.delete()).thenReturn(mockResponse);

      assertDoesNotThrow(() -> redpandaClient.deletePipeline("test-pipeline"));
    }

    @Test
    @DisplayName("throws FatalAdapterException on HTTP 400")
    void deletePipeline_http400_throwsFatalAdapterException() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(400);
      when(mockResponse.readEntity(String.class)).thenReturn("Bad Request");
      when(mockBuilder.delete()).thenReturn(mockResponse);

      assertThrows(
          FatalAdapterException.class, () -> redpandaClient.deletePipeline("test-pipeline"));
    }

    @Test
    @DisplayName("throws RetryableAdapterException on network error")
    void deletePipeline_networkError_throwsRetryableAdapterException() {
      when(mockBuilder.delete()).thenThrow(new ProcessingException("Connection refused"));

      assertThrows(
          RetryableAdapterException.class, () -> redpandaClient.deletePipeline("test-pipeline"));
    }
  }

  @Nested
  @DisplayName("Pipeline ID validation")
  class PipelineIdValidation {

    @Test
    @DisplayName("path traversal ID throws FatalAdapterException")
    void createPipeline_pathTraversalId_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class,
          () -> redpandaClient.createPipeline("../../admin", Map.of()));
    }

    @Test
    @DisplayName("slash in ID throws FatalAdapterException")
    void createPipeline_slashInId_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class,
          () -> redpandaClient.createPipeline("my/pipeline", Map.of()));
    }

    @Test
    @DisplayName("leading dash throws FatalAdapterException")
    void createPipeline_leadingDash_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class, () -> redpandaClient.createPipeline("-pipeline", Map.of()));
    }

    @Test
    @DisplayName("null ID throws FatalAdapterException")
    void createPipeline_nullId_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class, () -> redpandaClient.createPipeline(null, Map.of()));
    }

    @Test
    @DisplayName("spaces in ID throws FatalAdapterException")
    void createPipeline_spacesInId_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class,
          () -> redpandaClient.createPipeline("my pipeline", Map.of()));
    }

    @Test
    @DisplayName("valid ID with dots, dashes, and underscores succeeds")
    void createPipeline_validIdWithDotsAndDashes_succeeds() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      assertDoesNotThrow(
          () -> redpandaClient.createPipeline("my-pipeline.v2_test", Map.of("input", Map.of())));
    }

    @Test
    @DisplayName("updatePipeline with invalid ID throws FatalAdapterException")
    void updatePipeline_invalidId_throwsFatalAdapterException() {
      assertThrows(
          FatalAdapterException.class, () -> redpandaClient.updatePipeline("../etc", Map.of()));
    }

    @Test
    @DisplayName("deletePipeline with invalid ID throws FatalAdapterException")
    void deletePipeline_invalidId_throwsFatalAdapterException() {
      assertThrows(FatalAdapterException.class, () -> redpandaClient.deletePipeline("../../admin"));
    }

    @Test
    @DisplayName("ID exceeding 128 characters throws FatalAdapterException")
    void createPipeline_idExceeding128Chars_throwsFatalAdapterException() {
      String longId = "a" + "b".repeat(128); // 129 chars total
      assertThrows(
          FatalAdapterException.class, () -> redpandaClient.createPipeline(longId, Map.of()));
    }

    @Test
    @DisplayName("ID exactly 128 characters succeeds")
    void createPipeline_idExactly128Chars_succeeds() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(200);
      when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

      String exactId = "a" + "b".repeat(127); // 128 chars total
      assertDoesNotThrow(() -> redpandaClient.createPipeline(exactId, Map.of("input", Map.of())));
    }
  }

  @Test
  @DisplayName("close should close the underlying JAX-RS client")
  void close_default_closesUnderlyingClient() {
    redpandaClient.close();
    verify(mockClient).close();
  }
}
