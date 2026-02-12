/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigResultEvent;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

/**
 * Base class for ApisixAdapter unit tests that need a mocked JAX-RS Client chain. Sets up the
 * adapter with mock client, config, and publisher.
 */
abstract class AbstractApisixAdapterTest {

  protected ApisixAdapter adapter;
  protected AdapterConfig mockConfig;
  protected Client mockClient;
  protected WebTarget mockTarget;
  protected WebTarget mockPathTarget;
  protected Invocation.Builder mockBuilder;
  protected Response mockResponse;
  protected EventPublisher mockPublisher;

  @BeforeEach
  void setUpAdapter() {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);

    when(mockConfig.getProperty("apisix.topics"))
        .thenReturn(
            "de.civitascore.api.backend.created,de.civitascore.api.backend.updated,"
                + "de.civitascore.api.backend.deleted");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://localhost:9180");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");

    // Mock the JAX-RS Client fluent API chain
    mockClient = mock(Client.class);
    mockTarget = mock(WebTarget.class);
    mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);
    mockResponse = mock(Response.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.resolveTemplate(any(String.class), any())).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

    adapter.setClient(mockClient);
    adapter.initialize(mockConfig);

    mockPublisher = mock(EventPublisher.class);
    adapter.setEventPublisher(mockPublisher);
  }

  protected void givenMockPostReturns(int status, String body) {
    when(mockResponse.getStatus()).thenReturn(status);
    when(mockResponse.readEntity(String.class)).thenReturn(body);
    when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);
  }

  protected void givenMockPutReturns(int status, String body) {
    when(mockResponse.getStatus()).thenReturn(status);
    when(mockResponse.readEntity(String.class)).thenReturn(body);
    when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);
  }

  protected void givenMockDeleteReturns(int status, String body) {
    when(mockResponse.getStatus()).thenReturn(status);
    when(mockResponse.readEntity(String.class)).thenReturn(body);
    when(mockBuilder.delete()).thenReturn(mockResponse);
  }

  protected ConfigResultEvent capturePublishedResult()
      throws FatalAdapterException, RetryableAdapterException {
    ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
    verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
    return captor.getValue();
  }
}
