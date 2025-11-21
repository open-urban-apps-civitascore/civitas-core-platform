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
package com.civitas.configadapter.examples;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example adapter that logs received events and publishes result events. Useful for testing,
 * debugging, and as a reference implementation. Demonstrates how to use EventPublisher to send
 * result/error events.
 */
public class DummyLogAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(DummyLogAdapter.class);

  private static final String ADAPTER_NAME = "dummylog";

  public DummyLogAdapter(AdapterConfig config) {
    super(config, ADAPTER_NAME);
    logger.info("DummyLogAdapter '{}' initialized - will log all received events", getName());
    logger.info("Subscribed to {} topics: {}", getSubscribedTopics().size(), getSubscribedTopics());
  }

  @Override
  public void processConfigEvent(String topic, ConfigEvent event) {
    logger.info("==================== DummyLogAdapter Event ====================");
    logger.info("Operation:     {}", event.payload().operation());
    logger.info("ResourceType: 	{}", event.payload().targetComponent());
    logger.info("TargetResource:{}", event.payload().targetResource());
    logger.info("MessageId:   	{}", event.metadata().messageId());
    logger.info("Data:         	{}", event.payload().config());
    logger.info("ResultTopic:  	{}", event.metadata().resultTopic());
    logger.info("===============================================================");

    // Publish a result event if resultTopic is specified
    String resultTopic = event.metadata().resultTopic();
    if (getEventPublisher() != null && topic != null && !resultTopic.isEmpty()) {
      try {
        ConfigResultEvent resultEvent =
            ConfigResultEvent.success(
                event.metadata().correlationId(),
                event.metadata().messageId(),
                "Event logged by DummyLogAdapter",
                null,
                event.payload().operation(),
                event.payload().targetResource(),
                "civitas.config-adapter.dummy-log");

        getEventPublisher().publish(resultTopic, resultEvent);
        logger.debug(
            "Published result event to topic {} for {}", resultTopic, event.metadata().messageId());
      } catch (Exception e) {
        logger.error("Failed to publish result event", e);
      }
    }
  }

  @Override
  public void close() {
    logger.info("DummyLogAdapter closed");
  }
}
