/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.ConfigResultEvent;
import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

/**
 * Base class for ApisixAdapter unit tests. Runs the adapter's real OkHttp client against a
 * MockWebServer instead of mocking the HTTP client.
 */
abstract class AbstractApisixAdapterTest {

  protected ApisixAdapter adapter;
  protected AdapterConfig mockConfig;
  protected MockWebServer server;
  protected EventPublisher mockPublisher;

  @BeforeEach
  void setUpAdapter() throws IOException {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);
    server = new MockWebServer();
    server.start();

    when(mockConfig.getProperty("apisix.topics"))
        .thenReturn(
            "de.civitascore.api.backend.created,de.civitascore.api.backend.updated,"
                + "de.civitascore.api.backend.deleted");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");

    adapter.initialize(mockConfig);

    mockPublisher = mock(EventPublisher.class);
    adapter.setEventPublisher(mockPublisher);
  }

  @AfterEach
  void tearDownServer() throws IOException {
    server.close();
  }

  protected void givenMockPostReturns(int status, String body) {
    server.enqueue(new MockResponse.Builder().code(status).body(body).build());
  }

  protected void givenMockPutReturns(int status, String body) {
    server.enqueue(new MockResponse.Builder().code(status).body(body).build());
  }

  protected void givenMockDeleteReturns(int status, String body) {
    server.enqueue(new MockResponse.Builder().code(status).body(body).build());
  }

  protected ConfigResultEvent capturePublishedResult()
      throws FatalAdapterException, RetryableAdapterException {
    ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
    verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());
    return captor.getValue();
  }
}
