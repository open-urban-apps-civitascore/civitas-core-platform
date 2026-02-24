/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static de.civitascore.configadapter.frost.StaResourcePathParser.parse;

import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.frost.StaResourcePathParser.EntityType;
import de.civitascore.configadapter.frost.StaResourcePathParser.ResourceInfo;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FROST-Server adapter that processes configuration messages and manages SensorThings API entities.
 * Supports CREATE, UPDATE, and DELETE operations for Things, Locations, Sensors,
 * ObservedProperties, Datastreams, Projects. Supports project-scoped creation paths (e.g. {@code
 * Projects/{id}/Things}).
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Network errors (ProcessingException) → RetryableAdapterException (NETWORK_ERROR)
 *   <li>HTTP 5xx errors → RetryableAdapterException (SERVICE_UNAVAILABLE)
 *   <li>HTTP 4xx errors → FatalAdapterException (FROST_ENTITY_ERROR)
 *   <li>Unknown exceptions → FatalAdapterException (FROST_ENTITY_ERROR)
 * </ul>
 */
public class FrostAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(FrostAdapter.class);

  public static final String ADAPTER_NAME = "frost";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/v1.1";
  private static final String SERVER_URL_PROPERTY_KEY = "url";
  private static final String API_KEY_PROPERTY_KEY = "api.key";
  private static final String API_KEY_HEADER_PROPERTY_KEY = "api.key.header";
  private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";

  private static final String HTTP_STATUS_PREFIX = "HTTP ";

  private Client client;
  private String serverUrl;
  private String apiKey;
  private String apiKeyHeader;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    this.serverUrl =
        getAdapterProperty(SERVER_URL_PROPERTY_KEY, DEFAULT_SERVER_URL).replaceAll("/$", "");
    this.apiKey = getAdapterProperty(API_KEY_PROPERTY_KEY);
    this.apiKeyHeader = getAdapterProperty(API_KEY_HEADER_PROPERTY_KEY, DEFAULT_API_KEY_HEADER);

    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("The FROST API key cannot be null or blank.");
    }

    if (this.client == null) {
      this.client = createClient();
    }

    logger.info(
        "FROST adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(serverUrl));
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(getSubscribedTopics().toString()));
  }

  protected Client createClient() {
    return ClientBuilder.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  void setClient(Client client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(targetComponent),
        Encode.forJava(targetResource));

    ResourceInfo resourceInfo = parse(targetResource);

    switch (operation) {
      case CREATE -> handleCreate(resourceInfo, event);
      case UPDATE -> handleUpdate(resourceInfo, event);
      case DELETE -> handleDelete(resourceInfo, event);
      default -> {
        logger.warn("Unknown operation: {}", Encode.forJava(String.valueOf(operation)));
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return FrostConfigValue.FROST_RESULT_TYPE;
  }

  private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (resourceInfo.entityType() == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_RESOURCE_TYPE, event.payload().targetResource());
    }

    EntityType entityType = resourceInfo.entityType();
    Object entityConfig = extractEntityConfig(event);
    String path = resourceInfo.collectionPath();

    executeFrostOperation(
        AdapterOperation.FROST_ENTITY_CREATE,
        event,
        "FROST " + entityType.name() + " created successfully",
        null,
        () ->
            client
                .target(serverUrl)
                .path(path)
                .request(MediaType.APPLICATION_JSON)
                .header(apiKeyHeader, apiKey)
                .post(Entity.json(entityConfig)));
  }

  private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    if (resourceInfo.entityType() == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_RESOURCE_TYPE, event.payload().targetResource());
    }

    if (!resourceInfo.hasId()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Resource ID is required for update operations");
    }

    EntityType entityType = resourceInfo.entityType();
    String entityId = resourceInfo.id();
    Object entityConfig = extractEntityConfig(event);
    String basePath = resourceInfo.collectionPath();

    executeFrostOperation(
        AdapterOperation.FROST_ENTITY_UPDATE,
        event,
        "FROST " + entityType.name() + " updated successfully",
        entityId,
        () ->
            client
                .target(serverUrl)
                .path(basePath + "(" + entityId + ")")
                .request(MediaType.APPLICATION_JSON)
                .header(apiKeyHeader, apiKey)
                .method("PATCH", Entity.json(entityConfig)));
  }

  private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    if (resourceInfo.entityType() == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_RESOURCE_TYPE, event.payload().targetResource());
    }

    if (!resourceInfo.hasId()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Resource ID is required for delete operations");
    }

    EntityType entityType = resourceInfo.entityType();
    String entityId = resourceInfo.id();
    String basePath = resourceInfo.collectionPath();

    executeFrostOperation(
        AdapterOperation.FROST_ENTITY_DELETE,
        event,
        "FROST " + entityType.name() + " deleted successfully",
        entityId,
        () ->
            client
                .target(serverUrl)
                .path(basePath + "(" + entityId + ")")
                .request(MediaType.APPLICATION_JSON)
                .header(apiKeyHeader, apiKey)
                .delete());
  }

  private void handleHttpResponse(Response response, AdapterOperation operation)
      throws RetryableAdapterException, FatalAdapterException {
    int status = response.getStatus();

    if (status >= 200 && status < 300) {
      return;
    }

    String body = response.readEntity(String.class);

    if (status >= 500) {
      logger.warn(
          "FROST server error during {}: {} {}",
          Encode.forJava(operation.getDescription()),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, null, ADAPTER_NAME, status);
    }

    logger.error(
        "FROST client error during {}: {} {}",
        Encode.forJava(operation.getDescription()),
        status,
        Encode.forJava(body));
    throw new FatalAdapterException(
        AdapterErrorCode.FROST_ENTITY_ERROR, null, HTTP_STATUS_PREFIX + status + ": " + body);
  }

  // ============== OPERATION TEMPLATE ==============

  private void executeFrostOperation(
      AdapterOperation operation,
      ConfigEvent event,
      String successMessage,
      String resourceId,
      HttpRequestOperation requestOperation)
      throws RetryableAdapterException, FatalAdapterException {
    try (Response response = requestOperation.execute()) {
      handleHttpResponse(response, operation);

      if (operation == AdapterOperation.FROST_ENTITY_CREATE) {
        String locationHeader = response.getHeaderString("Location");
        resourceId = FrostUtils.extractIdFromLocation(locationHeader);
      }

      logger.info(Encode.forJava(successMessage));
      publishSuccessResult(event, successMessage, resourceId);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (ProcessingException e) {
      logger.warn(
          "Network error during {}: {}",
          Encode.forJava(operation.getDescription()),
          Encode.forJava(e.getMessage()));
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, ADAPTER_NAME, e.getMessage());
    } catch (Exception e) {
      logger.error("Failed to execute {}", Encode.forJava(operation.getDescription()), e);
      throw new FatalAdapterException(
          AdapterErrorCode.FROST_ENTITY_ERROR,
          e,
          operation.getDescription() + " failed: " + e.getMessage());
    }
  }

  @FunctionalInterface
  private interface HttpRequestOperation {
    Response execute();
  }

  // ============== HELPERS ==============

  private Object extractEntityConfig(ConfigEvent event) {
    ConfigValue configValue = event.payload().config().value();
    if (configValue instanceof FrostConfigValue frostValue) {
      return frostValue.toApiMap();
    }
    return configValue;
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
    logger.info("FROST adapter closed");
  }
}
