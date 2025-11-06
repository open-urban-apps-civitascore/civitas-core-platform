package com.civitas.configadapter.adapter;

import com.civitas.configadapter.model.ConfigEvent;

import java.util.List;

public interface ConfigAdapter extends AutoCloseable {

    void processConfigEvent(ConfigEvent event);

    default List<String> getSubscribedTopics() {
        return List.of();
    }

    @Override
    default void close() {
    }
}
