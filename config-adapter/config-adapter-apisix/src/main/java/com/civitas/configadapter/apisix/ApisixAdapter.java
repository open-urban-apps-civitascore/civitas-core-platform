/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.apisix;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.AdapterOperation;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.RouteConfigValue;
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
 * APISIX adapter that processes configuration messages and manages APISIX resources. Supports
 * CREATE, UPDATE, and DELETE operations for APISIX upstreams and routes.
 *
 * <p>Supported route events:
 *
 * <ul>
 *   <li>de.civitascore.api.route.created - Create a new route
 *   <li>de.civitascore.api.route.updated - Update an existing route
 *   <li>de.civitascore.api.route.deleted - Delete a route
 * </ul>
 *
 * <p>Routes support the following plugins (as per CIVITAS/CORE V1):
 *
 * <ul>
 *   <li>openid-connect - OpenID Connect authentication
 *   <li>serverless-post-function - Post-request serverless function
 *   <li>serverless-pre-function - Pre-request serverless function
 *   <li>response-rewrite - Response header/body rewriting
 *   <li>proxy-rewrite - Request URI/header rewriting
 *   <li>prometheus - Prometheus metrics collection
 *   <li>loki - Loki logging integration
 * </ul>
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Network errors (ProcessingException) → RetryableAdapterException (NETWORK_ERROR)
 *   <li>HTTP 5xx errors → RetryableAdapterException (SERVICE_UNAVAILABLE)
 *   <li>HTTP 4xx errors → FatalAdapterException (APISIX_ROUTE_ERROR/APISIX_UPSTREAM_ERROR)
 *   <li>Unknown exceptions → FatalAdapterException (UNKNOWN_ERROR)
 * </ul>
 */
public class ApisixAdapter extends AbstractConfigAdapter {

  private static final String ADMIN_URL_DEFAULT = "http://localhost:9180";
  private static final String ADMIN_URL_PROPERTY_KEY = "admin.url";
  private static final String ADMIN_KEY_PROPERTY_KEY = "admin.key";

  private static final String APISIX_RESULT_TYPE = "de.civitascore.api.processing.result";

  // Constants for exception messages
  private static final String HTTP_STATUS_PREFIX = "HTTP ";

  private static final Logger logger = LoggerFactory.getLogger(ApisixAdapter.class);

  public static final String ADAPTER_NAME = "apisix";
  public static final String X_API_KEY = "X-API-KEY";
  public static final String APISIX_ADMIN_ROUTES = "/apisix/admin/routes";
  public static final String APISIX_ADMIN_ROUTES_ID = "/apisix/admin/routes/{id}";
  public static final String ID = "id";
  public static final String APISIX_ADMIN_UPSTREAMS_ID = "/apisix/admin/upstreams/{id}";
  public static final String APISIX_ADMIN_UPSTREAMS = "/apisix/admin/upstreams";
  public static final String UPSTREAM = "upstream";
  public static final String ROUTE = "route";
  public static final String UPSTREAMS = "upstreams";
  public static final String ROUTES = "routes";

  private Client client;
  private String adminApiUrl;
  private String adminApiKey;

