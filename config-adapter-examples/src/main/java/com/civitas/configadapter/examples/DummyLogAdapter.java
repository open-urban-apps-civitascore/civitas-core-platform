package com.civitas.configadapter.examples;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple example adapter that only logs received events.
 * Useful for testing, debugging, and as a reference implementation.
 */
public class DummyLogAdapter implements ConfigAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DummyLogAdapter.class);

    private static final List<String> SUBSCRIBED_TOPICS = List.of(
        Topics.USER_CREATED,
        Topics.USER_UPDATED,
        Topics.USER_DELETED
    );

    public DummyLogAdapter(AppConfig config) {
        logger.info("DummyLogAdapter initialized - will log all received events");
        logger.info("Subscribed to {} topics: {}", SUBSCRIBED_TOPICS.size(), SUBSCRIBED_TOPICS);
    }

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
    }

    @Override
    public void processConfigEvent(ConfigEvent event) {
        logger.info("==================== DummyLogAdapter Event ====================");
        logger.info("Action:       {}", event.action());
        logger.info("Realm:        {}", event.realm());
        logger.info("ResourceType: {}", event.resourceType());
        logger.info("ResourceId:   {}", event.resourceId());
        logger.info("Data:         {}", event.data());
        logger.info("===============================================================");
    }

    @Override
    public void close() {
        logger.info("DummyLogAdapter closed");
    }
}
