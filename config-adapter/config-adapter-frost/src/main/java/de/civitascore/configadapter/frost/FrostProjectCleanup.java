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

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler.SagaApiException;
import de.civitascore.configadapter.util.OkHttpJson;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a FROST project's contents before the project itself is removed: FROST does not cascade
 * project deletion, but deleting a Thing cascades to its Datastreams and Observations. Entities
 * provisioned outside that cascade (e.g. Sensors) are deleted by identity.
 */
final class FrostProjectCleanup {

  /**
   * Deletion order: listed Datastreams before Sensors, because a Sensor delete cascades to its
   * remaining Datastreams. This protects only Datastreams in the list — ids are assumed to be
   * provisioned per dataset, so a listed Sensor sharing an unlisted live Datastream is not
   * expected.
   */
  static final List<String> PROVISIONABLE_ENTITY_SETS =
      List.of("Datastreams", "Sensors", "ObservedProperties", "FeaturesOfInterest", "Locations");

  // Page size for entity enumeration and chunk size for batched deletes: one page → one batch.
  private static final int BATCH_SIZE = 100;

  private final Logger log = LoggerFactory.getLogger(FrostProjectCleanup.class);

  private final OkHttpClient client;
  private final FrostAuthStrategy authStrategy;
  private final String serverUrl;
  private final String sagaId;

  FrostProjectCleanup(
      OkHttpClient client, FrostAuthStrategy authStrategy, String serverUrl, String sagaId) {
    this.client = client;
    this.authStrategy = authStrategy;
    this.serverUrl = serverUrl;
    this.sagaId = sagaId;
  }

  /** The FROST entity URL for a path relative to {@link #serverUrl}, e.g. {@code Projects(42)}. */
  private HttpUrl url(String path) {
    return OkHttpJson.url(serverUrl, path);
  }

