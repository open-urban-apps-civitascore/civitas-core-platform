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

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed, validated view of a route command's payload — decoded once from the loosely-typed saga
 * {@code Map} (which arrives via the Kafka → Flowable variable round-trip). It carries the
 * named-API {@code slugs} and per-slug {@code standardBySlug} (CREATE, drift checks), the
 * slug-keyed {@code routeIds} (UPDATE/DELETE/RESTORE) and the per-slug previous open-data flags
 * (RESTORE). An empty {@link #slugs}/{@link #routeIds} means the dataset has no named APIs: CREATE
 * provisions nothing and UPDATE/DELETE/RESTORE no-op (the legacy single dataset-level route was
 * removed), expressed in one place instead of re-derived in each handler.
 *
 * @param slugs named-API slugs from the {@code namedApis} payload entry (entries without a usable
 *     slug are dropped and logged)
 * @param routeIds slug-keyed APISIX route ids persisted by the portal-backend from the prior
 *     CREATE_ROUTE result (null-keyed/-valued entries are dropped and logged)
 * @param previousOpenBySlug per-slug open-data state captured by UPDATE_ROUTE for RESTORE_ROUTE
 * @param standardBySlug per-slug API standard ({@code STA}/{@code OWS}/{@code CUSTOM}) gating
 *     routability in CREATE_ROUTE and selecting the upstream (see {@link RouteUpstreamKind})
 */
record RoutePayload(
    List<String> slugs,
    Map<String, String> routeIds,
    Map<String, Boolean> previousOpenBySlug,
    Map<String, String> standardBySlug) {

  private static final Logger LOG = LoggerFactory.getLogger(RoutePayload.class);

  static RoutePayload decode(SagaCommandMessage command) {
    return new RoutePayload(
        readSlugs(command),
        readRouteIds(command),
        readPreviousOpenBySlug(command),
        readStandardBySlug(command));
  }

  /**
   * Named-API slugs that have no entry in {@link #routeIds} — the persisted route map drifted from
   * the dataset's named APIs. UPDATE fails loud on this (all-or-nothing), DELETE heals it by
   * deriving the deterministic route id.
   */
  List<String> slugsWithoutRouteId() {
    return slugs.stream().filter(slug -> !routeIds.containsKey(slug)).toList();
  }

  /**
   * Per-slug routeIds are deterministic: {@code routeId == NamedApiHelper.derive(datasetId, slug)}.
   * The persisted map is round-tripped from the CREATE_ROUTE result through the portal-backend, so
   * a value that no longer matches the derivation means the stored map drifted from the gateway —
   * exactly the mismatch that produces UPDATE/DELETE 404s (#1368). We don't auto-heal (the stored
   * id is what the gateway route was created under), but we surface the divergence so it's
   * attributable rather than a silent later 404.
   */
  void logRouteIdDivergence(String datasetId, String sagaId) {
    for (Map.Entry<String, String> entry : routeIds.entrySet()) {
      String derived = NamedApiHelper.derive(datasetId, entry.getKey());
      if (!derived.equals(entry.getValue())) {
        LOG.warn(
            "Route command: persisted routeId {} for slug '{}' diverges from the deterministic id"
                + " {} (datasetId={}) — stored route map may be stale. saga={}",
            Encode.forJava(entry.getValue()),
            Encode.forJava(entry.getKey()),
            Encode.forJava(derived),
            Encode.forJava(datasetId),
            Encode.forJava(sagaId));
      }
    }
  }

  private static List<String> readSlugs(SagaCommandMessage command) {
    Object raw = command.payload().get("namedApis");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> slugs = new ArrayList<>();
    int dropped = 0;
    for (Object item : list) {
      Object slug = item instanceof Map<?, ?> map ? map.get("slug") : null;
      if (slug != null && !slug.toString().isBlank()) {
        slugs.add(slug.toString());
      } else {
        dropped++;
      }
    }
    if (dropped > 0) {
      // A malformed namedApi entry means a published API would silently get no gateway route — make
      // the drop visible instead of failing 404 unexplained later.
      LOG.warn(
          "Route command: ignored {} namedApi entry/entries without a usable slug (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return slugs;
  }

  /**
   * Reads the slug-keyed {@code routeIds} map from an UPDATE/DELETE/RESTORE command (persisted by
   * the portal-backend from the prior CREATE_ROUTE result). Returns an empty map when absent — that
   * is a dataset with no named-API routes (the legacy singular {@code routeId} fallback was
   * removed).
   */
  private static Map<String, String> readRouteIds(SagaCommandMessage command) {
    Object raw = command.payload().get("routeIds");
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, String> routeIds = new LinkedHashMap<>();
    int dropped = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        routeIds.put(entry.getKey().toString(), entry.getValue().toString());
      } else {
        dropped++;
      }
    }
    if (dropped > 0) {
      LOG.warn(
          "Route command: ignored {} routeIds entry/entries with a null key/value (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return routeIds;
  }

  /** Reads the per-slug previous open-data state captured by UPDATE_ROUTE for RESTORE_ROUTE. */
  private static Map<String, Boolean> readPreviousOpenBySlug(SagaCommandMessage command) {
    Object raw = command.payload().get("previousOpenDataAccess");
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Boolean> previous = new LinkedHashMap<>();
    int dropped = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() == null) {
        dropped++;
        continue;
      }
      previous.put(
          entry.getKey().toString(),
          Boolean.TRUE.equals(entry.getValue())
              || "true".equalsIgnoreCase(String.valueOf(entry.getValue())));
    }
    if (dropped > 0) {
      LOG.warn(
          "RESTORE_ROUTE: ignored {} previousOpenDataAccess entry/entries with a null key (saga={})",
          dropped,
          Encode.forJava(command.sagaId()));
    }
    return previous;
  }

  /** Per-slug API standard from the {@code namedApis} payload, used to gate CREATE_ROUTE. */
  private static Map<String, String> readStandardBySlug(SagaCommandMessage command) {
    Object raw = command.payload().get("namedApis");
    if (!(raw instanceof List<?> list)) {
      return Map.of();
    }
    Map<String, String> standards = new LinkedHashMap<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Object slug = map.get("slug");
        if (slug != null && !slug.toString().isBlank()) {
          Object standard = map.get("standard");
          standards.put(slug.toString(), standard == null ? null : standard.toString());
        }
      }
    }
    return standards;
  }
}
