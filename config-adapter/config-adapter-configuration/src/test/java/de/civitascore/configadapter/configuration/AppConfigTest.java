/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  @Test
  void shouldReturnPropertyFromPropertiesFile() {
    AppConfig config = createAppConfig("test.key", "test-value");

    assertEquals("test-value", config.getProperty("test.key"));
  }

  @Test
  void shouldReturnDefaultValueWhenPropertyNotFound() {
    Map<String, Object> props = Collections.emptyMap();
    AppConfig config = new AppConfig(new MapConfiguration(props));

    assertEquals("default", config.getProperty("missing.key", "default"));
  }

  @Test
  void shouldReturnNullWhenPropertyNotFoundAndNoDefault() {
    Map<String, Object> props = Collections.emptyMap();
    AppConfig config = new AppConfig(new MapConfiguration(props));

    assertNull(config.getProperty("missing.key"));
  }

  @Test
  void shouldConvertPropertyKeyToEnvironmentVariableName() {
    AppConfig config = createAppConfig("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

    assertEquals("localhost:9092", config.getProperty("kafka.bootstrap.servers"));
  }

  @Test
  void shouldGetHealthCheckPortFromProperties() {
    AppConfig config = createAppConfig("healthcheck.port", "9090");

    assertEquals(9090, config.getHealthCheckPort());
  }

  @Test
  void shouldGetDefaultHealthCheckPort() {
    Map<String, Object> props = Collections.emptyMap();
    AppConfig config = new AppConfig(new MapConfiguration(props));

    assertEquals(8080, config.getHealthCheckPort());
  }

  @Test
  void shouldGetAdapterClasses() {
    AppConfig config = createAppConfig("adapters", "com.example.Adapter1,com.example.Adapter2");

    assertEquals(2, config.getAdapterNames().size());
    assertEquals("com.example.Adapter1", config.getAdapterNames().get(0));
    assertEquals("com.example.Adapter2", config.getAdapterNames().get(1));
  }

  @Test
  void shouldHandleEmptyAdaptersProperty() {
    AppConfig config = createAppConfig("adapters", "");

    assertEquals(0, config.getAdapterNames().size());
  }

  @Test
  void shouldTrimAdapterClassNames() {
    AppConfig config = createAppConfig("adapters", " com.example.Adapter1 , com.example.Adapter2 ");

    assertEquals(2, config.getAdapterNames().size());
    assertEquals("com.example.Adapter1", config.getAdapterNames().get(0));
    assertEquals("com.example.Adapter2", config.getAdapterNames().get(1));
  }

  @Test
  void shouldGetEventHandlerClass() {
    AppConfig config = createAppConfig("eventhandler.name", "com.example.EventHandler");

    assertEquals("com.example.EventHandler", config.getEventHandlerName());
  }

  @Test
  void shouldGetEventConsumerName() {
    AppConfig config = createAppConfig("eventconsumer.name", "com.example.EventConsumer");

    assertEquals("com.example.EventConsumer", config.getEventConsumerName());
  }

  @Test
  void shouldGetEventPublisherName() {
    AppConfig config = createAppConfig("eventpublisher.name", "com.example.EventPublisher");

    assertEquals("com.example.EventPublisher", config.getEventPublisherName());
  }

  @Test
  void shouldThrowExceptionWhenHealthCheckPortIsInvalid() {
    AppConfig config = createAppConfig("healthcheck.port", "invalid-port");

    RuntimeException exception = assertThrows(RuntimeException.class, config::getHealthCheckPort);
    assertTrue(exception.getMessage().contains("Invalid healthcheck.port value"));
    assertTrue(exception.getMessage().contains("invalid-port"));
  }

  @Test
  void shouldReturnEmptyListWhenAdaptersPropertyIsNull() {
    Map<String, Object> props = Collections.emptyMap();
    AppConfig config = new AppConfig(new MapConfiguration(props));

    assertEquals(0, config.getAdapterNames().size());
  }

  private AppConfig createAppConfig(String key, String value) {
    Map<String, Object> props = Collections.singletonMap(key, value);
    Configuration config = new MapConfiguration(props);
    return new AppConfig(config);
  }
}
