package de.civitascore.portal.service;

import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.model.output.event.EventMetadata;
import de.civitascore.portal.model.output.event.TopicResolver;
import de.civitascore.portal.service.event.EventPublisherService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional(readOnly = true)
public abstract class EventPublishingService<T, I extends BaseInputDTO> extends BaseService<T, I> {
  protected final EventPublisherService events;
  protected final TopicResolver topicResolver;

  protected EventPublishingService(EventPublisherService events, TopicResolver topicResolver) {
    this.events = events;
    this.topicResolver = topicResolver;
  }

  @Override
  protected final void postSave(T entity, I input) {
    String operation = detectOperation();
    publishEvent(entity, operation);
    super.postSave(entity, input);
  }

  @Override
  protected final void postDelete(T entity) {
    publishEvent(entity, "delete");
    super.postDelete(entity);
  }

  protected abstract String getAggregateType();

  protected abstract String getRealm(T entity);

  protected abstract UUID getEntityId(T entity);

  /**
   * Converts the entity to a Kafka-specific representation.
   *
   * <p>Override this method to send a custom DTO instead of the full entity. By default, returns
   * the entity itself.
   *
   * <p>Example:
   *
   * <pre>
   * &#64;Override
   * protected UserEventDTO toKafkaRepresentation(User entity) {
   *   return new UserEventDTO(entity.getId(), entity.getEmail(), entity.getFullName());
   * }
   * </pre>
   *
   * @param entity the entity to convert
   * @return the Kafka representation (can be entity or custom DTO)
   */
  protected Object toKafkaRepresentation(T entity) {
    return entity;
  }

  /**
   * Extracts request metadata for event correlation.
   *
   * <p>Returns empty map if no HTTP context is available (e.g., scheduled tasks, batch jobs).
   * Includes user context for audit trails and optional trace ID for distributed tracing.
   */
  protected Map<String, String> getCorrelationIds() {
    Map<String, String> metadata = new HashMap<>();

    // Add trace ID for distributed tracing (if available)
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      String traceId = attributes.getRequest().getHeader("X-B3-TraceId");
      if (traceId != null) {
        metadata.put("traceId", traceId);
      }
    }

    // Add user context for audit trail
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
      metadata.put("userId", auth.getName());
    }

    return metadata;
  }

  /**
   * Detects operation type from HTTP request method.
   *
   * <p>Falls back to "update" if no request context is available (e.g., scheduled tasks).
   */
  private String detectOperation() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      String method = attributes.getRequest().getMethod();
      return switch (method) {
        case "POST" -> "create";
        case "PUT", "PATCH" -> "update";
        case "DELETE" -> "delete";
        default -> "update";
      };
    }
    return "update"; // fallback for non-HTTP contexts
  }

  private void publishEvent(T entity, String operation) {
    Object kafkaPayload = toKafkaRepresentation(entity);
    DomainEvent<?> event =
        new DomainEvent<>(
            UUID.randomUUID(),
            topicResolver.resolve(getAggregateType(), operation),
            getEntityId(entity),
            getAggregateType(),
            operation,
            kafkaPayload,
            new EventMetadata(getRealm(entity), getCorrelationIds()),
            1,
            Instant.now());
    events.publish(event.eventType(), event);
  }
}
