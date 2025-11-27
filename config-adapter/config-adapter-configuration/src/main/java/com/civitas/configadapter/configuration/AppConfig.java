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
package com.civitas.configadapter.configuration;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.EnvironmentConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

public record AppConfig(Configuration configuration) implements ApplicationConfig {

  public AppConfig(String configFile) {
    this(buildConfiguration(configFile));
  }

  private static Configuration buildConfiguration(String configFile) {
    try {
      CompositeConfiguration compositeConfig = new CompositeConfiguration();
      EnvironmentConfiguration envConfig = new EnvironmentConfiguration();
      compositeConfig.addConfiguration(envConfig);

      Configurations configs = new Configurations();
      URL configFileResource = AppConfig.class.getClassLoader().getResource(configFile);
      if (configFileResource == null) {
        throw new RuntimeException("Unable to find " + configFile);
      }
      PropertiesConfiguration propsConfig = configs.properties(configFileResource);
      compositeConfig.addConfiguration(propsConfig);

      return compositeConfig;
    } catch (ConfigurationException ex) {
      throw new RuntimeException("Failed to load configuration from " + configFile, ex);
    }
  }

  public List<String> getAdapterNames() {
    String adaptersProperty = getProperty("adapters");
    if (adaptersProperty != null && !adaptersProperty.trim().isEmpty()) {
      return Arrays.stream(adaptersProperty.split(",")).map(String::trim).toList();
    }
    return Collections.emptyList();
  }

  public String getEventHandlerName() {
    return getProperty("eventhandler.name");
  }

  public String getEventConsumerName() {
    return getProperty("eventconsumer.name");
  }

  public String getEventPublisherName() {
    return getProperty("eventpublisher.name");
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
    String value = configuration.getString(envKey);

    if (value != null && !value.isEmpty()) {
      return value;
    }

    value = configuration.getString(key);
    return value != null ? value : defaultValue;
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
  private static String toEnvironmentVariableName(String propertyKey) {
    return propertyKey.toUpperCase().replace('.', '_').replace('-', '_');
  }
}
