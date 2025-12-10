package de.civitascore.portal.model.output.event;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Kafka topic names for domain events.
 *
 * <p>Supports optional topic prefix for multi-environment deployments.
 */
@Component
public class TopicResolver {
  private static final String DELIMITER = ".";

  @Value("${kafka.topic-prefix:}")
  private String topicPrefix;

  /**
   * Resolves topic name from aggregate type and operation.
   *
   * @param aggregateType the aggregate type (e.g., "User")
   * @param operation the operation (e.g., "created")
   * @return topic name (e.g., "User.created" or "prod.User.created" with prefix)
   */
  public String resolve(String aggregateType, String operation) {
    Objects.requireNonNull(aggregateType, "aggregateType must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    String topic = aggregateType + DELIMITER + operation;
    return applyPrefix(topic);
  }

  private String applyPrefix(String topic) {
    if (topicPrefix == null || topicPrefix.trim().isEmpty()) {
      return topic;
    }
    return topicPrefix.trim() + DELIMITER + topic;
  }
}
