package de.civitascore.portal.service;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abstract base service that extends {@link BaseService} with synchronous external system
 * validation via config adapter events. Overrides create, update, and delete to publish a config
 * event and wait for the adapter's response before committing the transaction. Subclasses must
 * provide topic resolution, config value mapping, and realm extraction.
 */
public abstract class EventPublishingService<T, I extends BaseInputDTO> extends BaseService<T, I> {

  protected final ConfigEventPublisherService configEventPublisher;
  protected final EventProperties eventProperties;

  protected EventPublishingService(
      ConfigEventPublisherService configEventPublisher, EventProperties eventProperties) {
    this.configEventPublisher = configEventPublisher;
    this.eventProperties = eventProperties;
  }

  @Override
  @Transactional
  public T create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    T entity = getMapper().toEntity(preProcessedInput);
    ConfigValue preSaveConfigValue = toConfigValuePreSave(entity, preProcessedInput);
    return publishToExternalSystemAndSave(preProcessedInput, entity, "create", preSaveConfigValue);
  }

  @Override
  @Transactional
  public T update(UUID id, I input) {
    T entity = findByIdOrThrow(id);
    I preProcessedInput = preProcessUpdateInput(input, entity);
    ConfigValue preSaveConfigValue = toConfigValuePreSave(entity, preProcessedInput);
    getMapper().updateEntity(entity, preProcessedInput);
    return publishToExternalSystemAndSave(preProcessedInput, entity, "update", preSaveConfigValue);
  }

  private T publishToExternalSystemAndSave(
      I preProcessedInput, T entity, String operation, ConfigValue preSaveConfigValue) {
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);

    entity = getRepository().saveAndFlush(entity);

    entity = prePublish(entity, preProcessedInput);

    ConfigResultEvent result =
        preValidateWithExternalSystem(entity, preProcessedInput, operation, preSaveConfigValue);

    if (result != null && !Strings.isBlank(result.resourceId())) {
      updateExternalId(entity, result.resourceId());
      entity = getRepository().saveAndFlush(entity);
    }

    return postSave(entity, preProcessedInput);
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    T entity = findByIdOrThrow(id);

    preValidateWithExternalSystem(entity, null, "delete", null);

    getRepository().deleteById(id);

    postDelete(entity);
  }

  /**
   * Publishes a config event for the given entity and operation, then blocks until the config
   * adapter responds or a timeout is reached.
   *
   * @param entity the entity to publish
   * @param input the entity extending {@link BaseInputDTO} that has been used for the modification
   * @param operation the operation name ("create", "update", or "delete")
   * @param preSaveConfigValue nullable {@link ConfigValue} that has been passed from {@link
   *     #toConfigValuePreSave(Object, BaseInputDTO)}
   * @return the config adapter result, or {@code null} if no publisher is configured
   * @throws ExternalSystemRejectionException if the config adapter rejects the operation
   * @throws ExternalSystemTimeoutException if the config adapter does not respond in time
   */
  protected ConfigResultEvent preValidateWithExternalSystem(
      T entity, I input, String operation, ConfigValue preSaveConfigValue) {
    try {
      Topics topic = resolveTopic(operation);
      String targetComponent = getTargetComponent();
      String targetResource = getRealm(entity);
      Operation configOperation = resolveOperation(operation);
      String configPath = getConfigPath();
      ConfigValue configValue = toConfigValuePostSave(entity, input, preSaveConfigValue);

      CompletableFuture<ConfigResultEvent> futureResult =
          configEventPublisher.publishConfigEvent(
              topic, targetComponent, targetResource, configOperation, configPath, configValue);

      ConfigResultEvent result =
          futureResult.get(eventProperties.configAdapterTimeoutSeconds(), TimeUnit.SECONDS);

      if (result != null && result.status() == ConfigResultEvent.Status.FAILURE) {
        throw new ExternalSystemRejectionException(
            "Config Adapter rejected %s: %s - %s"
                .formatted(operation, result.errorCode(), result.message()));
      }

      return result;

    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "Config Adapter unavailable - cannot complete operation (timeout)", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException("Config Adapter request was interrupted", e);
    } catch (ExternalSystemRejectionException | ExternalSystemTimeoutException e) {
      throw e;
    } catch (ExecutionException e) {
      throw new ExternalSystemRejectionException(
          "Failed to validate with Config Adapter: " + e.getMessage());
    }
  }

  private Operation resolveOperation(String operation) {
    return switch (operation.toLowerCase()) {
      case "create" -> Operation.CREATE;
      case "update" -> Operation.UPDATE;
      case "delete" -> Operation.DELETE;
      default -> throw new IllegalArgumentException("Unknown operation: " + operation);
    };
  }

  /**
   * Maps a CRUD operation name to the corresponding Kafka topic.
   *
   * @param operation the operation name ("create", "update", or "delete")
   * @return the matching {@link Topics} enum value
   */
  protected abstract Topics resolveTopic(String operation);

  /**
   * Returns the target component identifier for config events (e.g., "user", "group", "role").
   *
   * @return the target component name
   */
  protected abstract String getTargetComponent();

  /**
   * Returns the target resource context (e.g., Keycloak realm) for the given entity.
   *
   * @param entity the entity being published
   * @return the target resource identifier
   */
  protected abstract String getRealm(T entity);

  /**
   * Returns the configuration path used in the config event (e.g., "/users", "/groups").
   *
   * @return the config path
   */
  protected abstract String getConfigPath();

  /**
   * Optional pre-save hook, called during updates before the entity is mutated by the mapper.
   * Receives the entity in its current (old) state and the incoming input. The returned value is
   * passed as {@code preSaveConfigValue} to {@link #toConfigValuePostSave} to enable
   * change-detection (e.g. detecting an email address change). Default returns {@code null}.
   */
  protected ConfigValue toConfigValuePreSave(T entity, I input) {
    return null;
  }

  /**
   * Builds the {@link ConfigValue} to sync to the external system after save and prePublish.
   *
   * @param entity the entity in its post-save state
   * @param input the input DTO ({@code null} for delete operations)
   * @param preSaveConfigValue value returned by {@link #toConfigValuePreSave} ({@code null} for
   *     create/delete operations, or when the pre-save hook returns {@code null})
   */
  protected abstract ConfigValue toConfigValuePostSave(
      T entity, I input, ConfigValue preSaveConfigValue);

  /**
   * Extracts the entity ID, used for correlation in config adapter events.
   *
   * @param entity the entity
   * @return the entity's UUID
   */
  protected abstract UUID getEntityId(T entity);

  /**
   * Hook called after the entity is saved and flushed but before publishing to the external system.
   * Subclasses can override to perform additional setup (e.g., resolving relationships).
   *
   * @param entity the saved entity
   * @param input the input DTO
   * @return the entity ready for publishing
   */
  protected T prePublish(T entity, I input) {
    return entity;
  }

  /**
   * Hook called after successful external system synchronization to store the external ID. Default
   * implementation is a no-op; subclasses override to persist the ID.
   *
   * @param entity the entity to update
   * @param externalId the external system ID returned by the config adapter
   */
  protected void updateExternalId(T entity, String externalId) {}
}
