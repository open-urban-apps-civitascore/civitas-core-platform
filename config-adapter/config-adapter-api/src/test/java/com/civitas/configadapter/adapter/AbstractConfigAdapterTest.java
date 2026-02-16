/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbstractConfigAdapterTest {

  private static final String TEST_RESULT_TYPE = "de.civitascore.test.processing.result";

  private static class TestAdapter extends AbstractConfigAdapter {
    private String adapterName;
    private FatalAdapterException fatalToThrow;
    private RetryableAdapterException retryableToThrow;
    private RuntimeException runtimeToThrow;

    private TestAdapter(AdapterConfig config, String adapterName) {
      this.adapterName = adapterName;
      initialize(config);
    }

    @Override
    protected void doProcessConfigEvent(String topic, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException {
      if (fatalToThrow != null) {
        throw fatalToThrow;
      }
      if (retryableToThrow != null) {
        throw retryableToThrow;
      }
      if (runtimeToThrow != null) {
        throw runtimeToThrow;
      }
    }

    @Override
    protected String getResultType() {
      return TEST_RESULT_TYPE;
    }

    @Override
    public void close() {
      // Test implementation
    }

    @Override
    public String getName() {
      return adapterName;
    }
  }

  @Test
  void constructorShouldThrowExceptionWhenConfigIsNull() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new TestAdapter(null, "test-adapter"));

    assertEquals("AdapterConfig cannot be null", exception.getMessage());
  }

  @Test
  void constructorShouldThrowExceptionWhenAdapterNameIsNull() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new TestAdapter(mockConfig, null));

    assertEquals("Adapter name cannot be null or empty", exception.getMessage());
  }

  @Test
  void constructorShouldThrowExceptionWhenAdapterNameIsEmpty() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new TestAdapter(mockConfig, ""));

    assertEquals("Adapter name cannot be null or empty", exception.getMessage());
  }

  @Test
  void constructorShouldThrowExceptionWhenAdapterNameIsBlank() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new TestAdapter(mockConfig, "   "));

    assertEquals("Adapter name cannot be null or empty", exception.getMessage());
  }

  @Test
  void constructorShouldThrowExceptionWhenInvalidTopicConfigured() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn("invalid-topic");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new TestAdapter(mockConfig, "test-adapter"));

    assertTrue(exception.getMessage().contains("Invalid topic 'invalid-topic'"));
    assertTrue(exception.getMessage().contains("Must be one of"));
  }

  @Test
  void constructorShouldCreateAdapterWithEmptyTopicsWhenNoTopicsConfigured() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertTrue(adapter.getSubscribedTopics().isEmpty());
  }

  @Test
  void constructorShouldCreateAdapterWithEmptyTopicsWhenBlankTopicsConfigured() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn("   ");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertTrue(adapter.getSubscribedTopics().isEmpty());
  }

  @Test
  void constructorShouldCreateAdapterWithValidSingleTopic() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(Topics.USER_CREATED.toString());

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(1, adapter.getSubscribedTopics().size());
    assertEquals(Topics.USER_CREATED.toString(), adapter.getSubscribedTopics().getFirst());
  }

  @Test
  void constructorShouldCreateAdapterWithValidMultipleTopics() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics"))
        .thenReturn(Topics.USER_CREATED + "," + Topics.USER_UPDATED);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(2, adapter.getSubscribedTopics().size());
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_CREATED.toString()));
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_UPDATED.toString()));
  }

  @Test
  void constructorShouldHandleTopicsWithSpaces() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics"))
        .thenReturn("  " + Topics.USER_CREATED + " , " + Topics.USER_UPDATED + "  ");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(2, adapter.getSubscribedTopics().size());
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_CREATED.toString()));
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_UPDATED.toString()));
  }

  @Test
  void getNameShouldReturnAdapterName() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("my-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "my-adapter");

    assertEquals("my-adapter", adapter.getName());
    adapter.close();
  }

  @Test
  void getAdapterPropertyShouldReturnPropertyValue() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    when(mockConfig.getProperty("test-adapter.url")).thenReturn("http://localhost:8080");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals("http://localhost:8080", adapter.getAdapterProperty("url"));
    adapter.close();
  }

  @Test
  void getAdapterPropertyWithDefaultShouldReturnPropertyValue() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    when(mockConfig.getProperty("test-adapter.url", "default")).thenReturn("http://localhost:8080");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals("http://localhost:8080", adapter.getAdapterProperty("url", "default"));
    adapter.close();
  }

  @Test
  void setEventPublisherShouldSetPublisher() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    EventPublisher mockPublisher = mock(EventPublisher.class);

    assertNull(adapter.getEventPublisher());

    adapter.setEventPublisher(mockPublisher);

    assertEquals(mockPublisher, adapter.getEventPublisher());
    adapter.close();
  }

  @Test
  void getConfigShouldReturnConfig() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals(mockConfig, adapter.getConfig());
    adapter.close();
  }

  @Test
  void getSubscribedTopicsShouldReturnImmutableList() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(Topics.USER_CREATED.toString());

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    List<String> topics = adapter.getSubscribedTopics();

    assertNotNull(topics);
    assertThrows(UnsupportedOperationException.class, () -> topics.add("new-topic"));
    adapter.close();
  }

  @Test
  void templateMethodShouldCatchFatalExceptionAndPublishFailureResult()
      throws FatalAdapterException, RetryableAdapterException {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    EventPublisher mockPublisher = mock(EventPublisher.class);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.setEventPublisher(mockPublisher);
    adapter.fatalToThrow =
        new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test error");

    ConfigEvent event = createTestConfigEvent();

    FatalAdapterException thrown =
        assertThrows(
            FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, thrown.getErrorCode());

    ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
    verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

    ConfigResultEvent result = captor.getValue();
    assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
    assertEquals("civitas.config-adapter.test-adapter", result.source());
    assertEquals(TEST_RESULT_TYPE, result.resultType());
  }

  @Test
  void templateMethodShouldLetRetryableExceptionPassThrough() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    EventPublisher mockPublisher = mock(EventPublisher.class);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.setEventPublisher(mockPublisher);
    adapter.retryableToThrow =
        new RetryableAdapterException(AdapterErrorCode.CONNECTION_TIMEOUT, "test-service");

    ConfigEvent event = createTestConfigEvent();

    RetryableAdapterException thrown =
        assertThrows(
            RetryableAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

    assertEquals(AdapterErrorCode.CONNECTION_TIMEOUT, thrown.getErrorCode());
    assertTrue(thrown.isRetryable());
  }

  @Test
  void templateMethodShouldWrapUnexpectedExceptionAsFatalAndPublishFailure()
      throws FatalAdapterException, RetryableAdapterException {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    EventPublisher mockPublisher = mock(EventPublisher.class);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.setEventPublisher(mockPublisher);
    adapter.runtimeToThrow = new RuntimeException("unexpected error");

    ConfigEvent event = createTestConfigEvent();

    FatalAdapterException thrown =
        assertThrows(
            FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

    assertEquals(AdapterErrorCode.UNKNOWN_ERROR, thrown.getErrorCode());

    ArgumentCaptor<ConfigResultEvent> captor = ArgumentCaptor.forClass(ConfigResultEvent.class);
    verify(mockPublisher).publish(eq("test-result-topic"), captor.capture());

    ConfigResultEvent result = captor.getValue();
    assertEquals(ConfigResultEvent.Status.FAILURE, result.status());
  }

  @Test
  void templateMethodShouldNotPublishWhenNoEventPublisher() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.fatalToThrow =
        new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test error");

    ConfigEvent event = createTestConfigEvent();

    assertThrows(
        FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));
    // No NPE from publishFailureResult when publisher is null
  }

  @Test
  void templateMethodShouldNotPublishWhenEventIsNull() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    EventPublisher mockPublisher = mock(EventPublisher.class);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.setEventPublisher(mockPublisher);
    adapter.runtimeToThrow = new NullPointerException("null event");

    assertThrows(FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", null));
    // publishFailureResult should handle null event gracefully
  }

  @Test
  void getAdapterSourceShouldReturnDefaultSource() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("my-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "my-adapter");

    assertEquals("civitas.config-adapter.my-adapter", adapter.getAdapterSource());
    adapter.close();
  }

  @Test
  void publishFailureResultShouldHandlePublishException()
      throws FatalAdapterException, RetryableAdapterException {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    EventPublisher mockPublisher = mock(EventPublisher.class);
    doThrow(new FatalAdapterException(AdapterErrorCode.PUBLISH_ERROR, "publish failed"))
        .when(mockPublisher)
        .publish(any(String.class), any(ConfigResultEvent.class));

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    adapter.setEventPublisher(mockPublisher);
    adapter.fatalToThrow =
        new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "test error");

    ConfigEvent event = createTestConfigEvent();

    // Should still throw the original exception, not the publish exception
    FatalAdapterException thrown =
        assertThrows(
            FatalAdapterException.class, () -> adapter.processConfigEvent("test-topic", event));

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, thrown.getErrorCode());
  }

  private ConfigEvent createTestConfigEvent() {
    Metadata metadata =
        new Metadata(
            "msg-123",
            OffsetDateTime.now(),
            "test-source",
            "corr-123",
            "v1.0.0",
            "test-result-topic");
    Payload payload = new Payload("test", "test/resource-1", Operation.CREATE, null);
    return new ConfigEvent(metadata, payload);
  }
}
