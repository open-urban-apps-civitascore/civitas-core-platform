/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.frost;

import com.civitas.configadapter.adapter.SagaCommandHandler;
import com.civitas.configadapter.adapter.SagaCommandMessage;
import com.civitas.configadapter.adapter.SagaCommandResult;
import com.civitas.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saga command handler for the FROST SensorThings API. Handles project-level operations dispatched
 * by the saga orchestrator:
 *
 * <ul>
 *   <li>{@code CREATE_PROJECT} — POST /Projects
 *   <li>{@code UPDATE_PROJECT} — PATCH /Projects({projectId})
 *   <li>{@code DELETE_PROJECT} — DELETE /Projects({projectId})
 *   <li>{@code RESTORE_PROJECT} — PATCH /Projects({projectId}) with previous state (update
 *       compensation)
 * </ul>
 *
 * <p>Compensation operations: {@code DELETE_PROJECT} to compensate a {@code CREATE_PROJECT}, {@code
 * RESTORE_PROJECT} to compensate an {@code UPDATE_PROJECT}.
 */
public class FrostSagaHandler implements SagaCommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(FrostSagaHandler.class);

  private static final String ADAPTER_NAME = "frost";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/v1.1";

  private Client client;
  private String serverUrl;
  private String apiKey;
  private String apiKeyHeader;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public FrostSagaHandler() {}

  @Override
  public String adapter() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    this.serverUrl =
        config.getProperty(ADAPTER_NAME + ".url", DEFAULT_SERVER_URL).replaceAll("/$", "");
    this.apiKey = config.getProperty(ADAPTER_NAME + ".api.key");
    this.apiKeyHeader = config.getProperty(ADAPTER_NAME + ".api.key.header", "X-API-Key");

    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("The FROST API key cannot be null or blank.");
    }

    if (this.client == null) {
      this.client = createClient();
    }

    LOG.info("FrostSagaHandler initialized for: {}", Encode.forJava(serverUrl));
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
  public SagaCommandResult handle(SagaCommandMessage command) {
    boolean isCompensation = "COMPENSATE_STEP".equals(command.type());

    try {
      return switch (command.operation()) {
        case "CREATE_PROJECT" -> handleCreateProject(command);
        case "UPDATE_PROJECT" -> handleUpdateProject(command);
        case "DELETE_PROJECT" -> handleDeleteProject(command);
        case "RESTORE_PROJECT" -> handleRestoreProject(command);
        default -> {
          String error = "Unknown FROST operation: " + command.operation();
          LOG.warn(error);
          yield isCompensation
              ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
              : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
        }
      };
    } catch (ProcessingException e) {
      String error = "Network error: " + e.getMessage();
      LOG.warn(
          "FROST {} failed for saga {}: {}",
          Encode.forJava(command.operation()),
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return isCompensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    } catch (Exception e) {
      String error = command.operation() + " failed: " + e.getMessage();
      LOG.error(
          "FROST {} failed for saga {}",
          Encode.forJava(command.operation()),
          Encode.forJava(command.sagaId()),
          e);
      return isCompensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    }
  }

  private SagaCommandResult handleCreateProject(SagaCommandMessage command) {
    String datasetName = (String) command.payload().get("datasetName");
    String description = (String) command.payload().getOrDefault("description", "");

    Map<String, Object> body = new HashMap<>();
    body.put("name", datasetName);
    body.put("description", description);

    try (Response response =
        client
            .target(serverUrl)
            .path("Projects")
            .request(MediaType.APPLICATION_JSON)
            .header(apiKeyHeader, apiKey)
            .post(Entity.json(body))) {

      checkResponse(response, "CREATE_PROJECT");

      String projectId = extractIdFromLocation(response.getHeaderString("Location"));
      String baseUrl = serverUrl + "/Projects(" + projectId + ")";

      Map<String, Object> resultData = Map.of("projectId", projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData = Map.of("projectId", projectId);

      LOG.info(
          "FROST project created: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleUpdateProject(SagaCommandMessage command) {
    String projectId = (String) command.payload().get("projectId");
    String datasetName = (String) command.payload().get("datasetName");
    String description = (String) command.payload().getOrDefault("description", "");

    // Read current state before updating (needed for compensation)
    String previousName;
    String previousDescription;
    try (Response getResponse =
        client
            .target(serverUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .header(apiKeyHeader, apiKey)
            .get()) {
      checkResponse(getResponse, "GET project for UPDATE_PROJECT");
      @SuppressWarnings("unchecked")
      Map<String, Object> currentProject = getResponse.readEntity(Map.class);
      previousName = (String) currentProject.getOrDefault("name", "");
      previousDescription = (String) currentProject.getOrDefault("description", "");
    }

    Map<String, Object> body = new HashMap<>();
    body.put("name", datasetName);
    body.put("description", description);

    try (Response response =
        client
            .target(serverUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .header(apiKeyHeader, apiKey)
            .method("PATCH", Entity.json(body))) {

      checkResponse(response, "UPDATE_PROJECT");

      String baseUrl = serverUrl + "/Projects(" + projectId + ")";
      Map<String, Object> resultData = Map.of("projectId", projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData =
          Map.of(
              "projectId", projectId,
              "previousName", previousName,
              "previousDescription", previousDescription);

      LOG.info(
          "FROST project updated: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleDeleteProject(SagaCommandMessage command) {
    String projectId = (String) command.payload().get("projectId");

    try (Response response =
        client
            .target(serverUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .header(apiKeyHeader, apiKey)
            .delete()) {

      checkResponse(response, "DELETE_PROJECT");

      LOG.info(
          "FROST project deleted: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return "COMPENSATE_STEP".equals(command.type())
          ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
          : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }
  }

  private SagaCommandResult handleRestoreProject(SagaCommandMessage command) {
    String projectId = (String) command.payload().get("projectId");
    String previousName = (String) command.payload().get("previousName");
    String previousDescription = (String) command.payload().getOrDefault("previousDescription", "");

    Map<String, Object> body = new HashMap<>();
    body.put("name", previousName);
    body.put("description", previousDescription);

    try (Response response =
        client
            .target(serverUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .header(apiKeyHeader, apiKey)
            .method("PATCH", Entity.json(body))) {

      checkResponse(response, "RESTORE_PROJECT");

      LOG.info(
          "FROST project restored: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }
  }

  private void checkResponse(Response response, String operation) {
    int status = response.getStatus();
    if (status >= 200 && status < 300) {
      return;
    }

    String body = response.readEntity(String.class);
    throw new FrostApiException(
        "FROST " + operation + " failed: HTTP " + status + " — " + body, status);
  }

  private String extractIdFromLocation(String locationHeader) {
    if (locationHeader == null || locationHeader.isBlank()) {
      return null;
    }
    int start = locationHeader.lastIndexOf('(');
    int end = locationHeader.lastIndexOf(')');
    if (start >= 0 && end > start) {
      return locationHeader.substring(start + 1, end);
    }
    return null;
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
      LOG.info("FrostSagaHandler closed");
    }
  }

  /**
   * Internal exception for FROST API errors. Carries the HTTP status code to distinguish retryable
   * (5xx) from fatal (4xx) errors.
   */
  static class FrostApiException extends RuntimeException {
    /** serialVersionUID */
    private static final long serialVersionUID = -6459111249541958343L;

    private final int statusCode;

    FrostApiException(String message, int statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    int statusCode() {
      return statusCode;
    }
  }
}
