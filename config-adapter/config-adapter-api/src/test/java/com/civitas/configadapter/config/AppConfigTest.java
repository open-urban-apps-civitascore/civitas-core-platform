package com.civitas.configadapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppConfigTest {

  @Test
  void shouldReturnPropertyFromPropertiesFile() {
    Properties props = new Properties();
    props.setProperty("test.key", "test-value");

    AppConfig config = new AppConfig(props);

    assertEquals("test-value", config.getProperty("test.key"));
  }

  @Test
  void shouldReturnDefaultValueWhenPropertyNotFound() {
    Properties props = new Properties();

    AppConfig config = new AppConfig(props);

    assertEquals("default", config.getProperty("missing.key", "default"));
  }

  @Test
  void shouldReturnNullWhenPropertyNotFoundAndNoDefault() {
    Properties props = new Properties();

    AppConfig config = new AppConfig(props);

    assertNull(config.getProperty("missing.key"));
  }

  @Test
  void shouldConvertPropertyKeyToEnvironmentVariableName() {
    Properties props = new Properties();
    props.setProperty("kafka.bootstrap.servers", "localhost:9092");

    AppConfig config = new AppConfig(props);

    assertEquals("localhost:9092", config.getProperty("kafka.bootstrap.servers"));
  }

  @Test
  void shouldGetHealthCheckPortFromProperties() {
    Properties props = new Properties();
    props.setProperty("healthcheck.port", "9090");

    AppConfig config = new AppConfig(props);

    assertEquals(9090, config.getHealthCheckPort());
  }

  @Test
  void shouldGetDefaultHealthCheckPort() {
    Properties props = new Properties();

    AppConfig config = new AppConfig(props);

    assertEquals(8080, config.getHealthCheckPort());
  }

  @Test
  void shouldGetAdapterClasses() {
    Properties props = new Properties();
    props.setProperty("adapters", "com.example.Adapter1,com.example.Adapter2");

    AppConfig config = new AppConfig(props);

    assertEquals(2, config.getAdapterClasses().size());
    assertEquals("com.example.Adapter1", config.getAdapterClasses().get(0));
    assertEquals("com.example.Adapter2", config.getAdapterClasses().get(1));
  }

  @Test
  void shouldHandleEmptyAdaptersProperty() {
    Properties props = new Properties();
    props.setProperty("adapters", "");

    AppConfig config = new AppConfig(props);

    assertEquals(0, config.getAdapterClasses().size());
  }

  @Test
  void shouldTrimAdapterClassNames() {
    Properties props = new Properties();
    props.setProperty("adapters", " com.example.Adapter1 , com.example.Adapter2 ");

    AppConfig config = new AppConfig(props);

    assertEquals(2, config.getAdapterClasses().size());
    assertEquals("com.example.Adapter1", config.getAdapterClasses().get(0));
    assertEquals("com.example.Adapter2", config.getAdapterClasses().get(1));
  }

  @Test
  void shouldGetEventHandlerClass() {
    Properties props = new Properties();
    props.setProperty("eventhandler.class", "com.example.EventHandler");

    AppConfig config = new AppConfig(props);

    assertEquals("com.example.EventHandler", config.getEventHandlerClass());
  }

  @Test
  void shouldGetEventConsumerClass() {
    Properties props = new Properties();
    props.setProperty("eventconsumer.class", "com.example.EventConsumer");

    AppConfig config = new AppConfig(props);

    assertEquals("com.example.EventConsumer", config.getEventConsumerClass());
  }

  @Test
  void shouldGetEventPublisherClass() {
    Properties props = new Properties();
    props.setProperty("eventpublisher.class", "com.example.EventPublisher");

    AppConfig config = new AppConfig(props);

    assertEquals("com.example.EventPublisher", config.getEventPublisherClass());
  }
}
