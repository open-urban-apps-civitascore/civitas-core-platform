package com.civitas.configadapter.core;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.messaging.CloudEventHandler;
import com.civitas.configadapter.model.ConfigEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudEventProcessor implements CloudEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(CloudEventProcessor.class);

    private final ConfigAdapter configAdapter;
    private final ObjectMapper objectMapper;

    public CloudEventProcessor(ConfigAdapter configAdapter) {
        this.configAdapter = configAdapter;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handleEvent(CloudEvent cloudEvent) {
        try {
            logger.info("Received CloudEvent - ID: {}, Type: {}, Source: {}",
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
            configAdapter.processConfigEvent(configEvent);

        } catch (Exception e) {
            logger.error("Error processing CloudEvent: {}", cloudEvent.getId(), e);
        }
    }
}
