/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * APISIX adapter that processes configuration messages and manages APISIX upstream resources.
 * Supports CREATE, UPDATE, and DELETE operations for APISIX upstreams.
 */
public class ApisixAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(ApisixAdapter.class);

  public static final String ADAPTER_NAME = "apisix";

  private Client client;
  private String adminApiUrl;
  private String adminApiKey;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    this.adminApiUrl = getAdapterProperty("admin.url", "http://localhost:9180");
    this.adminApiKey = getAdapterProperty("admin.key");
    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }

    if (this.client == null) {
      this.client = createClient();
    }

    logger.info("APISIX adapter '{}' initialized for: {}", getName(), adminApiUrl);
    logger.info(
        "Subscribed to {} Kafka topics: {}", getSubscribedTopics().size(), getSubscribedTopics());
  }

  /**
   * Creates the default JAX-RS Client. Can be overridden for testing.
   *
   * @return configured Client instance
   */
  protected Client createClient() {
    return ClientBuilder.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  /**
   * Sets the Client for testing purposes.
   *
   * @param client the Client to use
   */
  void setClient(Client client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void processConfigEvent(String topic, ConfigEvent event) {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        topic,
        operation,
        targetComponent,
        targetResource);

    try {
      ResourceInfo resourceInfo = parseTargetResource(targetResource);

      switch (operation) {
        case CREATE -> handleCreate(resourceInfo, event);
        case UPDATE -> handleUpdate(resourceInfo, event);
        case DELETE -> handleDelete(resourceInfo, event);
        default -> {
          logger.warn("Unknown operation: {}", operation);
          publishErrorResult(event, "UNSUPPORTED_OPERATION", "Unknown operation: " + operation);
        }
      }

    } catch (Exception e) {
      logger.error("Failed to process config event", e);
      publishErrorResult(event, "PROCESSING_ERROR", e.getMessage());
    }
  }

  /**
   * Parses the targetResource string to extract resource type and resource ID. Expected format:
   * "upstreams/{upstreamId}" or "upstreams"
   */
  private ResourceInfo parseTargetResource(String targetResource) {
    String[] parts = targetResource.split("/");

    String resourceType = null;
    String resourceId = null;

    for (int i = 0; i < parts.length; i++) {
      if ("upstreams".equals(parts[i])) {
        resourceType = "upstream";
        if (i + 1 < parts.length) {
          resourceId = parts[i + 1];
        }
      }
    }

    logger.debug("Parsed resource - Type: {}, ID: {}", resourceType, resourceId);
    return new ResourceInfo(resourceType, resourceId);
  }

  private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event) {
    switch (resourceInfo.type) {
      case "upstream" -> createUpstream(event);
      default -> {
        logger.warn("Unknown resource type for create: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event) {
    switch (resourceInfo.type) {
      case "upstream" -> updateUpstream(resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for update: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event) {
    switch (resourceInfo.type) {
      case "upstream" -> deleteUpstream(resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for delete: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  // ============== UPSTREAM OPERATIONS ==============

  private void createUpstream(ConfigEvent event) {
    try {
      ConfigValue configValue = event.payload().config().value();
      Object upstreamConfig;
      if (configValue instanceof ApisixConfigValue apisixValue) {
        upstreamConfig = apisixValue.data();
      } else {
        upstreamConfig = configValue;
      }

      Response response =
          client
              .target(adminApiUrl)
              .path("/apisix/admin/upstreams")
              .request(MediaType.APPLICATION_JSON)
              .header("X-API-KEY", adminApiKey)
              .post(Entity.json(upstreamConfig));

      if (response.getStatus() >= 200 && response.getStatus() < 300) {
        logger.info("Created APISIX upstream successfully");
        publishSuccessResult(event, "APISIX upstream created successfully", null);
      } else {
        String errorMsg =
            "Failed to create APISIX upstream. Status: "
                + response.getStatus()
                + ", Body: "
                + response.readEntity(String.class);
        logger.error(errorMsg);
        publishErrorResult(event, "UPSTREAM_CREATE_FAILED", errorMsg);
      }
      response.close();

    } catch (Exception e) {
      logger.error("Failed to create APISIX upstream", e);
      publishErrorResult(event, "UPSTREAM_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateUpstream(String upstreamId, ConfigEvent event) {
    try {
      ConfigValue configValue = event.payload().config().value();
      Object upstreamConfig;
      if (configValue instanceof ApisixConfigValue apisixValue) {
        upstreamConfig = apisixValue.data();
      } else {
        upstreamConfig = configValue;
      }

      Response response =
          client
              .target(adminApiUrl)
              .path("/apisix/admin/upstreams/{id}")
              .resolveTemplate("id", upstreamId)
              .request(MediaType.APPLICATION_JSON)
              .header("X-API-KEY", adminApiKey)
              .put(Entity.json(upstreamConfig));

      if (response.getStatus() >= 200 && response.getStatus() < 300) {
        logger.info("Updated APISIX upstream: {}", upstreamId);
        publishSuccessResult(event, "APISIX upstream updated successfully", upstreamId);
      } else {
        String errorMsg =
            "Failed to update APISIX upstream. Status: "
                + response.getStatus()
                + ", Body: "
                + response.readEntity(String.class);
        logger.error(errorMsg);
        publishErrorResult(event, "UPSTREAM_UPDATE_FAILED", errorMsg);
      }
      response.close();

    } catch (Exception e) {
      logger.error("Failed to update APISIX upstream: {}", upstreamId, e);
      publishErrorResult(event, "UPSTREAM_UPDATE_FAILED", e.getMessage());
    }
  }

  private void deleteUpstream(String upstreamId, ConfigEvent event) {
    try {
      Response response =
          client
              .target(adminApiUrl)
              .path("/apisix/admin/upstreams/{id}")
              .resolveTemplate("id", upstreamId)
              .request(MediaType.APPLICATION_JSON)
              .header("X-API-KEY", adminApiKey)
              .delete();

      if (response.getStatus() >= 200 && response.getStatus() < 300) {
        logger.info("Deleted APISIX upstream: {}", upstreamId);
        publishSuccessResult(event, "APISIX upstream deleted successfully", upstreamId);
      } else {
        String errorMsg =
            "Failed to delete APISIX upstream. Status: "
                + response.getStatus()
                + ", Body: "
                + response.readEntity(String.class);
        logger.error(errorMsg);
        publishErrorResult(event, "UPSTREAM_DELETE_FAILED", errorMsg);
      }
      response.close();

    } catch (Exception e) {
      logger.error("Failed to delete APISIX upstream: {}", upstreamId, e);
      publishErrorResult(event, "UPSTREAM_DELETE_FAILED", e.getMessage());
    }
  }

  // ============== RESULT PUBLISHING ==============

  private void publishSuccessResult(ConfigEvent originalEvent, String message, String resourceId) {
    if (getEventPublisher() == null || originalEvent.metadata().resultTopic() == null) {
      return;
    }

    try {
      ConfigResultEvent resultEvent =
          ConfigResultEvent.success(
              originalEvent.metadata().correlationId(),
              originalEvent.metadata().messageId(),
              message,
              resourceId,
              originalEvent.payload().operation(),
              originalEvent.payload().targetResource(),
              "civitas.config-adapter.apisix");

      getEventPublisher().publish(originalEvent.metadata().resultTopic(), resultEvent);
      logger.debug("Published SUCCESS result to topic: {}", originalEvent.metadata().resultTopic());

    } catch (Exception e) {
      logger.error("Failed to publish success result", e);
    }
  }

  private void publishErrorResult(
      ConfigEvent originalEvent, String errorCode, String errorMessage) {
    if (getEventPublisher() == null || originalEvent.metadata().resultTopic() == null) {
      return;
    }

    try {
      ConfigResultEvent resultEvent =
          ConfigResultEvent.failure(
              originalEvent.metadata().correlationId(),
              originalEvent.metadata().messageId(),
              errorCode,
              errorMessage,
              originalEvent.payload().operation(),
              originalEvent.payload().targetResource(),
              "civitas.config-adapter.apisix");

      getEventPublisher().publish(originalEvent.metadata().resultTopic(), resultEvent);
      logger.debug("Published FAILURE result to topic: {}", originalEvent.metadata().resultTopic());

    } catch (Exception e) {
      logger.error("Failed to publish error result", e);
    }
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
    logger.info("APISIX adapter closed");
  }

  /** Helper record to hold parsed resource information */
  private record ResourceInfo(String type, String id) {}
}
