/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.apisix;

import com.civitas.configadapter.adapter.AbstractSagaCommandHandler;
import com.civitas.configadapter.adapter.SagaCommandMessage;
import com.civitas.configadapter.adapter.SagaCommandResult;
import com.civitas.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
  private static final String ROUTES_PATH = "/apisix/admin/routes/";
  private static final String UPSTREAMS_PATH = "/apisix/admin/upstreams/";
  private static final String X_API_KEY = "X-API-KEY";

  private String adminApiUrl;
  private String adminApiKey;
  private String pluginConfigId;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public ApisixSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.adminApiUrl = getProperty("admin.url", ADMIN_URL_DEFAULT);
    this.adminApiKey = getProperty("admin.key");
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
    String datasetId = (String) command.payload().get("datasetId");
    String upstreamUrl = (String) command.payload().get("upstreamUrl");
    Object openDataAccess = command.payload().getOrDefault("openDataAccess", false);

    // 1. Create upstream (PUT with deterministic ID)
    Map<String, Object> upstreamBody = buildUpstreamBody(upstreamUrl);
    putResource(UPSTREAMS_PATH + datasetId, upstreamBody, "CREATE upstream");

    // 2. Create route (PUT with deterministic ID)
    Map<String, Object> routeBody = buildRouteBody(datasetId, openDataAccess);
    putResource(ROUTES_PATH + datasetId, routeBody, "CREATE route");

    String publicUrl = adminApiUrl + "/datasets/" + datasetId;
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
    String routeId = (String) command.payload().get("routeId");
    String serviceId = (String) command.payload().get("serviceId");
    Object openDataAccess = command.payload().getOrDefault("openDataAccess", false);

    // Read current route state before updating (needed for compensation)
    boolean previousOpenDataAccess = readCurrentOpenDataAccess(routeId);

    Map<String, Object> routeBody = buildRouteBody(serviceId, openDataAccess);
    putResource(ROUTES_PATH + routeId, routeBody, "UPDATE route");

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
    String routeId = (String) command.payload().get("routeId");
    String serviceId = (String) command.payload().get("serviceId");

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
    String routeId = (String) command.payload().get("routeId");
    String serviceId = (String) command.payload().get("serviceId");
    Object previousOpenDataAccess = command.payload().getOrDefault("previousOpenDataAccess", false);

    Map<String, Object> routeBody = buildRouteBody(serviceId, previousOpenDataAccess);
    putResource(ROUTES_PATH + routeId, routeBody, "RESTORE route");

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

  // ─── Body builders ───────────────────────────────────────────────────────────

  private Map<String, Object> buildUpstreamBody(String upstreamUrl) {
    var body = new HashMap<String, Object>();
    body.put("type", "roundrobin");
    body.put("nodes", Map.of(upstreamUrl, 1));
    return body;
  }

  private Map<String, Object> buildRouteBody(String upstreamId, Object openDataAccess) {
    var body = new HashMap<String, Object>();
    body.put("uri", "/datasets/" + upstreamId + "/*");
    body.put("upstream_id", upstreamId);
    body.put("status", 1);

    // If not open data, attach the shared auth plugin config
    boolean isOpenData = Boolean.TRUE.equals(openDataAccess);
    if (!isOpenData && pluginConfigId != null) {
      body.put("plugin_config_id", pluginConfigId);
    }

    return body;
  }
}
