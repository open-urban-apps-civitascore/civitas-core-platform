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
import java.util.ArrayList;
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
 *       and Observations), then DELETE /Projects({projectId})
 *   <li>{@code RESTORE_PROJECT} — PATCH /Projects({projectId}) with previous state (update
 *       compensation)
 * </ul>
 *
 * <p>Compensation operations: {@code DELETE_PROJECT} to compensate a {@code CREATE_PROJECT}, {@code
 * RESTORE_PROJECT} to compensate an {@code UPDATE_PROJECT}.
 */
@SuppressWarnings("PMD.TooManyMethods") // One method per saga operation plus focused helpers
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

    String projectName = frostProjectName(datasetName, datasetId);
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

    deleteProjectThings(projectId, command.sagaId());

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

  /**
   * Deletes all Things of a project before the project itself is deleted. FROST does not cascade
   * project deletion — {@code DELETE /Projects(n)} removes only the project and its membership
   * links, while the member Things, Datastreams and Observations survive at server root,
   * unreachable through any project. Deleting a Thing, however, DOES cascade to its Datastreams and
   * their Observations, so removing the project's Things first removes the project's sensor data
   * with it. Not covered by that cascade: Sensors, ObservedProperties, FeaturesOfInterest and
   * Locations shared across Things.
   *
   * <p>Safe to re-run (compensation retries, partial earlier cleanup): a project that is already
   * absent (enumeration 404) skips the cleanup entirely — the subsequent project delete remains the
   * single place deciding idempotency semantics — and a Thing that is already gone (delete 404) is
   * skipped.
   */
  private void deleteProjectThings(String projectId, String sagaId) {
    // Collect all Thing ids fully BEFORE deleting: deleting while paging shifts the pages and
    // would skip Things. Pages are requested with explicit $skip/$orderby instead of following
    // @iot.nextLink verbatim — FROST renders that link from its configured serviceRootUrl, which
    // is not necessarily reachable from this adapter (internal vs. public URL). $skip paging is
    // stable here: the saga tears down the writing pipelines before this step, and no Thing is
    // deleted until the enumeration is complete.
    List<String> thingIds = new ArrayList<>();
    int skip = 0;
    while (true) {
      try (Response response =
          authStrategy
              .apply(
                  client()
                      .target(serverUrl)
                      .path(projectPath(projectId) + "/Things")
                      .queryParam("$select", "@iot.id")
                      .queryParam("$orderby", "id asc")
                      .queryParam("$top", "100")
                      .queryParam("$skip", String.valueOf(skip))
                      .request(MediaType.APPLICATION_JSON))
              .get()) {
        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
          log.info(
              "DELETE_PROJECT: project {} not found while listing its Things — skipping Thing"
                  + " cleanup. saga={}",
              Encode.forJava(projectId),
              Encode.forJava(sagaId));
          return;
        }
        checkResponse(response, "GET project Things for DELETE_PROJECT");

        @SuppressWarnings("unchecked")
        Map<String, Object> page = response.readEntity(Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> things =
            (List<Map<String, Object>>) page.getOrDefault("value", List.of());
        for (Map<String, Object> thing : things) {
          thingIds.add(String.valueOf(thing.get("@iot.id")));
        }
        if (things.isEmpty() || page.get("@iot.nextLink") == null) {
          break;
        }
        skip += things.size();
      }
    }

    int deletedCount = 0;
    for (String thingId : thingIds) {
      try (Response response =
          authStrategy
              .apply(
                  client()
                      .target(serverUrl)
                      .path("Things(" + thingId + ")")
                      .request(MediaType.APPLICATION_JSON))
              .delete()) {
        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
          log.debug(
              "DELETE_PROJECT: Thing {} already absent (404) — continuing. saga={}",
              Encode.forJava(thingId),
              Encode.forJava(sagaId));
          continue;
        }
        checkResponse(response, "DELETE Thing for DELETE_PROJECT");
        deletedCount++;
      }
    }

    log.info(
        "FROST project Things deleted (cascading to their Datastreams and Observations):"
            + " projectId={}, thingsDeleted={}, saga={}",
        Encode.forJava(projectId),
        deletedCount,
        Encode.forJava(sagaId));
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
