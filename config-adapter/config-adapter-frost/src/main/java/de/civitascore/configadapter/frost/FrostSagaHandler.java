/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;

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
public class FrostSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "frost";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/v1.1";

  private String serverUrl;
  private String publicUrl;
  private FrostAuthStrategy authStrategy;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public FrostSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    this.serverUrl = getProperty("url", DEFAULT_SERVER_URL).replaceAll("/$", "");
    this.publicUrl = getProperty("public.url", this.serverUrl);
    this.authStrategy = FrostAuthStrategy.fromConfig(config, ADAPTER_NAME);

    log.info("FrostSagaHandler initialized for: {}", Encode.forJava(serverUrl));
  }

  void setTestClient(Client client) {
    super.setClient(client);
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case "CREATE_PROJECT" -> handleCreateProject(command);
      case "UPDATE_PROJECT" -> handleUpdateProject(command);
      case "DELETE_PROJECT" -> handleDeleteProject(command);
      case "RESTORE_PROJECT" -> handleRestoreProject(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleCreateProject(SagaCommandMessage command) {
    String datasetName = requireString(command, "datasetName");
    String description = (String) command.payload().getOrDefault("description", "");

    Map<String, Object> body = new HashMap<>();
    body.put("name", datasetName);
    body.put("description", description);

    try (Response response =
        authStrategy
            .apply(client().target(serverUrl).path("Projects").request(MediaType.APPLICATION_JSON))
            .post(Entity.json(body))) {

      // FROST returns HTTP 500 with "Failed to store data." on UNIQUE constraint violations
      // (duplicate project name) instead of the expected HTTP 409 Conflict.
      // Fall back to a name lookup so CREATE_PROJECT is idempotent.
      if (response.getStatus() == 500) {
        String responseBody = response.readEntity(String.class);
        if (responseBody != null
            && responseBody.contains(FrostAdapter.ERROR_FAILED_TO_STORE_DATA)) {
          log.info(
              "FROST returned 500 'Failed to store data.' for CREATE_PROJECT"
                  + " — checking for existing project with name '{}', saga={}",
              Encode.forJava(datasetName),
              Encode.forJava(command.sagaId()));
          return findExistingProjectByName(command, datasetName);
        }
        throw new SagaApiException("CREATE_PROJECT failed: HTTP 500 — " + responseBody, 500);
      }

      checkResponse(response, "CREATE_PROJECT");

      String projectId = FrostUtils.extractIdFromLocation(response.getHeaderString("Location"));
      String baseUrl = publicUrl + "/Projects(" + projectId + ")";

      Map<String, Object> resultData = Map.of("projectId", projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData = Map.of("projectId", projectId);

      log.info(
          "FROST project created: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult findExistingProjectByName(SagaCommandMessage command, String name) {
    String escapedName = name.replace("'", "''");
    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path("Projects")
                    .queryParam("$filter", "name eq '" + escapedName + "'")
                    .request(MediaType.APPLICATION_JSON))
            .get()) {

      checkResponse(response, "GET_PROJECTS_BY_NAME");

      @SuppressWarnings("unchecked")
      Map<String, Object> result = response.readEntity(Map.class);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> projects = (List<Map<String, Object>>) result.get("value");

      if (projects == null || projects.isEmpty()) {
        throw new SagaApiException(
            "CREATE_PROJECT failed: HTTP 500 — Failed to store data."
                + " (no existing project found with name '"
                + name
                + "')",
            500);
      }

      String projectId = String.valueOf(projects.get(0).get("@iot.id"));
      String baseUrl = publicUrl + "/Projects(" + projectId + ")";

      Map<String, Object> resultData = Map.of("projectId", projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData = Map.of("projectId", projectId);

      log.info(
          "FROST project already exists, reusing: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleUpdateProject(SagaCommandMessage command) {
    String projectId = requireString(command, "projectId");
    String datasetName = requireString(command, "datasetName");
    String description = (String) command.payload().getOrDefault("description", "");

    // Read current state before updating (needed for compensation)
    String previousName;
    String previousDescription;
    try (Response getResponse =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path("Projects(" + projectId + ")")
                    .request(MediaType.APPLICATION_JSON))
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
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path("Projects(" + projectId + ")")
                    .request(MediaType.APPLICATION_JSON))
            .method("PATCH", Entity.json(body))) {

      checkResponse(response, "UPDATE_PROJECT");

      String baseUrl = publicUrl + "/Projects(" + projectId + ")";
      Map<String, Object> resultData = Map.of("projectId", projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData =
          Map.of(
              "projectId", projectId,
              "previousName", previousName,
              "previousDescription", previousDescription);

      log.info(
          "FROST project updated: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleDeleteProject(SagaCommandMessage command) {
    String projectId = requireString(command, "projectId");

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path("Projects(" + projectId + ")")
                    .request(MediaType.APPLICATION_JSON))
            .delete()) {

      checkResponse(response, "DELETE_PROJECT");

      log.info(
          "FROST project deleted: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return "COMPENSATE_STEP".equals(command.type())
          ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
          : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }
  }

  private SagaCommandResult handleRestoreProject(SagaCommandMessage command) {
    String projectId = requireString(command, "projectId");
    String previousName = requireString(command, "previousName");
    String previousDescription = (String) command.payload().getOrDefault("previousDescription", "");

    Map<String, Object> body = new HashMap<>();
    body.put("name", previousName);
    body.put("description", previousDescription);

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path("Projects(" + projectId + ")")
                    .request(MediaType.APPLICATION_JSON))
            .method("PATCH", Entity.json(body))) {

      checkResponse(response, "RESTORE_PROJECT");

      log.info(
          "FROST project restored: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }
  }
}
