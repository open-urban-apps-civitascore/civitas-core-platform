package de.civitascore.portal.service;

import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.model.output.event.EventMetadata;
import de.civitascore.portal.model.output.event.TopicResolver;
import de.civitascore.portal.service.event.SynchronousEventPublisher;
import de.civitascore.portal.service.event.SynchronousEventPublisher.ConfigAdapterResult;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.annotation.Transactional;

public abstract class EventPublishingService<T, I extends BaseInputDTO> extends BaseService<T, I> {

  protected final SynchronousEventPublisher syncEventPublisher;
  protected final TopicResolver topicResolver;

  protected EventPublishingService(
      SynchronousEventPublisher syncEventPublisher, TopicResolver topicResolver) {
    this.syncEventPublisher = syncEventPublisher;
    this.topicResolver = topicResolver;
  }

  @Override
  @Transactional
  public T create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    T entity = getMapper().toEntity(preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);

    entity = getRepository().saveAndFlush(entity);

    ConfigAdapterResult result = preValidateWithExternalSystem(entity, "create");

    if (result != null && result.resourceId() != null) {
      updateExternalId(entity, result.resourceId());
      entity = getRepository().saveAndFlush(entity);
    }

    postSave(entity, preProcessedInput);

    return entity;
  }

  @Override
  @Transactional
  public T update(UUID id, I input) {
    T entity = findById(id);
    I preProcessedInput = preProcessUpdateInput(input, entity);
    getMapper().updateEntity(entity, preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);

    entity = getRepository().saveAndFlush(entity);

    ConfigAdapterResult result = preValidateWithExternalSystem(entity, "update");

    if (result != null && result.resourceId() != null) {
      updateExternalId(entity, result.resourceId());
      entity = getRepository().saveAndFlush(entity);
    }

    postSave(entity, preProcessedInput);

    return entity;
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    T entity = findById(id);

    preValidateWithExternalSystem(entity, "delete");

    getRepository().deleteById(id);

    postDelete(entity);
  }

  protected ConfigAdapterResult preValidateWithExternalSystem(T entity, String operation) {
    try {
      String aggregateType = getAggregateType();
      String topic = topicResolver.resolve(aggregateType, operation);
      DomainEvent<?> event = createDomainEvent(entity, operation);

      ConfigAdapterResult result = syncEventPublisher.publishAndWaitForResult(topic, event);

      if (!result.isSuccess()) {
        throw new ExternalSystemRejectionException(
            String.format(
                "External system rejected %s: %s - %s",
                operation, result.errorCode(), result.message()));
      }

      return result;

    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "External system unavailable - cannot complete operation", e);
    }
  }

  protected DomainEvent<?> createDomainEvent(T entity, String operation) {
    UUID entityId = getEntityId(entity);

    if (entityId == null) {
      throw new IllegalStateException("Cannot create domain event: entity ID is null");
    }

    Object kafkaRepresentation = toKafkaRepresentation(entity);
    String realm = getRealm(entity);
    String aggregateType = getAggregateType();

    EventMetadata metadata = new EventMetadata(realm, null);

    return DomainEvent.builder()
        .eventId(UUID.randomUUID())
        .eventType(String.format("%s.%s", aggregateType.toLowerCase(), operation))
        .entityId(entityId)
        .aggregateType(aggregateType)
        .operation(operation)
        .payload(kafkaRepresentation)
        .metadata(metadata)
        .schemaVersion(1)
        .timestamp(Instant.now())
        .build();
  }

  protected abstract String getAggregateType();

  protected abstract String getRealm(T entity);

  protected abstract Object toKafkaRepresentation(T entity);

  protected abstract UUID getEntityId(T entity);

  protected void updateExternalId(T entity, String externalId) {}
}
