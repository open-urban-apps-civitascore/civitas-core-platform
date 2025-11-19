package com.civitas.configadapter.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public record AppConfig(Properties properties) {

  public AppConfig(String configFile) {
    this(new Properties());
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
      if (input == null) {
        throw new RuntimeException("Unable to find " + configFile);
      }
      properties.load(input);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to load configuration", ex);
    }
  }

  public List<String> getAdapterClasses() {
    List<String> adapterClasses = new ArrayList<>();
    String adaptersProperty = getProperty("adapters");

    if (adaptersProperty != null && !adaptersProperty.trim().isEmpty()) {
      String[] adapters = adaptersProperty.split(",");
      for (String adapter : adapters) {
        String trimmed = adapter.trim();
        if (!trimmed.isEmpty()) {
          adapterClasses.add(trimmed);
        }
      }
    }
    return adapterClasses;
  }

  public String getEventHandlerClass() {
    return getProperty("eventhandler.class");
  }

  public String getEventConsumerClass() {
    return getProperty("eventconsumer.class");
  }

  public String getEventPublisherClass() {
    return getProperty("eventpublisher.class");
  }

  public int getHealthCheckPort() {
    String portProperty = getProperty("healthcheck.port", "8080");
    try {
      return Integer.parseInt(portProperty);
    } catch (NumberFormatException e) {
      throw new RuntimeException(
          "Invalid healthcheck.port value: " + portProperty + ". Must be a valid integer.", e);
    }
  }

  /**
   * Gets a property value with environment variable override support.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Environment variable (property key converted to uppercase with dots/dashes replaced by
   *       underscores)
   *   <li>Properties file value
   * </ol>
   *
   * <p>Example: Property "kafka.bootstrap.servers" can be overridden by environment variable
   * "KAFKA_BOOTSTRAP_SERVERS"
   *
   * @param key the property key
   * @return the property value, or null if not found
   */
  public String getProperty(String key) {
    return getProperty(key, null);
  }

  /**
   * Gets a property value with environment variable override support and default fallback.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Environment variable (property key converted to uppercase with dots/dashes replaced by
   *       underscores)
   *   <li>Properties file value
   *   <li>Default value
   * </ol>
   *
   * @param key the property key
   * @param defaultValue the default value if not found
   * @return the property value, or defaultValue if not found
   */
  public String getProperty(String key, String defaultValue) {
    String envKey = toEnvironmentVariableName(key);
    String envValue = System.getenv(envKey);

    if (envValue != null && !envValue.isEmpty()) {
      return envValue;
    }

    return properties.getProperty(key, defaultValue);
  }

  /**
   * Converts a property key to an environment variable name.
   *
   * <p>Conversion rules:
   *
   * <ul>
   *   <li>Convert to uppercase
   *   <li>Replace dots (.) with underscores (_)
   *   <li>Replace dashes (-) with underscores (_)
   * </ul>
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"kafka.bootstrap.servers" → "KAFKA_BOOTSTRAP_SERVERS"
   *   <li>"healthcheck.port" → "HEALTHCHECK_PORT"
   *   <li>"keycloak.client.id" → "KEYCLOAK_CLIENT_ID"
   * </ul>
   *
   * @param propertyKey the property key
   * @return the environment variable name
   */
  private String toEnvironmentVariableName(String propertyKey) {
    return propertyKey.toUpperCase().replace('.', '_').replace('-', '_');
  }
}
