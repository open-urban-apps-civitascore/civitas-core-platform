package de.civitascore.portal.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.configuration.OutboxConfig;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {
  private final OutboxEventRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final OutboxConfig config;

  @Transactional
  public void publish(String topic, DomainEvent<?> event) {
    OutboxEvent outboxEvent = new OutboxEvent();
    outboxEvent.setTopic(topic);
    outboxEvent.setAggregateType(event.aggregateType());
    outboxEvent.setAggregateId(event.entityId());

    try {
      String payload = objectMapper.writeValueAsString(event);

      // Warn if payload is very large
      if (payload.length() > config.getPayloadSizeWarningBytes()) {
        log.warn(
            "Event payload very large: {} bytes for event type {} (threshold: {} bytes)",
            payload.length(),
            event.eventType(),
            config.getPayloadSizeWarningBytes());
      }

      outboxEvent.setPayload(payload);
      outboxRepository.save(outboxEvent);

      log.debug("Event {} queued for topic {}", event.eventId(), topic);
    } catch (Exception e) {
      log.error("Failed to serialize event: {}", event, e);
      throw new EventSerializationException(event, e);
    }
  }

  /** Exception thrown when event serialization fails. */
  public static class EventSerializationException extends RuntimeException {
    public EventSerializationException(DomainEvent<?> event, Throwable cause) {
      super(
          "Failed to serialize event: " + event.eventType() + " (ID: " + event.eventId() + ")",
          cause);
    }
  }
}
