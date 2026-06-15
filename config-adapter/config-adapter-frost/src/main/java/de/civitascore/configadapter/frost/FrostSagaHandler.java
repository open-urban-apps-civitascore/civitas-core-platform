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
  private static final String KEY_PROJECT_ID = "projectId";
  private static final String KEY_NAME = "name";
  private static final String KEY_DESCRIPTION = "description";
  private static final String KEY_PUBLIC = "public";

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

  /**
   * Globally-unique FROST project name: {@code "{datasetName} ({datasetId})"}. The portal allows
   * duplicate dataset display names, but FROST enforces project-name uniqueness — naming by display
   * name alone would let a second same-named dataset's 500-duplicate recovery bind to the FIRST
   * dataset's project (cross-dataset data leakage, P1). Including the globally-unique datasetId
   * makes the name unique per dataset, so the duplicate-recovery lookup only ever re-binds a
   * dataset to its OWN project (correct retry idempotency, never a foreign binding).
   */
  private static String frostProjectName(String datasetName, String datasetId) {
    return datasetName + " (" + datasetId + ")";
  }

  /** FROST entity path of a project, e.g. {@code Projects(42)}. */
  private static String projectPath(String projectId) {
    return "Projects(" + projectId + ")";
  }

  /** Public base URL of a project, reported back to the portal as the dataset payload root. */
  private String projectBaseUrl(String projectId) {
    return publicUrl + "/" + projectPath(projectId);
  }

  private SagaCommandResult handleCreateProject(SagaCommandMessage command) {
    String datasetName = requireString(command, "datasetName");
    String datasetId = requireString(command, "datasetId");
    String description = (String) command.payload().getOrDefault(KEY_DESCRIPTION, "");
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));

    String projectName = frostProjectName(datasetName, datasetId);
    Map<String, Object> body = new HashMap<>();
    body.put(KEY_NAME, projectName);
    body.put(KEY_DESCRIPTION, description);
    body.put(KEY_PUBLIC, openDataAccess);

    try (Response response =
        authStrategy
            .apply(client().target(serverUrl).path("Projects").request(MediaType.APPLICATION_JSON))
            .post(Entity.json(body))) {

      // FROST returns HTTP 500 with "Failed to store data." on UNIQUE constraint violations
      // (duplicate project name) instead of the expected HTTP 409 Conflict.
      // Fall back to a name lookup so CREATE_PROJECT is idempotent.
      if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
        String responseBody = response.readEntity(String.class);
        if (responseBody != null
            && responseBody.contains(FrostAdapter.ERROR_FAILED_TO_STORE_DATA)) {
          log.info(
              "FROST returned 500 'Failed to store data.' for CREATE_PROJECT"
                  + " — checking for existing project with name '{}', saga={}",
              Encode.forJava(projectName),
              Encode.forJava(command.sagaId()));
          return findExistingProjectByName(command, projectName);
        }
        // An HTTP 500 that is NOT the known duplicate-name case is a genuine FROST-side failure.
        // Log the full body at WARN so operators see the real cause — the exception message is
        // surfaced to the saga but may be truncated/sanitized downstream.
        log.warn(
            "FROST returned HTTP 500 for CREATE_PROJECT that is not the known 'Failed to store"
                + " data.' duplicate case — body: {}, saga={}",
            Encode.forJava(responseBody),
            Encode.forJava(command.sagaId()));
        throw new SagaApiException("CREATE_PROJECT failed: HTTP 500 — " + responseBody, 500);
      }

      checkResponse(response, "CREATE_PROJECT");

      String projectId = FrostUtils.extractIdFromLocation(response.getHeaderString("Location"));
      String baseUrl = projectBaseUrl(projectId);

      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData = Map.of(KEY_PROJECT_ID, projectId);

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
      String baseUrl = projectBaseUrl(projectId);

      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData = Map.of(KEY_PROJECT_ID, projectId);

      // Recovery from an HTTP 500 by binding to a PRE-EXISTING project matched by the unique name
      // "{datasetName} ({datasetId})". Because the name carries the globally-unique datasetId, this
      // only ever re-binds the dataset to its OWN project (a retried CREATE_PROJECT) — never a
      // foreign same-display-named one. Kept at WARN so the 500-recovery path stays
      // operator-visible.
      log.warn(
          "FROST CREATE_PROJECT recovered from a 500 by reusing the existing project with the same"
              + " unique (datasetName + datasetId) name: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleUpdateProject(SagaCommandMessage command) {
    String projectId = requireString(command, KEY_PROJECT_ID);
    String datasetName = requireString(command, "datasetName");
    String datasetId = requireString(command, "datasetId");
    String description = (String) command.payload().getOrDefault(KEY_DESCRIPTION, "");
    boolean openDataAccess =
        Boolean.TRUE.equals(command.payload().getOrDefault("openDataAccess", false));

    // Read current state before updating (needed for compensation)
    String previousName;
    String previousDescription;
    boolean previousPublic;
    try (Response getResponse =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path(projectPath(projectId))
                    .request(MediaType.APPLICATION_JSON))
            .get()) {
      checkResponse(getResponse, "GET project for UPDATE_PROJECT");
      @SuppressWarnings("unchecked")
      Map<String, Object> currentProject = getResponse.readEntity(Map.class);
      previousName = (String) currentProject.getOrDefault(KEY_NAME, "");
      previousDescription = (String) currentProject.getOrDefault(KEY_DESCRIPTION, "");
      previousPublic = Boolean.TRUE.equals(currentProject.getOrDefault(KEY_PUBLIC, false));
    }

    Map<String, Object> body =
        Map.of(
            KEY_NAME, frostProjectName(datasetName, datasetId),
            KEY_DESCRIPTION, description,
            KEY_PUBLIC, openDataAccess);

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path(projectPath(projectId))
                    .request(MediaType.APPLICATION_JSON))
            .method("PATCH", Entity.json(body))) {

      checkResponse(response, "UPDATE_PROJECT");

      String baseUrl = projectBaseUrl(projectId);
      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      Map<String, Object> compensationData =
          new HashMap<>(
              Map.of(
                  KEY_PROJECT_ID,
                  projectId,
                  "previousDescription",
                  previousDescription,
                  "previousPublic",
                  previousPublic));
      if (previousName.isBlank()) {
        // FROST returned no usable name (unexpected). Capturing "" would make a later
        // RESTORE_PROJECT blank the project name and break the unique-name duplicate-recovery
        // lookup — omit the field so compensation leaves the name as-is (MR !547 finding 6).
        log.warn(
            "UPDATE_PROJECT: FROST returned no name for project {} — compensation will keep the"
                + " then-current name instead of restoring. saga={}",
            Encode.forJava(projectId),
            Encode.forJava(command.sagaId()));
      } else {
        compensationData.put("previousName", previousName);
      }

      log.info(
          "FROST project updated: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  private SagaCommandResult handleDeleteProject(SagaCommandMessage command) {
    String projectId = requireString(command, KEY_PROJECT_ID);
    boolean compensating = "COMPENSATE_STEP".equals(command.type());

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path(projectPath(projectId))
                    .request(MediaType.APPLICATION_JSON))
            .delete()) {

      // Idempotent compensation: a 404 means the project is already gone — which is exactly the
      // goal state of a DELETE_PROJECT rollback. Treat it as success on a compensation re-run
      // (retry / partial earlier cleanup) rather than failing the saga rollback. A forward delete
      // keeps the stricter checkResponse so genuine drift stays visible.
      if (compensating && response.getStatus() == 404) {
        log.info(
            "DELETE_PROJECT compensation: project {} already absent (404) — treating as success."
                + " saga={}",
            Encode.forJava(projectId),
            Encode.forJava(command.sagaId()));
        return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
      }

      checkResponse(response, "DELETE_PROJECT");

      log.info(
          "FROST project deleted: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return compensating
          ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
          : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }
  }

  private SagaCommandResult handleRestoreProject(SagaCommandMessage command) {
    String projectId = requireString(command, KEY_PROJECT_ID);
    Object previousName = command.payload().get("previousName");
    String previousDescription = (String) command.payload().getOrDefault("previousDescription", "");

    Map<String, Object> body = new HashMap<>();
    // Only restore the name when the UPDATE saga captured a usable one. PATCHing "" would blank
    // the project identity and break the unique-name duplicate-recovery lookup; leaving the field
    // unset preserves the current value via PATCH semantics (same guard as previousPublic below).
    if (previousName instanceof String name && !name.isBlank()) {
      body.put(KEY_NAME, name);
    } else {
      log.info(
          "RESTORE_PROJECT: previousName not captured — leaving the FROST project name unchanged"
              + " for projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));
    }
    body.put(KEY_DESCRIPTION, previousDescription);
    // Only restore previousPublic when the UPDATE saga captured it (older sagas may
    // predate this field — leaving it unset preserves the current value via PATCH semantics).
    Object previousPublic = command.payload().get("previousPublic");
    if (previousPublic != null) {
      body.put(KEY_PUBLIC, Boolean.TRUE.equals(previousPublic));
    } else {
      // Privacy-relevant edge: without a captured previousPublic (e.g. an in-flight saga across a
      // deploy) the FROST project's public flag is left as-is rather than reverted. Surface it so
      // an
      // operator can verify the access state after such a rollback.
      log.info(
          "RESTORE_PROJECT: previousPublic not captured — leaving the FROST public flag unchanged"
              + " for projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));
    }

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path(projectPath(projectId))
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
