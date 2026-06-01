/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.geoserver.GeoServerConfigValue;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GeoServer REST API adapter that processes configuration messages and manages GeoServer resources.
 * Supports CREATE, UPDATE, and DELETE operations for workspaces, datastores, feature types, styles,
 * and layers via the GeoServer REST API.
 *
 * <p>The {@code targetResource} in the config event maps directly to the REST path relative to
 * {@code /rest/}. Examples:
 *
 * <ul>
 *   <li>{@code workspaces} — create a workspace (POST /rest/workspaces)
 *   <li>{@code workspaces/myws} — update or delete a workspace
 *   <li>{@code workspaces/myws/datastores} — create a PostGIS datastore
 *   <li>{@code workspaces/myws/datastores/myds/featuretypes} — create a feature type
 *   <li>{@code styles} — create a style
 * </ul>
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Network errors (ProcessingException) → RetryableAdapterException (NETWORK_ERROR)
 *   <li>HTTP 5xx → RetryableAdapterException (SERVICE_UNAVAILABLE)
 *   <li>HTTP 409 on CREATE → success (idempotent: resource already exists)
 *   <li>HTTP 404 on DELETE → success (idempotent: resource already deleted)
 *   <li>Other HTTP 4xx → FatalAdapterException (GEOSERVER_RESOURCE_ERROR)
 * </ul>
 */