  public ApisixAdapter() {}

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    this.adminApiUrl = getAdapterProperty(ADMIN_URL_PROPERTY_KEY, ADMIN_URL_DEFAULT);
    this.adminApiKey = getAdapterProperty(ADMIN_KEY_PROPERTY_KEY);
    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }

    if (this.client == null) {
      this.client = createClient();
    }

    logger.info(
        "APISIX adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(adminApiUrl));
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(String.valueOf(getSubscribedTopics())));
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
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(String.valueOf(targetComponent)),
        Encode.forJava(String.valueOf(targetResource)));

    ResourceInfo resourceInfo = parseTargetResource(targetResource);

    switch (operation) {
      case CREATE -> handleCreate(resourceInfo, event);
      case UPDATE -> handleUpdate(resourceInfo, event);
      case DELETE -> handleDelete(resourceInfo, event);
      default -> {
        logger.warn("Unknown operation: {}", operation);
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return APISIX_RESULT_TYPE;
  }

  /**
   * Parses the targetResource string to extract resource type and resource ID. Expected formats:
   * "upstreams/{upstreamId}" or "upstreams" "routes/{routeId}" or "routes"
   */
  private ResourceInfo parseTargetResource(String targetResource) {
    String[] parts = targetResource.split("/");

    String resourceType = null;
    String resourceId = null;

    for (int i = 0; i < parts.length; i++) {
      if (UPSTREAMS.equals(parts[i])) {
        resourceType = UPSTREAM;
        if (i + 1 < parts.length) {
          resourceId = parts[i + 1];
        }
        break;
      } else if (ROUTES.equals(parts[i])) {
        resourceType = ROUTE;
        if (i + 1 < parts.length) {
          resourceId = parts[i + 1];
        }
        break;
      }
    }

    logger.debug(
        "Parsed resource - Type: {}, ID: {}",
        Encode.forJava(String.valueOf(resourceType)),
        Encode.forJava(String.valueOf(resourceId)));
    return new ResourceInfo(resourceType, resourceId);
  }

  private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case UPSTREAM -> createUpstream(event);
      case ROUTE -> createRoute(event);
      case null, default -> {
        logger.warn(
            "Unknown resource type for create: {}",
            Encode.forJava(String.valueOf(resourceInfo.type)));
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
      }
    }
  }

  private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case UPSTREAM -> updateUpstream(resourceInfo.id, event);
      case ROUTE -> updateRoute(resourceInfo.id, event);
      default -> {
        logger.warn(
            "Unknown resource type for update: {}",
            Encode.forJava(String.valueOf(resourceInfo.type)));
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
      }
    }
  }

  private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case UPSTREAM -> deleteUpstream(resourceInfo.id, event);
      case ROUTE -> deleteRoute(resourceInfo.id, event);
      default -> {
        logger.warn(
            "Unknown resource type for delete: {}",
            Encode.forJava(String.valueOf(resourceInfo.type)));
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
      }
    }
  }

  // ============== EXCEPTION WRAPPING ==============

  /**
   * Wraps network/processing exceptions as RetryableAdapterException.
   *
   * @param e the ProcessingException (network error)
   * @param operation the operation being performed
   * @return RetryableAdapterException
   */
  private RetryableAdapterException wrapNetworkException(
      ProcessingException e, AdapterOperation operation) {
    logger.warn(
        "Network error during {}: {}",
        operation.getDescription(),
        Encode.forJava(String.valueOf(e.getMessage())));
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, e, ADAPTER_NAME, e.getMessage());
  }

  /**
   * Handles HTTP response and throws appropriate exceptions for error status codes.
   *
   * @param response the HTTP response
   * @param errorCode the error code to use for 4xx errors
   * @param operation the operation being performed
   * @throws RetryableAdapterException for HTTP 5xx errors
   * @throws FatalAdapterException for HTTP 4xx errors
   */
  private void handleHttpResponse(
      Response response, AdapterErrorCode errorCode, AdapterOperation operation)
      throws RetryableAdapterException, FatalAdapterException {
    int status = response.getStatus();

    // Success - nothing to throw
    if (status >= 200 && status < 300) {
      return;
    }

    String body = response.readEntity(String.class);

    // HTTP 5xx - Server errors are retryable
    if (status >= 500) {
      logger.warn(
          "APISIX server error during {}: {} {}",
          operation.getDescription(),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, ADAPTER_NAME, status);
    }

    // HTTP 4xx - Client errors are fatal
    logger.error(
        "APISIX client error during {}: {} {}",
        operation.getDescription(),
        status,
        Encode.forJava(body));
    throw new FatalAdapterException(errorCode, HTTP_STATUS_PREFIX + status + ": " + body);
  }

  // ============== UPSTREAM OPERATIONS ==============

  private void createUpstream(ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Object upstreamConfig = extractUpstreamConfig(event);
    executeApisixOperation(
        AdapterOperation.UPSTREAM_CREATE,
        AdapterErrorCode.APISIX_UPSTREAM_ERROR,
        event,
        "APISIX upstream created successfully",
        null,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_UPSTREAMS)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .post(Entity.json(upstreamConfig)));
  }

  private void updateUpstream(String upstreamId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Object upstreamConfig = extractUpstreamConfig(event);
    executeApisixOperation(
        AdapterOperation.UPSTREAM_UPDATE,
        AdapterErrorCode.APISIX_UPSTREAM_ERROR,
        event,
        "APISIX upstream updated successfully",
        upstreamId,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_UPSTREAMS_ID)
                .resolveTemplate(ID, upstreamId)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .put(Entity.json(upstreamConfig)));
  }

  private void deleteUpstream(String upstreamId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    executeApisixOperation(
        AdapterOperation.UPSTREAM_DELETE,
        AdapterErrorCode.APISIX_UPSTREAM_ERROR,
        event,
        "APISIX upstream deleted successfully",
        upstreamId,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_UPSTREAMS_ID)
                .resolveTemplate(ID, upstreamId)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .delete());
  }

  private Object extractUpstreamConfig(ConfigEvent event) {
    ConfigValue configValue = event.payload().config().value();
    if (configValue instanceof ApisixConfigValue apisixValue) {
      return apisixValue.toApiMap();
    }
    return configValue;
  }

  // ============== ROUTE OPERATIONS ==============

  private void createRoute(ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Object routeConfig = extractRouteConfig(event.payload().config().value());
    executeApisixOperation(
        AdapterOperation.ROUTE_CREATE,
        AdapterErrorCode.APISIX_ROUTE_ERROR,
        event,
        "APISIX route created successfully",
        null,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_ROUTES)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .post(Entity.json(routeConfig)));
  }

  private void updateRoute(String routeId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Object routeConfig = extractRouteConfig(event.payload().config().value());
    executeApisixOperation(
        AdapterOperation.ROUTE_UPDATE,
        AdapterErrorCode.APISIX_ROUTE_ERROR,
        event,
        "APISIX route updated successfully",
        routeId,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_ROUTES_ID)
                .resolveTemplate(ID, routeId)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .put(Entity.json(routeConfig)));
  }

  private void deleteRoute(String routeId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    executeApisixOperation(
        AdapterOperation.ROUTE_DELETE,
        AdapterErrorCode.APISIX_ROUTE_ERROR,
        event,
        "APISIX route deleted successfully",
        routeId,
        () ->
            client
                .target(adminApiUrl)
                .path(APISIX_ADMIN_ROUTES_ID)
                .resolveTemplate(ID, routeId)
                .request(MediaType.APPLICATION_JSON)
                .header(X_API_KEY, adminApiKey)
                .delete());
  }

  /**
   * Extracts route configuration from the ConfigValue. Supports both RouteConfigValue and
   * ApisixConfigValue for flexibility.
   *
   * @param configValue the configuration value from the event
   * @return the route configuration data to send to APISIX
   */
  private Object extractRouteConfig(ConfigValue configValue) {
    if (configValue instanceof RouteConfigValue routeValue) {
      return routeValue.toApiMap();
    } else if (configValue instanceof ApisixConfigValue apisixValue) {
      return apisixValue.toApiMap();
    }
    return configValue;
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

  /** Functional interface for HTTP request operations. */
  @FunctionalInterface
  private interface HttpRequestOperation {
    Response execute() throws Exception;
  }

  /**
   * Template method for executing APISIX operations with standardized error handling.
   *
   * @param operation the adapter operation being performed
   * @param errorCode the error code for fatal errors
   * @param event the original config event
   * @param successMessage the message to log/publish on success
   * @param resourceId the resource ID (may be null for create operations)
   * @param requestOperation the HTTP request to execute
   */
  private void executeApisixOperation(
      AdapterOperation operation,
      AdapterErrorCode errorCode,
      ConfigEvent event,
      String successMessage,
      String resourceId,
      HttpRequestOperation requestOperation)
      throws FatalAdapterException, RetryableAdapterException {
    Response response = null;
    try {
      response = requestOperation.execute();
      handleHttpResponse(response, errorCode, operation);
      logger.info(successMessage);
      publishSuccessResult(event, successMessage, resourceId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, operation);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Failed to execute {}", operation.getDescription(), e);
      throw new FatalAdapterException(
          errorCode, e, operation.getDescription() + " failed: " + e.getMessage());
    } finally {
      closeResponse(response);
    }
  }

  private void closeResponse(Response response) {
    if (response != null) {
      response.close();
    }
  }
}
