package com.civitas.configadapter.examples;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Simple example adapter that logs received events and publishes result events.
 * Useful for testing, debugging, and as a reference implementation.
 * Demonstrates how to use EventPublisher to send result/error events.
 */
public class DummyLogAdapter extends AbstractConfigAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DummyLogAdapter.class);

    private static final List<String> SUBSCRIBED_TOPICS = List.of(
        Topics.USER_CREATED,
        Topics.USER_UPDATED,
        Topics.USER_DELETED
    );

    public DummyLogAdapter(AppConfig config) {
        super(config);
        logger.info("DummyLogAdapter initialized - will log all received events");
        logger.info("Subscribed to {} topics: {}", SUBSCRIBED_TOPICS.size(), SUBSCRIBED_TOPICS);
    }

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
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
                CloudEvent resultEvent = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withSource(URI.create("civitas.config-adapter.dummy-log"))
                    .withType("core.civitas.idm.processing.result")
                    .withTime(OffsetDateTime.now())
                    .withDataContentType("application/json")
                    .withExtension("originalEventId", event.metadata().messageId())
                    .withExtension("status", "processed")
                    .withExtension("adapter", "DummyLogAdapter")
                    .build();

				getEventPublisher().publish(resultTopic, resultEvent);
                logger.debug("Published result event to topic {} for {}", resultTopic, event.metadata().messageId());
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
