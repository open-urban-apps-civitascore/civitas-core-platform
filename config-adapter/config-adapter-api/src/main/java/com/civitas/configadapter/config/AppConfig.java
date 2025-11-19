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
    String adaptersProperty = properties.getProperty("adapters");

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
    return properties.getProperty("eventhandler.class");
  }

  public String getEventConsumerClass() {
    return properties.getProperty("eventconsumer.class");
  }

  public String getEventPublisherClass() {
    return properties.getProperty("eventpublisher.class");
  }

  public int getHealthCheckPort() {
    String portProperty = properties.getProperty("healthcheck.port", "8080");
    try {
      return Integer.parseInt(portProperty);
    } catch (NumberFormatException e) {
      throw new RuntimeException(
          "Invalid healthcheck.port value: " + portProperty + ". Must be a valid integer.", e);
    }
  }

  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  public String getProperty(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }
}
