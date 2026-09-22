/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The sub-requests one record contributes to a batch, in the order the port declared them.
 *
 * <p>The order carries meaning that the {@code if} expressions alone do not. A lookup and its
 * conditional create share an identifier, so a request that must see only the lookup result has to
 * stand before the create: after the create the identifier resolves again, and a condition written
 * for the found entity would fire a second time for the created one.
 */
public final class RecordPlan {

  private final int recordIndex;
  private final List<SubRequest> requests = new ArrayList<>();
  private final Set<String> entitiesRequiringWrite = new LinkedHashSet<>();

  public RecordPlan(int recordIndex) {
    if (recordIndex < 0) {
      throw new IllegalArgumentException("record index must not be negative");
    }
    this.recordIndex = recordIndex;
  }

  public int recordIndex() {
    return recordIndex;
  }

  /** The sub-request identifier for a suffix of this record, for use in a back-reference. */
  public String id(String suffix) {
    return SubRequestId.of(recordIndex, suffix);
  }

  /** Adds a lookup of a reference the record does not own. An empty result fails the record. */
  public void parentLookup(String entity, String idSuffix, String url) {
    add(SubRequestRole.PARENT_LOOKUP, entity, idSuffix, BatchMethod.GET, url, null, null, null);
  }

  /**
   * Adds a lookup of something the record needs but does not write, which runs only under a
   * condition and names its own reason when it finds nothing — for a precondition FROST would
   * otherwise report as an unexplained 400.
   */
  public void parentLookup(
      String entity, String idSuffix, String url, String condition, String missReason) {
    add(
        SubRequestRole.PARENT_LOOKUP,
        entity,
        idSuffix,
        BatchMethod.GET,
        url,
        condition,
        null,
        missReason);
  }

  /** Adds a lookup of the record's own reference. An empty result lets the create run. */
  public void ownLookup(String entity, String idSuffix, String url) {
    ownLookup(entity, idSuffix, url, null);
  }

  /** Adds a lookup of the record's own reference that runs only under a condition. */
  public void ownLookup(String entity, String idSuffix, String url, String condition) {
    add(SubRequestRole.OWN_LOOKUP, entity, idSuffix, BatchMethod.GET, url, condition, null, null);
  }

  /**
   * Adds a write.
   *
   * @param required whether the entity must see one successful write before the record counts as
   *     written. Both halves of an upsert pass true: exactly one of them runs, and the entity is
   *     unwritten if neither does. A write that covers one branch only — the update of a nested
   *     entity that the create deep-inserts — passes false, because the other branch writes it by
   *     another route.
   */
  public void write(
      String entity,
      String idSuffix,
      BatchMethod method,
      String url,
      String condition,
      JsonNode body,
      boolean required) {
    add(SubRequestRole.WRITE, entity, idSuffix, method, url, condition, body, null);
    if (required) {
      entitiesRequiringWrite.add(entity);
    }
  }

  private void add(
      SubRequestRole role,
      String entity,
      String idSuffix,
      BatchMethod method,
      String url,
      String condition,
      JsonNode body,
      String missReason) {
    requests.add(
        new SubRequest(
            id(idSuffix),
            SubRequestId.group(recordIndex),
            role,
            entity,
            method,
            url,
            condition,
            body,
            missReason));
  }

  public List<SubRequest> requests() {
    return List.copyOf(requests);
  }

  /** The entities that must see one successful write before the record counts as written. */
  public Collection<String> entitiesRequiringWrite() {
    return Set.copyOf(entitiesRequiringWrite);
  }
}
