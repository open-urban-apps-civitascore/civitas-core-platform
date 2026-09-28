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
import de.civitascore.configadapter.util.OkHttpJson;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for the FROST SensorThings API. Handles project-level operations dispatched
 * by the saga orchestrator:
 *
 * <ul>
 *   <li>{@code CREATE_PROJECT} — POST /Projects
 *   <li>{@code UPDATE_PROJECT} — PATCH /Projects({projectId}), falling back to CREATE_PROJECT when
 *       the dataset has no project yet (a FROST sink added after a release without one)
 *   <li>{@code DELETE_PROJECT} — DELETE all Things of the project (cascades to their Datastreams
 *       and Observations), then DELETE /Projects({projectId}). As a CREATE_PROJECT compensation it
 *       is a no-op when the create only reused a pre-existing project ({@code created=false}), so a
 *       later step's failure cannot destroy the data the unrelease flow preserves.
 *   <li>{@code RESTORE_PROJECT} — PATCH /Projects({projectId}) with previous state (update
 *       compensation). Delegates to {@code DELETE_PROJECT} when the update provisioned the project
 *       rather than patching one, since there is no previous state to restore.
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
  private static final String KEY_DATASET_ID = "datasetId";
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

  void setTestClient(OkHttpClient client) {
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
    return datasetName + " " + frostProjectNameSuffix(datasetId);
  }

  /**
   * The immutable {@code "(datasetId)"} suffix of a FROST project name, shared by {@link
   * #frostProjectName} and the datasetId lookup filter in {@link #reuseProjectByDatasetId} so the
   * two can never drift apart — a mismatch would make the lookup miss the dataset's own project and
   * let CREATE_PROJECT create a duplicate.
   */
  private static String frostProjectNameSuffix(String datasetId) {
    return "(" + datasetId + ")";
  }

  /** FROST entity path of a project, e.g. {@code Projects(42)}. */
  private static String projectPath(String projectId) {
    return "Projects(" + projectId + ")";
  }

  /** Public base URL of a project, reported back to the portal as the dataset payload root. */
  private String projectBaseUrl(String projectId) {
    return publicUrl + "/" + projectPath(projectId);
  }

  /** The FROST entity URL for a path relative to {@link #serverUrl}, e.g. {@code Projects(42)}. */
  private HttpUrl url(String path) {
    return OkHttpJson.url(serverUrl, path);
  }

  private SagaCommandResult handleCreateProject(SagaCommandMessage command) {
    String datasetName = requireString(command, "datasetName");
    String datasetId = requireString(command, KEY_DATASET_ID);
    String description = (String) command.payload().getOrDefault(KEY_DESCRIPTION, "");

    String projectName = frostProjectName(datasetName, datasetId);

    // Find-or-create: the lookup matches on the immutable datasetId suffix of the name, so a match
    // can only be this dataset's own project — a prior CREATE_PROJECT that already ran (re-release,
    // retried saga) — even when the display-name portion is now stale because the dataset was
    // renamed while unreleased. Reusing it keeps the FROST data (Things → Datastreams →
    // Observations) intact instead of orphaning it behind a second project.
    SagaCommandResult existing =
        reuseProjectByDatasetId(command, datasetId, projectName, description);
    if (existing != null) {
      return existing;
    }

    return createNewProject(command, projectName, description);
  }

  /** POSTs a new private FROST project, with a duplicate-name race guard (409 or 500). */
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

    Request request =
        authStrategy
            .apply(OkHttpJson.jsonRequest(url("Projects")).post(OkHttpJson.jsonBodyUnchecked(body)))
            .build();
    try (Response response = execute(request)) {

      int status = response.code();
      if (status == HttpURLConnection.HTTP_CONFLICT
          || status == HttpURLConnection.HTTP_INTERNAL_ERROR) {
        return handleCreateProjectConflict(command, projectName, response);
      }

      checkResponse(response, "CREATE_PROJECT");

      String projectId = FrostUtils.extractIdFromLocation(response.header("Location"));
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
   * Handles an HTTP 409 or 500 from the CREATE_PROJECT POST. This is a race guard: the up-front
   * find-or-create lookup already ran, but a concurrent CREATE may have inserted the project in
   * between — so re-run the dataset-id lookup and reuse the winner.
   *
   * <p>FROST-Server core &gt;= 2.7.0 answers a UNIQUE constraint violation (duplicate project name)
   * with 409 Conflict; earlier cores answer 500 with a {@code "Failed to store data."} body, which
   * is otherwise indistinguishable from a genuine server error and must be checked for before
   * treating it as a duplicate.
   */
  private SagaCommandResult handleCreateProjectConflict(
      SagaCommandMessage command, String projectName, Response response) {
    int status = response.code();
    String responseBody = readBody(response);
    boolean isDuplicate =
        status == HttpURLConnection.HTTP_CONFLICT
            || responseBody.contains(FrostAdapter.ERROR_FAILED_TO_STORE_DATA);
    if (isDuplicate) {
      String datasetId = requireString(command, KEY_DATASET_ID);
      log.info(
          "FROST returned {} for CREATE_PROJECT — re-checking for an existing project for dataset"
              + " '{}', saga={}",
          status,
          Encode.forJava(datasetId),
          Encode.forJava(command.sagaId()));
      String description = (String) command.payload().getOrDefault(KEY_DESCRIPTION, "");
      SagaCommandResult recovered =
          reuseProjectByDatasetId(command, datasetId, projectName, description);
      if (recovered != null) {
        return recovered;
      }
      throw new SagaApiException(
          "CREATE_PROJECT failed: HTTP "
              + status
              + " — no existing project found for dataset '"
              + datasetId
              + "'",
          status);
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
   * Looks up a project by the immutable datasetId suffix of its name — {@code
   * endswith(name,'({datasetId})')} — rather than the full name, so a match survives the dataset's
   * display name changing since the project was created. On a match it also PATCHes the project's
   * name/description to the freshly computed values, self-healing a display name left stale by a
   * rename that happened while the dataset was unreleased. Returns a successful {@link
   * SagaCommandResult} bound to the existing project, or {@code null} if none exists.
   */
  private SagaCommandResult reuseProjectByDatasetId(
      SagaCommandMessage command, String datasetId, String projectName, String description) {
    String escapedDatasetId = datasetId.replace("'", "''");
    HttpUrl url =
        url("Projects")
            .newBuilder()
            .addQueryParameter(
                "$filter", "endswith(name,'" + frostProjectNameSuffix(escapedDatasetId) + "')")
            .build();
    Request request = authStrategy.apply(OkHttpJson.jsonRequest(url).get()).build();
    try (Response response = execute(request)) {

      checkResponse(response, "GET_PROJECTS_BY_DATASET_ID");

      Map<String, Object> result = OkHttpJson.readJsonMap(response);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> projects = (List<Map<String, Object>>) result.get("value");

      if (projects == null || projects.isEmpty()) {
        return null;
      }

      String projectId = String.valueOf(projects.get(0).get("@iot.id"));
      return patchAndBuildReuseResult(command, projectId, projectName, description);
    }
  }

  /**
   * PATCHes a known-existing project's name/description to the freshly computed values —
   * self-healing a display name left stale by a rename that happened while the dataset was
   * unreleased — then returns success reusing it. Compensation is {@code created=false}: a
   * CREATE_PROJECT compensation must not delete data a re-release is meant to reuse.
   */
  private SagaCommandResult patchAndBuildReuseResult(
      SagaCommandMessage command, String projectId, String projectName, String description) {
    Map<String, Object> body =
        Map.of(KEY_NAME, projectName, KEY_DESCRIPTION, description, KEY_PUBLIC, false);

    Request request =
        authStrategy
            .apply(
                OkHttpJson.jsonRequest(url(projectPath(projectId)))
                    .patch(OkHttpJson.jsonBodyUnchecked(body)))
            .build();
    try (Response response = execute(request)) {

      checkResponse(response, "CREATE_PROJECT (reuse)");
    }

    String baseUrl = projectBaseUrl(projectId);
    Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
    Map<String, Object> compensationData = Map.of(KEY_PROJECT_ID, projectId, KEY_CREATED, false);

    log.info(
        "FROST CREATE_PROJECT reused the existing project, syncing its name: projectId={},"
            + " saga={}",
        Encode.forJava(projectId),
        Encode.forJava(command.sagaId()));

    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), resultData, compensationData);
  }

  private SagaCommandResult handleUpdateProject(SagaCommandMessage command) {
    // A FROST sink added to a dataset that was released without one has no project yet, and the
    // update saga is gated on the sink rather than on the project. Provision it here instead of
    // failing: the create path is find-or-create on a name carrying the dataset id, so it is
    // idempotent and can only ever bind this dataset to its own project.
    if (!(command.payload().get(KEY_PROJECT_ID) instanceof String projectId)
        || projectId.isBlank()) {
      log.info(
          "UPDATE_PROJECT: dataset {} has no FROST project yet — provisioning it. saga={}",
          Encode.forJava((String) command.payload().get(KEY_DATASET_ID)),
          Encode.forJava(command.sagaId()));
      return handleCreateProject(command);
    }

    String datasetName = requireString(command, "datasetName");
    String datasetId = requireString(command, KEY_DATASET_ID);
    String description = (String) command.payload().getOrDefault(KEY_DESCRIPTION, "");

    // Read current state before updating (needed for compensation)
    String previousName;
    String previousDescription;
    Request getRequest =
        authStrategy.apply(OkHttpJson.jsonRequest(url(projectPath(projectId))).get()).build();
    try (Response getResponse = execute(getRequest)) {
      checkResponse(getResponse, "GET project for UPDATE_PROJECT");
      Map<String, Object> currentProject = OkHttpJson.readJsonMap(getResponse);
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

    Request request =
        authStrategy
            .apply(
                OkHttpJson.jsonRequest(url(projectPath(projectId)))
                    .patch(OkHttpJson.jsonBodyUnchecked(body)))
            .build();
    try (Response response = execute(request)) {

      checkResponse(response, "UPDATE_PROJECT");

      String baseUrl = projectBaseUrl(projectId);
      Map<String, Object> resultData = Map.of(KEY_PROJECT_ID, projectId, "baseUrl", baseUrl);
      // created=false: this project pre-existed and holds data the unrelease flow intentionally
      // preserves — a CREATE_PROJECT compensation must NOT delete it, or a later step's failure
      // would destroy exactly the storage re-release is meant to reuse.
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
    boolean compensating = "COMPENSATE_STEP".equals(command.type());

    // No project id is the same goal state as the 404 below: there is nothing to delete. A dataset
    // that never reached a release has none, and this step is not gated on one — demanding it here
    // would fail the teardown of a dataset that owns no FROST project at all.
    if (!(command.payload().get(KEY_PROJECT_ID) instanceof String projectId)
        || projectId.isBlank()) {
      log.info(
          "DELETE_PROJECT: no project provisioned for this dataset — nothing to delete. saga={}",
          Encode.forJava(command.sagaId()));
      return compensating
          ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
          : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
    }

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

    Request request =
        authStrategy.apply(OkHttpJson.jsonRequest(url(projectPath(projectId))).delete()).build();
    try (Response response = execute(request)) {

      // A 404 means the project is already gone — the exact goal state of a DELETE_PROJECT, in both
      // directions. A forward delete of a dataset whose project a prior run (or a failed saga's
      // compensation) already removed must succeed too, not strand the delete saga on the 404.
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
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
    // The created marker is only written by the create path, so its presence means UPDATE_PROJECT
    // provisioned the project instead of patching an existing one. There is no previous state to
    // restore then — the exact inverse is the delete, which itself preserves a project it merely
    // reused (created=false). PATCHing here would instead blank the description of a project that
    // should have been removed.
    if (command.payload().containsKey(KEY_CREATED)) {
      return handleDeleteProject(command);
    }

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

    Request request =
        authStrategy
            .apply(
                OkHttpJson.jsonRequest(url(projectPath(projectId)))
                    .patch(OkHttpJson.jsonBodyUnchecked(body)))
            .build();
    try (Response response = execute(request)) {

      checkResponse(response, "RESTORE_PROJECT");

      log.info(
          "FROST project restored: projectId={}, saga={}",
          Encode.forJava(projectId),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
    }
  }
}
