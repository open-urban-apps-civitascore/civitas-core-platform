package de.civitascore.portal.service;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Operation;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

public abstract class EventPublishingService<T, I extends BaseInputDTO> extends BaseService<T, I> {

  protected final ConfigEventPublisherService configEventPublisher;

  @Value("${event.config-adapter-timeout-seconds:10}")
  private int configAdapterTimeoutSeconds;

  protected EventPublishingService(ConfigEventPublisherService configEventPublisher) {
    this.configEventPublisher = configEventPublisher;
  }

  @Override
  @Transactional
  public T create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    T entity = getMapper().toEntity(preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);

    entity = getRepository().saveAndFlush(entity);

    ConfigResultEvent result = preValidateWithExternalSystem(entity, "create");

    if (result != null && result.resourceId() != null && !result.resourceId().isBlank()) {
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

    ConfigResultEvent result = preValidateWithExternalSystem(entity, "update");

    if (result != null && result.resourceId() != null && !result.resourceId().isBlank()) {
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

  protected ConfigResultEvent preValidateWithExternalSystem(T entity, String operation) {
    try {
      Topics topic = resolveTopic(operation);
      String targetComponent = getTargetComponent();
      String targetResource = getRealm(entity);
      Operation configOperation = resolveOperation(operation);
      String configPath = getConfigPath();
      ConfigValue configValue = toConfigValue(entity);

      CompletableFuture<ConfigResultEvent> futureResult =
          configEventPublisher.publishConfigEvent(
              topic, targetComponent, targetResource, configOperation, configPath, configValue);

      ConfigResultEvent result = futureResult.get(configAdapterTimeoutSeconds, TimeUnit.SECONDS);

      if (result != null && result.status() == ConfigResultEvent.Status.FAILURE) {
        throw new ExternalSystemRejectionException(
            String.format(
                "Config Adapter rejected %s: %s - %s",
                operation, result.errorCode(), result.message()));
      }

      return result;

    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "Config Adapter unavailable - cannot complete operation (timeout)", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException("Config Adapter request was interrupted", e);
    } catch (Exception e) {
      if (e instanceof ExternalSystemRejectionException
          || e instanceof ExternalSystemTimeoutException) {
        throw (RuntimeException) e;
      }
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

  protected abstract Topics resolveTopic(String operation);

  protected abstract String getTargetComponent();

  protected abstract String getRealm(T entity);

  protected abstract String getConfigPath();

  protected abstract ConfigValue toConfigValue(T entity);

  protected abstract UUID getEntityId(T entity);

  protected void updateExternalId(T entity, String externalId) {}
}
