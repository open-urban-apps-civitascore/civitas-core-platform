/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.adapter;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for ConfigAdapter implementations. Provides common infrastructure including
 * configuration, event publishing, and topic management.
 */
public abstract class AbstractConfigAdapter implements ConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(AbstractConfigAdapter.class);

  protected static final String UNKNOWN_RESOURCE_TYPE_CODE = "UNKNOWN_RESOURCE_TYPE";
  protected static final String UNKNOWN_RESOURCE_TYPE_MSG = "Unknown resource type: ";
  protected static final String UNSUPPORTED_OPERATION_CODE = "UNSUPPORTED_OPERATION";
  protected static final String UNSUPPORTED_OPERATION_MSG = "Unknown operation: ";

  protected AdapterConfig config;
  protected EventPublisher eventPublisher;
  private String adapterName;
  private final List<String> subscribedTopics = new LinkedList<>();

  public void initialize(AdapterConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("AdapterConfig cannot be null");
    }
    this.adapterName = getName();
    if (adapterName == null || adapterName.trim().isEmpty()) {
      throw new IllegalArgumentException("Adapter name cannot be null or empty");
    }
    this.config = config;
    String topicsConfigKey = adapterName + ".topics";
    this.subscribedTopics.addAll(parseAndValidateTopics(topicsConfigKey));
  }

  /**
   * Gets a configuration property using the adapter name as prefix. For example, if adapter name is
   * "keycloak" and property is "url", this will read "keycloak.url" from configuration.
   *
   * @param property the property name (without adapter prefix)
   * @return the property value, or null if not found
   */
  protected String getAdapterProperty(String property) {
    return config.getProperty(adapterName + "." + property);
  }

  /**
   * Gets a configuration property using the adapter name as prefix with a default value.
   *
   * @param property the property name (without adapter prefix)
   * @param defaultValue the default value if property is not found
   * @return the property value, or defaultValue if not found
   */
  protected String getAdapterProperty(String property, String defaultValue) {
    return config.getProperty(adapterName + "." + property, defaultValue);
  }

  /**
   * Parses topics from configuration and validates them against the list of known topics.
   *
   * @param configKey the configuration key to read topics from
   * @return list of validated topics
   * @throws IllegalArgumentException if any configured topic is invalid
   */
  private List<String> parseAndValidateTopics(String configKey) {
    String topicsProperty = config.getProperty(configKey);

    if (topicsProperty == null || topicsProperty.trim().isEmpty()) {
      logger.warn("No topics configured for key: {}", configKey);
      return List.of();
    }

    List<String> topics = new ArrayList<>();
    String[] topicArray = topicsProperty.split(",");

    for (String topic : topicArray) {
      String trimmedTopic = topic.trim();
      if (!trimmedTopic.isEmpty()) {
        if (!Topics.isValidTopic(trimmedTopic)) {
          throw new IllegalArgumentException(
              String.format(
                  "Invalid topic '%s' configured for key '%s'. Must be one of: %s",
                  trimmedTopic, configKey, Topics.ALL_TOPICS));
        }
        topics.add(trimmedTopic);
      }
    }

    logger.info(
        "Parsed and validated {} topic(s) from configuration key '{}': {}",
        topics.size(),
        configKey,
        topics);

    return List.copyOf(topics);
  }

  @Override
  public List<String> getSubscribedTopics() {
    return Collections.unmodifiableList(subscribedTopics);
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    this.eventPublisher = publisher;
  }

  /**
   * Gets the configured EventPublisher. Subclasses should check if eventPublisher is not null
   * before using it.
   *
   * @return the EventPublisher instance, may be null if not yet set
   */
  protected EventPublisher getEventPublisher() {
    return eventPublisher;
  }

  /**
   * Gets the application configuration.
   *
   * @return the AdapterConfig instance
   */
  protected AdapterConfig getConfig() {
    return config;
  }
}
