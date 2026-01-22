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
package com.civitas.configadapter.examples;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example adapter that logs received events and publishes result events. Useful for testing,
 * debugging, and as a reference implementation. Demonstrates how to use EventPublisher to send
 * result/error events.
 *
 * <p>This adapter also demonstrates the exception handling pattern using topic-based triggers:
 *
 * <ul>
 *   <li>Topics containing "fatal" - Throws FatalAdapterException (goes directly to DLQ)
 *   <li>Topics containing "retry" - Throws RetryableAdapterException (triggers retry loop)
 *   <li>Other topics - Normal processing, no exceptions thrown
 * </ul>
 *
 * <p>Example: {@code processConfigEvent("test.fatal.error", event)} throws FatalAdapterException
 */
public class DummyLogAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(DummyLogAdapter.class);

  private static final String DUMMY_LOG_RESULT_TYPE = "core.civitas.processing.result";
  private static final String DUMMY_LOG_SOURCE = "civitas.config-adapter.dummy-log";

  public static final String ADAPTER_NAME = "dummylog";

  public DummyLogAdapter() {}

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    logger.info("DummyLogAdapter '{}' initialized - will log all received events", getName());
    logger.info("Subscribed to {} topics: {}", getSubscribedTopics().size(), getSubscribedTopics());
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void processConfigEvent(String topic, ConfigEvent event) {
    logger.info("==================== DummyLogAdapter Event ====================");
    logger.info("Operation:     {}", event.payload().operation());
    logger.info("ResourceType: 	{}", event.payload().targetComponent());
    logger.info("TargetResource:{}", event.payload().targetResource());
    logger.info("MessageId:   	{}", event.metadata().messageId());
    // keep in mind config could contain sensitive data (e.g. password) not for production
    logger.info("Data:         	{}", event.payload().config());
    logger.info("ResultTopic:  	{}", event.metadata().resultTopic());
    logger.info("===============================================================");

    // Topic-based exception handling for testing/demonstration
    if (topic != null && topic.contains("fatal")) {
      logger.warn("Topic contains 'fatal' - throwing FatalAdapterException");
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, null, "Simulated fatal error for topic: " + topic);
    }

    if (topic != null && topic.contains("retry")) {
      logger.warn("Topic contains 'retry' - throwing RetryableAdapterException");
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, null, ADAPTER_NAME, 503);
    }

    // Publish a result event if resultTopic is specified
    String resultTopic = event.metadata().resultTopic();
    if (getEventPublisher() != null
        && topic != null
        && resultTopic != null
        && !resultTopic.isEmpty()) {
      try {
        ConfigResultEvent resultEvent =
            ConfigResultEvent.success(
                event.metadata().correlationId(),
                event.metadata().messageId(),
                "Event logged by DummyLogAdapter",
                null,
                event.payload().operation(),
                event.payload().targetResource(),
                DUMMY_LOG_SOURCE,
                DUMMY_LOG_RESULT_TYPE);

        getEventPublisher().publish(resultTopic, resultEvent);
        logger.debug(
            "Published result event to topic {} for {}", resultTopic, event.metadata().messageId());
      } catch (Exception e) {
        logger.error("Failed to publish result event", e);
        throw new FatalAdapterException(
            AdapterErrorCode.UNKNOWN_ERROR, e, "Event processing failed: unable to publish result");
      }
    }
  }

  @Override
  public void close() {
    logger.info("DummyLogAdapter closed");
  }
}
