package com.civitas.configadapter.adapter;

import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;
import java.util.List;

public interface ConfigAdapter extends AutoCloseable {

  /**
   * Returns the name of this adapter. The name is used as a prefix for configuration properties
   * (e.g., "keycloak.topics").
   *
   * @return the adapter name (e.g., "keycloak", "dummylog")
   */
  String getName();

  void processConfigEvent(String topic, ConfigEvent event);

  List<String> getSubscribedTopics();

  void setEventPublisher(EventPublisher publisher);

  @Override
  default void close() {}
}
