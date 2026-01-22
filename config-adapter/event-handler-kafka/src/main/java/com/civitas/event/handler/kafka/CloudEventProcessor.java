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
package com.civitas.event.handler.kafka;

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
    this.objectMapper = ObjectMapperFactory.createObjectMapper();
  }

  public void handleEvent(String topic, CloudEvent cloudEvent) throws Exception {
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
      throw e;
    }
  }
}
