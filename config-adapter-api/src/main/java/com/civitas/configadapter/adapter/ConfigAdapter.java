package com.civitas.configadapter.adapter;

import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;

import java.util.List;

public interface ConfigAdapter extends AutoCloseable {

	
    void processConfigEvent(String topic, ConfigEvent event);

    List<String> getSubscribedTopics();
 
    void setEventPublisher(EventPublisher publisher);

    @Override
    default void close() {
    }
}
