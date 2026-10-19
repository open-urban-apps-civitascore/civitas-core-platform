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
import de.civitascore.configadapter.util.OkHttpJson;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FROST-Server adapter that processes configuration messages and manages SensorThings API entities.
 * Supports CREATE, UPDATE, and DELETE operations for Things, Locations, Sensors,
 * ObservedProperties, Datastreams, Projects. Supports project-scoped creation paths (e.g. {@code
 * Projects/{id}/Things}).
 *
 * <p>Error handling (network/5xx retryable, idempotent 409/404/duplicate-500, 4xx fatal) is
 * classified by {@link FrostHttpExecutor}.
 */
public class FrostAdapter extends AbstractConfigAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(FrostAdapter.class);
  static final String ERROR_FAILED_TO_STORE_DATA = "Failed to store data.";

  public static final String ADAPTER_NAME = "frost";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/v1.1";
  private static final String SERVER_URL_PROPERTY_KEY = "url";

  private OkHttpClient client;
  private String serverUrl;
  private FrostAuthStrategy authStrategy;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    this.serverUrl =
        getAdapterProperty(SERVER_URL_PROPERTY_KEY, DEFAULT_SERVER_URL).replaceAll("/$", "");
    this.authStrategy = FrostAuthStrategy.fromConfig(config, getName());

    if (this.client == null) {
      this.client = createClient();
    }

    LOG.info(
        "FROST adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(serverUrl));
    LOG.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(getSubscribedTopics().toString()));
  }

  protected OkHttpClient createClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  void setClient(OkHttpClient client) {
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

    LOG.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(targetComponent),
        Encode.forJava(targetResource));

    ResourceInfo resourceInfo = parse(targetResource);

    switch (operation) {
      case CREATE -> handleCreate(resourceInfo, event);
      case UPDATE, DELETE -> handleModify(operation, resourceInfo, event);
    }
  }

  @Override
  protected String getResultType() {
    return FrostConfigValue.FROST_RESULT_TYPE;
  }

  private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    EntityType entityType = requireEntityType(resourceInfo, event);
    Object entityConfig = extractEntityConfig(event);
    HttpUrl url = url(resourceInfo.collectionPath());

    String resourceId =
        FrostHttpExecutor.execute(
            AdapterOperation.FROST_ENTITY_CREATE,
            null,
            () -> {
              Request request =
                  authStrategy
                      .apply(OkHttpJson.jsonRequest(url).post(OkHttpJson.jsonBody(entityConfig)))
                      .build();
              return client.newCall(request).execute();
            });
    publishOutcome(event, "FROST " + entityType.name() + " created successfully", resourceId);
  }

  /** UPDATE and DELETE share target validation and the {@code Collection(id)} path shape. */
  private void handleModify(Operation operation, ResourceInfo resourceInfo, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    EntityType entityType = requireEntityType(resourceInfo, event);
    if (!resourceInfo.hasId()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Resource ID is required for "
              + operation.name().toLowerCase(Locale.ROOT)
              + " operations");
    }

    String entityId = resourceInfo.id();
    HttpUrl url = url(resourceInfo.collectionPath() + "(" + entityId + ")");

    if (operation == Operation.UPDATE) {
      Object entityConfig = extractEntityConfig(event);
      FrostHttpExecutor.execute(
          AdapterOperation.FROST_ENTITY_UPDATE,
          entityId,
          () -> {
            Request request =
                authStrategy
                    .apply(OkHttpJson.jsonRequest(url).patch(OkHttpJson.jsonBody(entityConfig)))
                    .build();
            return client.newCall(request).execute();
          });
      publishOutcome(event, "FROST " + entityType.name() + " updated successfully", entityId);
    } else {
      FrostHttpExecutor.execute(
          AdapterOperation.FROST_ENTITY_DELETE,
          entityId,
          () -> {
            Request request = authStrategy.apply(OkHttpJson.jsonRequest(url).delete()).build();
            return client.newCall(request).execute();
          });
      publishOutcome(event, "FROST " + entityType.name() + " deleted successfully", entityId);
    }
  }

  /** The FROST entity URL for a path relative to {@link #serverUrl}, e.g. {@code Things(123)}. */
  private HttpUrl url(String path) {
    return OkHttpJson.url(serverUrl, path);
  }

  private static EntityType requireEntityType(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException {
    if (resourceInfo.entityType() == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_RESOURCE_TYPE, event.payload().targetResource());
    }
    return resourceInfo.entityType();
  }

  private void publishOutcome(ConfigEvent event, String successMessage, String resourceId)
      throws FatalAdapterException, RetryableAdapterException {
    LOG.info(Encode.forJava(successMessage));
    publishSuccessResult(event, successMessage, resourceId);
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
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
    LOG.info("FROST adapter closed");
  }
}
