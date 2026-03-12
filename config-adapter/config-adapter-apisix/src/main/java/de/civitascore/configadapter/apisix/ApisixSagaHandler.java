/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for the APISIX API Gateway. Handles route and upstream management for
 * dataset provisioning:
 *
 * <ul>
 *   <li>{@code CREATE_ROUTE} — creates upstream + route (uses datasetId as deterministic ID)
 *   <li>{@code UPDATE_ROUTE} — updates route configuration (plugin_config_id for auth)
 *   <li>{@code DELETE_ROUTE} — deletes route + upstream
 *   <li>{@code RESTORE_ROUTE} — restores route to previous auth configuration (update compensation)
 * </ul>
 *
 * <p>Uses PUT with deterministic IDs (derived from datasetId) to ensure idempotent operations.
 * Compensation: {@code DELETE_ROUTE} for create rollback, {@code RESTORE_ROUTE} for update
 * rollback.
 */
public class ApisixSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "apisix";
  private static final String ADMIN_URL_DEFAULT = "http://localhost:9180";
  private static final String GATEWAY_URL_DEFAULT = "http://localhost:9080";
  private static final String ROUTES_PATH = "/apisix/admin/routes/";
  private static final String UPSTREAMS_PATH = "/apisix/admin/upstreams/";
  private static final String X_API_KEY = "X-API-KEY";

  private String adminApiUrl;
  private String adminApiKey;
  private String gatewayUrl;
  private String pluginConfigId;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public ApisixSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.adminApiUrl = getProperty("admin.url", ADMIN_URL_DEFAULT);
    this.adminApiKey = getProperty("admin.key");
    this.gatewayUrl = getProperty("gateway.url", GATEWAY_URL_DEFAULT);
    this.pluginConfigId = getProperty("plugin.config.id");

    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }

    log.info("ApisixSagaHandler initialized for: {}", Encode.forJava(adminApiUrl));
  }

  void setTestClient(Client client) {
    super.setClient(client);
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case "CREATE_ROUTE" -> handleCreateRoute(command);
      case "UPDATE_ROUTE" -> handleUpdateRoute(command);
      case "DELETE_ROUTE" -> handleDeleteRoute(command);
      case "RESTORE_ROUTE" -> handleRestoreRoute(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleCreateRoute(SagaCommandMessage command) {
    String datasetId = requireString(command, "datasetId");
    String upstreamUrl = requireString(command, "upstreamUrl");
    Object openDataAccess = command.payload().getOrDefault("openDataAccess", false);

    // Parse upstream URL into host:port and path components
    // e.g. "http://civitas-frost:8080/FROST-Server/v1.1/Projects(1)"
    //   → node = "civitas-frost:8080", path = "/FROST-Server/v1.1/Projects(1)"
    URI upstream;
    try {
      upstream = URI.create(upstreamUrl);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "CREATE_ROUTE: invalid upstreamUrl: " + Encode.forJava(upstreamUrl), e);
    }
    if (upstream.getHost() == null) {
      throw new IllegalArgumentException(
          "CREATE_ROUTE: upstreamUrl has no host: " + Encode.forJava(upstreamUrl));
    }
    String upstreamNode =
        upstream.getPort() > 0 ? upstream.getHost() + ":" + upstream.getPort() : upstream.getHost();
    String upstreamPath = upstream.getPath() != null ? upstream.getPath() : "/";
    String scheme = upstream.getScheme() != null ? upstream.getScheme() : "http";

    // 1. Create upstream (PUT with deterministic ID)
    Map<String, Object> upstreamBody = buildUpstreamBody(upstreamNode, scheme);
    putResource(UPSTREAMS_PATH + datasetId, upstreamBody, "CREATE upstream");

    // 2. Create route (PUT with deterministic ID)
    Map<String, Object> routeBody = buildCreateRouteBody(datasetId, openDataAccess, upstreamPath);
    putResource(ROUTES_PATH + datasetId, routeBody, "CREATE route");

    String publicUrl = gatewayUrl + "/datasets/" + datasetId;
    Map<String, Object> resultData =
        Map.of("routeId", datasetId, "serviceId", datasetId, "publicUrl", publicUrl);
    Map<String, Object> compensationData = Map.of("routeId", datasetId, "serviceId", datasetId);

    log.info(
        "APISIX route created: datasetId={}, saga={}",
        Encode.forJava(datasetId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleUpdateRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    String serviceId = requireString(command, "serviceId");
    Object openDataAccess = command.payload().getOrDefault("openDataAccess", false);

    // Read current route state before updating (needed for compensation)
    boolean previousOpenDataAccess = readCurrentOpenDataAccess(routeId);

    Map<String, Object> routeBody = buildAuthPatchBody(openDataAccess);
    patchResource(ROUTES_PATH + routeId, routeBody, "UPDATE route");

    Map<String, Object> resultData = Map.of("routeId", routeId, "serviceId", serviceId);
    Map<String, Object> compensationData =
        Map.of(
            "routeId", routeId,
            "serviceId", serviceId,
            "previousOpenDataAccess", previousOpenDataAccess);

    log.info(
        "APISIX route updated: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleDeleteRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    String serviceId = requireString(command, "serviceId");

    // 1. Delete route first (route depends on upstream)
    deleteResource(ROUTES_PATH + routeId, "DELETE route");

    // 2. Delete upstream
    deleteResource(UPSTREAMS_PATH + serviceId, "DELETE upstream");

    log.info(
        "APISIX route deleted: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return "COMPENSATE_STEP".equals(command.type())
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult handleRestoreRoute(SagaCommandMessage command) {
    String routeId = requireString(command, "routeId");
    String serviceId = requireString(command, "serviceId");
    Object previousOpenDataAccess = command.payload().getOrDefault("previousOpenDataAccess", false);

    Map<String, Object> routeBody = buildAuthPatchBody(previousOpenDataAccess);
    patchResource(ROUTES_PATH + routeId, routeBody, "RESTORE route");

    log.info(
        "APISIX route restored: routeId={}, saga={}",
        Encode.forJava(routeId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
  }

  /**
   * Reads the current route from APISIX and determines if it has open data access (no
   * plugin_config_id means open data).
   */
  private boolean readCurrentOpenDataAccess(String routeId) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(ROUTES_PATH + routeId)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .get()) {
      checkResponse(response, "GET route for UPDATE_ROUTE");
      @SuppressWarnings("unchecked")
      Map<String, Object> responseBody = response.readEntity(Map.class);
      // APISIX wraps the route in a "value" field
      @SuppressWarnings("unchecked")
      Map<String, Object> routeValue =
          responseBody.containsKey("value")
              ? (Map<String, Object>) responseBody.get("value")
              : responseBody;
      // No plugin_config_id means open data access
      return !routeValue.containsKey("plugin_config_id");
    }
  }

  // ─── HTTP helpers ────────────────────────────────────────────────────────────

  private void putResource(String path, Map<String, Object> body, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .put(Entity.json(body))) {
      checkResponse(response, operationDesc);
    }
  }

  private void deleteResource(String path, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .delete()) {
      checkResponse(response, operationDesc);
    }
  }

  private void patchResource(String path, Map<String, Object> body, String operationDesc) {
    try (Response response =
        client()
            .target(adminApiUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .header(X_API_KEY, adminApiKey)
            .method("PATCH", Entity.json(body))) {
      checkResponse(response, operationDesc);
    }
  }

  // ─── Body builders ───────────────────────────────────────────────────────────

  private Map<String, Object> buildUpstreamBody(String node, String scheme) {
    Map<String, Object> body = new HashMap<>();
    body.put("type", "roundrobin");
    body.put("scheme", scheme);
    body.put("nodes", Map.of(node, 1));
    return body;
  }

  /** Builds a full route body for CREATE — includes URIs, upstream, and proxy-rewrite plugin. */
  private Map<String, Object> buildCreateRouteBody(
      String datasetId, Object openDataAccess, String upstreamPath) {
    Map<String, Object> body = new HashMap<>();
    body.put("uris", new String[] {"/datasets/" + datasetId, "/datasets/" + datasetId + "/*"});
    body.put("upstream_id", datasetId);
    body.put("status", 1);

    // Rewrite gateway path to upstream FROST path
    // e.g. /datasets/{id}/Things → /FROST-Server/v1.1/Projects(1)/Things
    Map<String, Object> plugins = new HashMap<>();
    plugins.put(
        "proxy-rewrite",
        Map.of(
            "regex_uri",
            new String[] {"^/datasets/" + datasetId + "(/.*)?$", upstreamPath + "$1"}));
    body.put("plugins", plugins);

    applyAuthConfig(body, openDataAccess);
    return body;
  }

  /** Builds an auth-only route body for UPDATE/RESTORE — used with PATCH to preserve routing. */
  private Map<String, Object> buildAuthPatchBody(Object openDataAccess) {
    Map<String, Object> body = new HashMap<>();
    applyAuthConfig(body, openDataAccess);
    return body;
  }

  private void applyAuthConfig(Map<String, Object> body, Object openDataAccess) {
    boolean isOpenData = Boolean.TRUE.equals(openDataAccess);
    if (!isOpenData && pluginConfigId != null) {
      body.put("plugin_config_id", pluginConfigId);
    } else if (isOpenData) {
      body.put("plugin_config_id", null);
    }
  }
}
