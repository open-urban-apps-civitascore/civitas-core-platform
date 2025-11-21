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
package com.civitas.configadapter.core;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.model.ConfigEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal helper class that processes CloudEvents and delegates to ConfigAdapter. Handles
 * deserialization of CloudEvent data into ConfigEvent.
 */
public class CloudEventProcessor {

  private static final Logger logger = LoggerFactory.getLogger(CloudEventProcessor.class);

  private final ConfigAdapter configAdapter;
  private final ObjectMapper objectMapper;

  public CloudEventProcessor(ConfigAdapter configAdapter) {
    this.configAdapter = configAdapter;
    this.objectMapper = new ObjectMapper();
  }

  public void handleEvent(String topic, CloudEvent cloudEvent) {
    try {
      logger.info(
          "Received CloudEvent - ID: {}, Type: {}, Source: {}",
          cloudEvent.getId(),
          cloudEvent.getType(),
          cloudEvent.getSource());

      if (cloudEvent.getData() == null) {
        logger.warn("CloudEvent has no data, skipping: {}", cloudEvent.getId());
        return;
      }

      String jsonData = new String(cloudEvent.getData().toBytes());
      logger.debug("CloudEvent data: {}", jsonData);

      ConfigEvent configEvent = objectMapper.readValue(jsonData, ConfigEvent.class);
      configAdapter.processConfigEvent(topic, configEvent);

    } catch (Exception e) {
      logger.error("Error processing CloudEvent: {}", cloudEvent.getId(), e);
    }
  }
}