public class GeoServerAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(GeoServerAdapter.class);

  public static final String ADAPTER_NAME = "geoserver";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/geoserver";
  private static final String SERVER_URL_PROPERTY_KEY = "url";
  private static final String USERNAME_PROPERTY_KEY = "admin.user";
  private static final String PASSWORD_PROPERTY_KEY = "admin.password";
  private static final String REST_BASE = "/rest/";
  private static final String HTTP_STATUS_PREFIX = "HTTP ";

  static final Set<String> COLLECTION_KEYWORDS =
      Set.of(
          "workspaces",
          "datastores",
          "coveragestores",
          "featuretypes",
          "coverages",
          "styles",
          "layers",
          "layergroups",
          "namespaces");

  /** GeoServer resource types resolved from the {@code targetResource} path. */
  enum ResourceType {
    WORKSPACE,
    DATASTORE,
    COVERAGE_STORE,
    COVERAGE,
    FEATURE_TYPE,
    STYLE,
    LAYER,
    UNKNOWN
  }

  private Client client;
  private String serverUrl;
  private GeoServerAuth auth;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    this.serverUrl =
        getAdapterProperty(SERVER_URL_PROPERTY_KEY, DEFAULT_SERVER_URL).replaceAll("/$", "");
    String username = getAdapterProperty(USERNAME_PROPERTY_KEY);
    String password = getAdapterProperty(PASSWORD_PROPERTY_KEY);
    this.auth = GeoServerAuth.create(username, password);
    if (this.client == null) {
      this.client = createClient();
    }
    logger.info(
        "GeoServer adapter '{}' initialized for: {}",
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

    if (targetResource == null || targetResource.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "targetResource must not be blank");
    }

    ResourceType resourceType = detectResourceType(targetResource);
    if (resourceType == ResourceType.UNKNOWN) {
      throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, targetResource);
    }

    switch (operation) {
      case CREATE -> handleCreate(targetResource, resourceType, event);
      case UPDATE -> handleUpdate(targetResource, resourceType, event);
      case DELETE -> handleDelete(targetResource, resourceType, event);
      default -> {
        logger.warn("Unknown operation: {}", Encode.forJava(String.valueOf(operation)));
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return GeoServerConfigValue.GEOSERVER_RESULT_TYPE;
  }

  private void handleCreate(String targetResource, ResourceType resourceType, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Object body = extractBody(event);
    String restPath = REST_BASE + targetResource;

    executeGeoServerOperation(
        AdapterOperation.GEOSERVER_RESOURCE_CREATE,
        event,
        "GeoServer " + resourceType.name() + " created successfully",
        null,
        () ->
            auth.apply(client.target(serverUrl).path(restPath).request(MediaType.APPLICATION_JSON))
                .post(Entity.json(body)));
  }

  private void handleUpdate(String targetResource, ResourceType resourceType, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (!hasResourceName(targetResource)) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Resource name is required for update operations");
    }

    Object body = extractBody(event);
    String restPath = REST_BASE + targetResource;
    String resourceName = extractResourceName(targetResource);

    executeGeoServerOperation(
        AdapterOperation.GEOSERVER_RESOURCE_UPDATE,
        event,
        "GeoServer " + resourceType.name() + " updated successfully",
        resourceName,
        () ->
            auth.apply(client.target(serverUrl).path(restPath).request(MediaType.APPLICATION_JSON))
                .put(Entity.json(body)));
  }

  private void handleDelete(String targetResource, ResourceType resourceType, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (!hasResourceName(targetResource)) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Resource name is required for delete operations");
    }

    String resourceName = extractResourceName(targetResource);
    boolean useRecurse = shouldUseRecurse(resourceType);

    WebTarget target = client.target(serverUrl).path(REST_BASE + targetResource);
    if (useRecurse) {
      target = target.queryParam("recurse", "true");
    }
    final WebTarget deleteTarget = target;

    executeGeoServerOperation(
        AdapterOperation.GEOSERVER_RESOURCE_DELETE,
        event,
        "GeoServer " + resourceType.name() + " deleted successfully",
        resourceName,
        () -> auth.apply(deleteTarget.request(MediaType.APPLICATION_JSON)).delete());
  }

  private void handleHttpResponse(Response response, AdapterOperation operation)
      throws RetryableAdapterException, FatalAdapterException {
    int status = response.getStatus();

    if (status >= 200 && status < 300) {
      return;
    }

    if (status == 409 && operation == AdapterOperation.GEOSERVER_RESOURCE_CREATE) {
      logger.info(
          "GeoServer resource already exists (409), treating create as success (idempotent)");
      return;
    }

    if (status == 404 && operation == AdapterOperation.GEOSERVER_RESOURCE_DELETE) {
      logger.info(
          "GeoServer resource not found (404), treating delete as success"
              + " (already deleted, idempotent)");
      return;
    }

    String body = response.readEntity(String.class);

    if (status >= 500) {
      logger.warn(
          "GeoServer server error during {}: {} {}",
          Encode.forJava(operation.getDescription()),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, null, ADAPTER_NAME, status);
    }

    logger.error(
        "GeoServer client error during {}: {} {}",
        Encode.forJava(operation.getDescription()),
        status,
        Encode.forJava(body));
    throw new FatalAdapterException(
        AdapterErrorCode.GEOSERVER_RESOURCE_ERROR, null, HTTP_STATUS_PREFIX + status + ": " + body);
  }

  private void executeGeoServerOperation(
      AdapterOperation operation,
      ConfigEvent event,
      String successMessage,
      String resourceName,
      HttpRequestOperation requestOperation)
      throws RetryableAdapterException, FatalAdapterException {
    try (Response response = requestOperation.execute()) {
      handleHttpResponse(response, operation);

      String resolvedName = resourceName;
      if (operation == AdapterOperation.GEOSERVER_RESOURCE_CREATE && response.getStatus() == 201) {
        String locationHeader = response.getHeaderString("Location");
        resolvedName = extractNameFromLocation(locationHeader);
      }

      logger.info(Encode.forJava(successMessage));
      publishSuccessResult(event, successMessage, resolvedName);
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
          AdapterErrorCode.GEOSERVER_RESOURCE_ERROR,
          e,
          operation.getDescription() + " failed: " + e.getMessage());
    }
  }

  @FunctionalInterface
  private interface HttpRequestOperation {
    Response execute();
  }

  // ============== HELPERS ==============

  private Object extractBody(ConfigEvent event) {
    ConfigValue configValue = event.payload().config().value();
    if (configValue instanceof GeoServerConfigValue geoValue) {
      return geoValue.toApiMap();
    }
    return configValue;
  }

  static ResourceType detectResourceType(String targetResource) {
    String lower = targetResource.toLowerCase();
    if (lower.contains("featuretypes")) return ResourceType.FEATURE_TYPE;
    if (lower.contains("coveragestores")) return ResourceType.COVERAGE_STORE;
    if (lower.contains("coverages")) return ResourceType.COVERAGE;
    if (lower.contains("datastores")) return ResourceType.DATASTORE;
    // styles/layers can appear at root or scoped under a workspace — check before workspaces
    if (lower.startsWith("styles") || lower.contains("/styles")) return ResourceType.STYLE;
    if (lower.startsWith("layers") || lower.contains("/layers")) return ResourceType.LAYER;
    if (lower.contains("workspaces")) return ResourceType.WORKSPACE;
    return ResourceType.UNKNOWN;
  }

  static boolean hasResourceName(String targetResource) {
    String[] parts = targetResource.replaceAll("^/+|/+$", "").split("/");
    return parts.length > 0 && !COLLECTION_KEYWORDS.contains(parts[parts.length - 1].toLowerCase());
  }

  static String extractResourceName(String targetResource) {
    String[] parts = targetResource.split("/");
    return parts[parts.length - 1];
  }

  /**
   * GeoServer requires {@code recurse=true} to delete resources that own or are referenced by
   * layers. A store (workspace, datastore, coverage store) would otherwise leave orphaned layers,
   * and a feature type or coverage delete fails outright while a published layer still references
   * it — and publishing a feature type/coverage always creates such a layer. Layers and styles are
   * excluded so referenced (possibly shared) styles are never cascade-deleted.
   */
  static boolean shouldUseRecurse(ResourceType resourceType) {
    return switch (resourceType) {
      case WORKSPACE, DATASTORE, COVERAGE_STORE, FEATURE_TYPE, COVERAGE -> true;
      case STYLE, LAYER, UNKNOWN -> false;
    };
  }

  static String extractNameFromLocation(String locationHeader) {
    if (locationHeader == null || locationHeader.isBlank()) {
      return null;
    }
    String path = locationHeader.replaceAll("\\?.*$", "");
    int lastSlash = path.lastIndexOf('/');
    return (lastSlash >= 0 && lastSlash < path.length() - 1) ? path.substring(lastSlash + 1) : null;
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
    logger.info("GeoServer adapter closed");
  }
}
