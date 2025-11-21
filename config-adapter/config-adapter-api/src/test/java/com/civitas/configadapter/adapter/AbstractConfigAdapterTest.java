/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractConfigAdapterTest {

  private static class TestAdapter extends AbstractConfigAdapter {
    public TestAdapter(AdapterConfig config, String adapterName) {
      super(config, adapterName);
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event) {
      // Test implementation
    }

    @Override
    public void close() {
      // Test implementation
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
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(Topics.USER_CREATED);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(1, adapter.getSubscribedTopics().size());
    assertEquals(Topics.USER_CREATED, adapter.getSubscribedTopics().get(0));
  }

  @Test
  void constructorShouldCreateAdapterWithValidMultipleTopics() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics"))
        .thenReturn(Topics.USER_CREATED + "," + Topics.USER_UPDATED);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(2, adapter.getSubscribedTopics().size());
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_CREATED));
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_UPDATED));
  }

  @Test
  void constructorShouldHandleTopicsWithSpaces() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics"))
        .thenReturn("  " + Topics.USER_CREATED + " , " + Topics.USER_UPDATED + "  ");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertNotNull(adapter);
    assertEquals(2, adapter.getSubscribedTopics().size());
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_CREATED));
    assertTrue(adapter.getSubscribedTopics().contains(Topics.USER_UPDATED));
  }

  @Test
  void getNameShouldReturnAdapterName() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("my-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "my-adapter");

    assertEquals("my-adapter", adapter.getName());
  }

  @Test
  void getAdapterPropertyShouldReturnPropertyValue() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    when(mockConfig.getProperty("test-adapter.url")).thenReturn("http://localhost:8080");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals("http://localhost:8080", adapter.getAdapterProperty("url"));
  }

  @Test
  void getAdapterPropertyWithDefaultShouldReturnPropertyValue() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);
    when(mockConfig.getProperty("test-adapter.url", "default")).thenReturn("http://localhost:8080");

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals("http://localhost:8080", adapter.getAdapterProperty("url", "default"));
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
  }

  @Test
  void getConfigShouldReturnConfig() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(null);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");

    assertEquals(mockConfig, adapter.getConfig());
  }

  @Test
  void getSubscribedTopicsShouldReturnImmutableList() {
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("test-adapter.topics")).thenReturn(Topics.USER_CREATED);

    TestAdapter adapter = new TestAdapter(mockConfig, "test-adapter");
    List<String> topics = adapter.getSubscribedTopics();

    assertNotNull(topics);
    assertThrows(UnsupportedOperationException.class, () -> topics.add("new-topic"));
  }
}
