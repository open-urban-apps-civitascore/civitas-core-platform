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
 *   <li>{@code DELETE_PROJECT} — DELETE all Things of the project (cascades to their Datastreams
 *       and Observations), then DELETE /Projects({projectId}). As a CREATE_PROJECT compensation it
 *       is a no-op when the create only reused a pre-existing project ({@code created=false}), so a
 *       later step's failure cannot destroy the data the unrelease flow preserves.
 *   <li>{@code RESTORE_PROJECT} — PATCH /Projects({projectId}) with previous state (update
 *       compensation)
 * </ul>
 *
 * <p>Compensation operations: {@code DELETE_PROJECT} to compensate a {@code CREATE_PROJECT}, {@code
 * RESTORE_PROJECT} to compensate an {@code UPDATE_PROJECT}.
 */
// One method per saga operation (CREATE/UPDATE/DELETE/RESTORE_PROJECT) plus focused helpers — the
// method count and coupling are inherent to a per-operation dispatch handler, not a God Class.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
public class FrostSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "frost";
  private static final String DEFAULT_SERVER_URL = "http://localhost:8080/v1.1";
  private static final String KEY_PROJECT_ID = "projectId";
  private static final String KEY_CREATED = "created";
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

    String projectName = frostProjectName(datasetName, datasetId);

    // Find-or-create: the project name carries the globally-unique datasetId, so a pre-existing
    // project can only be this dataset's own — a prior CREATE_PROJECT that already ran (re-release,
    // retried saga). Reusing it keeps the FROST data (Things → Datastreams → Observations) intact
    // instead of orphaning it behind a second project. Idempotency no longer hinges on the
    // version-specific 500 "Failed to store data." string; that path stays only as a race guard.
    SagaCommandResult existing = findExistingProjectByName(command, projectName);
    if (existing != null) {
      return existing;
    }

    return createNewProject(command, projectName, description);
  }

  /** POSTs a new private FROST project, with a 500-duplicate race guard. */
  private SagaCommandResult createNewProject(
      SagaCommandMessage command, String projectName, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put(KEY_NAME, projectName);
    body.put(KEY_DESCRIPTION, description);
    // The FROST project is ALWAYS created private. Open data access is an authorization
    // decision made by OPA at request time (ABAC on the dataset's openDataAccess flag), not by
    // FROST project visibility — anonymous open-data reads flow through the gateway → OPA, never
    // around it via a publicly readable FROST project. (Replaces the former
    // public=openDataAccess bypass.)
    body.put(KEY_PUBLIC, false);

    try (Response response =
        authStrategy
            .apply(client().target(serverUrl).path("Projects").request(MediaType.APPLICATION_JSON))
            .post(Entity.json(body))) {

      if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
        return handleCreateProjectServerError(command, projectName, response);
      }

      checkResponse(response, "CREATE_PROJECT");

      String projectId = FrostUtils.extractIdFromLocation(response.getHeaderString("Location"));
      String baseUrl = projectBaseUrl(projectId);

      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      // created=true: this execution POSTed the project, so its compensation may safely DELETE it.
      Map<String, Object> compensationData = Map.of(KEY_PROJECT_ID, projectId, KEY_CREATED, true);

      log.info(
          "FROST project created: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);
    }
  }

  /**
   * Handles an HTTP 500 from the CREATE_PROJECT POST. FROST returns 500 "Failed to store data." on
   * a UNIQUE constraint violation (duplicate project name) instead of 409 Conflict. This is a race
   * guard: the up-front find-or-create lookup already ran, but a concurrent CREATE may have
   * inserted the project in between — so re-run the name lookup and reuse the winner.
   */
  private SagaCommandResult handleCreateProjectServerError(
      SagaCommandMessage command, String projectName, Response response) {
    String responseBody = response.readEntity(String.class);
    if (responseBody != null && responseBody.contains(FrostAdapter.ERROR_FAILED_TO_STORE_DATA)) {
      log.info(
          "FROST returned 500 'Failed to store data.' for CREATE_PROJECT"
              + " — re-checking for existing project with name '{}', saga={}",
          Encode.forJava(projectName),
          Encode.forJava(command.sagaId()));
      SagaCommandResult recovered = findExistingProjectByName(command, projectName);
      if (recovered != null) {
        return recovered;
      }
      throw new SagaApiException(
          "CREATE_PROJECT failed: HTTP 500 — Failed to store data."
              + " (no existing project found with name '"
              + projectName
              + "')",
          500);
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

  /**
   * Looks up a project by its globally-unique {@code "{datasetName} ({datasetId})"} name. Returns a
   * successful {@link SagaCommandResult} bound to the existing project, or {@code null} if none
   * exists. The datasetId in the name guarantees any match is this dataset's own project, never a
   * foreign same-display-named one.
   */
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
        return null;
      }

      String projectId = String.valueOf(projects.get(0).get("@iot.id"));
      String baseUrl = projectBaseUrl(projectId);

      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      // created=false: this project pre-existed and holds data the unrelease flow intentionally
      // preserves — a CREATE_PROJECT compensation must NOT delete it, or a later step's failure
      // would destroy exactly the storage re-release is meant to reuse.
      Map<String, Object> compensationData = Map.of(KEY_PROJECT_ID, projectId, KEY_CREATED, false);

      log.info(
          "FROST CREATE_PROJECT reused the existing project matched by unique name: projectId={},"
              + " saga={}",
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

    // Read current state before updating (needed for compensation)
    String previousName;
    String previousDescription;
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
    }

    // Always force the project private (see handleCreateProject). New projects are never created
    // public, so a public project can only be legacy/pre-migration data; re-provisioning flips it
    // back to private, closing the old OPA-bypass path.
    Map<String, Object> body =
        Map.of(
            KEY_NAME,
            frostProjectName(datasetName, datasetId),
            KEY_DESCRIPTION,
            description,
            KEY_PUBLIC,
            false);

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
              Map.of(KEY_PROJECT_ID, projectId, "previousDescription", previousDescription));
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

    // created=false marks a reused, data-bearing project; deleting it as a CREATE_PROJECT
    // compensation would destroy the storage re-release reuses. Forward deletes omit the flag.
    if (compensating && Boolean.FALSE.equals(command.payload().get(KEY_CREATED))) {
      log.info(
          "DELETE_PROJECT compensation skipped: project {} was reused, not created by this saga —"
              + " preserving its data. saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));
      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }

    FrostProjectCleanup cleanup =
        new FrostProjectCleanup(client(), authStrategy, serverUrl, command.sagaId());
    // Parse (and thereby validate) the provisioned ids before anything is deleted — a malformed
    // payload must fail the step up front, not after the Things are already gone.
    Map<String, List<String>> provisionedEntities = provisionedEntities(command);
    cleanup.deleteProjectThings(projectId);
    cleanup.deleteProvisionedEntities(provisionedEntities);

    try (Response response =
        authStrategy
            .apply(
                client()
                    .target(serverUrl)
                    .path(projectPath(projectId))
                    .request(MediaType.APPLICATION_JSON))
            .delete()) {

      // A 404 means the project is already gone — the exact goal state of a DELETE_PROJECT, in both
      // directions. A forward delete of a dataset whose project a prior run (or a failed saga's
      // compensation) already removed must succeed too, not strand the delete saga on the 404.
      if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
        log.info(
            "DELETE_PROJECT: project {} already absent (404) — treating as success. saga={}",
            Encode.forJava(projectId),
            Encode.forJava(command.sagaId()));
        return compensating
            ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
            : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
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

  /**
   * Optional payload map of provisioned FROST entity ids per entity set (payload key {@code
   * "provisionedEntities"}), filled by the portal once Datastream provisioning exists. Absent or
   * malformed → empty (nothing to delete).
   */
  private static Map<String, List<String>> provisionedEntities(SagaCommandMessage command) {
    Object raw = command.payload().get("provisionedEntities");
    // Absent key = payload predates (or doesn't use) the contract — nothing to delete. A PRESENT
    // key with the wrong shape is a broken producer and must fail loudly, like a malformed id.
    if (raw == null) {
      return Map.of();
    }
    if (!(raw instanceof Map<?, ?> bySet)) {
      throw new IllegalArgumentException(
          "DELETE_PROJECT payload field 'provisionedEntities' is not a map: "
              + raw.getClass().getSimpleName());
    }
    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<?, ?> entry : bySet.entrySet()) {
      if (!(entry.getValue() instanceof List<?> ids)) {
        throw new IllegalArgumentException(
            "DELETE_PROJECT payload field 'provisionedEntities."
                + entry.getKey()
                + "' is not a list");
      }
      result.put(
          String.valueOf(entry.getKey()),
          ids.stream().map(rawId -> scalarId(entry.getKey(), rawId)).toList());
    }
    return result;
  }

  /**
   * A provisioned entity id must be a non-blank scalar. Anything else (null, object, blank) is a
   * broken producer — failing loudly beats silently skipping the id, which would silently retain
   * the entity in FROST.
   */
  private static String scalarId(Object entitySet, Object rawId) {
    if (rawId instanceof Number number) {
      return String.valueOf(number);
    }
    if (rawId instanceof String id && !id.isBlank()) {
      return id;
    }
    throw new IllegalArgumentException(
        "DELETE_PROJECT payload field 'provisionedEntities."
            + entitySet
            + "' contains a non-scalar or blank id: "
            + rawId);
  }

  private SagaCommandResult handleRestoreProject(SagaCommandMessage command) {
    String projectId = requireString(command, KEY_PROJECT_ID);
    Object previousName = command.payload().get("previousName");
    String previousDescription = (String) command.payload().getOrDefault("previousDescription", "");

    Map<String, Object> body = new HashMap<>();
    // Only restore the name when the UPDATE saga captured a usable one. PATCHing "" would blank
    // the project identity and break the unique-name duplicate-recovery lookup; leaving the field
    // unset preserves the current value via PATCH semantics. (The public flag is not restored —
    // it is unconditionally forced false below, since FROST projects are never public.)
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
    // Force private on restore as well: FROST projects are never public in the OPA-decides model,
    // so a compensation must not resurrect a public flag. (No previousPublic is captured anymore.)
    body.put(KEY_PUBLIC, false);

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