  /**
   * Deletes the project's Things (cascading to their Datastreams and Observations).
   *
   * <p>Re-run safe: already-deleted Things (404) are skipped, and a 404 on the enumeration means
   * the whole project is already gone — nothing to clean up, in either direction. A forward delete
   * of a dataset whose project a prior run (or a failed saga's compensation) already removed
   * therefore succeeds rather than stranding on the 404.
   */
  void deleteProjectThings(String projectId) {
    // Collect all ids before deleting (deleting while paging shifts pages). Explicit $skip paging
    // instead of @iot.nextLink: FROST renders that link from its serviceRootUrl, which is not
    // necessarily reachable from this adapter. Stable because nothing writes during this step.
    List<String> thingIds = new ArrayList<>();
    int skip = 0;
    while (true) {
      HttpUrl url =
          url("Projects(" + projectId + ")/Things")
              .newBuilder()
              .addQueryParameter("$select", "@iot.id")
              .addQueryParameter("$orderby", "id asc")
              .addQueryParameter("$top", String.valueOf(BATCH_SIZE))
              .addQueryParameter("$skip", String.valueOf(skip))
              .build();
      Request request = authStrategy.apply(OkHttpJson.jsonRequest(url).get()).build();
      try (Response response = OkHttpJson.execute(client, request)) {
        if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
          // A missing project means there is nothing left to delete — idempotent in both
          // directions. This also covers a re-release/re-delete after an earlier run already
          // removed the project (e.g. a prior failed saga's compensation), so the forward delete
          // must not fail on the 404 either.
          log.info(
              "DELETE_PROJECT: project {} not found while listing its Things — skipping Thing"
                  + " cleanup (already gone). saga={}",
              Encode.forJava(projectId),
              Encode.forJava(sagaId));
          return;
        }
        checkResponse(response, "GET project Things for DELETE_PROJECT");

        Map<String, Object> page = OkHttpJson.readJsonMap(response);
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

    int deletedCount = deleteEntities("Things", thingIds);

    log.info(
        "FROST project Things deleted (cascading to their Datastreams and Observations):"
            + " projectId={}, thingsDeleted={}, saga={}",
        Encode.forJava(projectId),
        deletedCount,
        Encode.forJava(sagaId));
  }

  /**
   * Deletes entities provisioned for the dataset by identity, in {@link #PROVISIONABLE_ENTITY_SETS}
   * order. Ids already removed by the Thing cascade are tolerated (404); unknown entity sets are
   * ignored with a warning.
   */
  void deleteProvisionedEntities(Map<String, List<String>> entityIdsBySet) {
    for (String entitySet : PROVISIONABLE_ENTITY_SETS) {
      List<String> entityIds = entityIdsBySet.getOrDefault(entitySet, List.of());
      if (entityIds.isEmpty()) {
        continue;
      }
      int deleted = deleteEntities(entitySet, entityIds);
      log.info(
          "FROST provisioned {} deleted: requested={}, deleted={}, saga={}",
          Encode.forJava(entitySet),
          entityIds.size(),
          deleted,
          Encode.forJava(sagaId));
    }
    for (String entitySet : entityIdsBySet.keySet()) {
      if (!PROVISIONABLE_ENTITY_SETS.contains(entitySet)) {
        log.warn(
            "DELETE_PROJECT: ignoring unknown provisioned entity set '{}'. saga={}",
            Encode.forJava(entitySet),
            Encode.forJava(sagaId));
      }
    }
  }

  /** Deletes the given entities in JSON batch requests, chunked at {@link #BATCH_SIZE}. */
  private int deleteEntities(String entitySet, List<String> entityIds) {
    int deleted = 0;
    for (int from = 0; from < entityIds.size(); from += BATCH_SIZE) {
      List<String> chunk = entityIds.subList(from, Math.min(from + BATCH_SIZE, entityIds.size()));
      deleted += deleteEntitiesBatch(entitySet, chunk);
    }
    return deleted;
  }

  /**
   * Deletes one chunk in a single JSON batch request. Sub-requests execute independently: 404 means
   * already gone (skip), any other failure fails the step. Every entity needs a confirmed outcome —
   * a truncated batch response fails rather than leaving entities behind.
   *
   * @return the number of entities actually deleted
   */
  private int deleteEntitiesBatch(String entitySet, List<String> entityIds) {
    List<Map<String, Object>> requests = new ArrayList<>();
    for (int i = 0; i < entityIds.size(); i++) {
      requests.add(
          Map.of(
              "id",
              String.valueOf(i),
              "method",
              "delete",
              "url",
              entitySet + "(" + entityIds.get(i) + ")"));
    }

    Request request =
        authStrategy
            .apply(
                OkHttpJson.jsonRequest(url("$batch"))
                    .post(OkHttpJson.jsonBodyUnchecked(Map.of("requests", requests))))
            .build();
    try (Response response = OkHttpJson.execute(client, request)) {
      checkResponse(response, batchOperation(entitySet));

      Map<String, Object> body = OkHttpJson.readJsonMap(response);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> subResponses =
          (List<Map<String, Object>>) body.getOrDefault("responses", List.of());
      if (subResponses.size() != entityIds.size()) {
        throw new SagaApiException(
            batchOperation(entitySet)
                + " returned "
                + subResponses.size()
                + " sub-responses for "
                + entityIds.size()
                + " requests",
            502);
      }

      int deleted = 0;
      boolean[] confirmed = new boolean[entityIds.size()];
      for (Map<String, Object> subResponse : subResponses) {
        deleted += confirmSubResponse(entitySet, entityIds, confirmed, subResponse);
      }
      return deleted;
    }
  }

  /**
   * Confirms one entity's outcome from its batch sub-response: 404 counts as already gone, any
   * other failure fails the step.
   *
   * @return 1 if the entity was deleted, 0 if it was already absent
   */
  private int confirmSubResponse(
      String entitySet,
      List<String> entityIds,
      boolean[] confirmed,
      Map<String, Object> subResponse) {
    int requestIndex = requestIndex(subResponse.get("id"), confirmed, entitySet);
    if (!(subResponse.get("status") instanceof Number statusNumber)) {
      throw new SagaApiException(
          batchOperation(entitySet) + " returned no status for sub-response id " + requestIndex,
          502);
    }
    int status = statusNumber.intValue();
    String entityPath = entitySet + "(" + entityIds.get(requestIndex) + ")";
    if (status == HttpURLConnection.HTTP_NOT_FOUND) {
      log.debug(
          "DELETE_PROJECT: {} already absent (404) — continuing. saga={}",
          Encode.forJava(entityPath),
          Encode.forJava(sagaId));
      return 0;
    }
    if (status < 200 || status >= 300) {
      throw new SagaApiException(
          "DELETE "
              + entityPath
              + " for DELETE_PROJECT failed: HTTP "
              + status
              + " — "
              + subResponse.get("body"),
          status);
    }
    return 1;
  }

  /**
   * Resolves a batch sub-response id back to its request index and marks it confirmed. Sub-response
   * ids echo the request indexes; a malformed, out-of-range or duplicate id means some entity's
   * outcome is unconfirmed — together with the size check, exactly-once confirmation guarantees
   * full coverage.
   */
  private int requestIndex(Object rawId, boolean[] confirmed, String entitySet) {
    String idText = String.valueOf(rawId);
    // Request ids are the indexes 0..99 we generated — anything non-numeric is malformed.
    if (!idText.matches("\\d{1,3}")) {
      throw new SagaApiException(
          batchOperation(entitySet) + " returned a malformed sub-response id: " + idText, 502);
    }
    int index = Integer.parseInt(idText);
    if (index >= confirmed.length || confirmed[index]) {
      throw new SagaApiException(
          batchOperation(entitySet) + " returned an unknown or duplicate sub-response id: " + index,
          502);
    }
    confirmed[index] = true;
    return index;
  }

  /** Operation label for error messages, e.g. {@code batch DELETE Things for DELETE_PROJECT}. */
  private static String batchOperation(String entitySet) {
    return "batch DELETE " + entitySet + " for DELETE_PROJECT";
  }

  private void checkResponse(Response response, String operationDesc) {
    if (response.isSuccessful()) {
      return;
    }
    throw new SagaApiException(
        operationDesc + " failed: HTTP " + response.code() + " — " + OkHttpJson.readBody(response),
        response.code());
  }
}
