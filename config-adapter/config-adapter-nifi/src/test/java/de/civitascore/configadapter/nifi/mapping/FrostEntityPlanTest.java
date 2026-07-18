/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The compiler only ever constructs valid plans, so the record's cross-field invariants are pinned
 * directly here — they are what makes an illegal chain (a body with no lookup, a filter term on an
 * uncaptured attribute, an unsafe filter path) unrepresentable rather than a deploy-time surprise.
 */
class FrostEntityPlanTest {

  private static final FilterTerm THING_TERM = new FilterTerm("properties/reference", "sta_0_ref");
  private static final FilterTerm DS_TERM = new FilterTerm("properties/reference", "sta_1_ref");

  @Test
  void rejectsAnUnsafeFilterPath() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterTerm("properties/reference eq '' or 1", "sta_0_ref"));
    assertTrue(ex.getMessage().contains("unsafe FROST filter path"));
  }

  @Test
  void acceptsADottedFreeVocabularyPath() {
    assertDoesNotThrow(() -> new FilterTerm("name", "sta_0_ref"));
    assertDoesNotThrow(() -> new FilterTerm("properties/stationRef", "sta_0_ref"));
  }

  @Test
  void requiresANonEmptyThingFilter() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> plan(List.of("sta_0_ref"), List.of(), null, List.of(), null, null));
    assertTrue(ex.getMessage().contains("requires a Thing lookup filter"));
  }

  @Test
  void anObservationBodyRequiresADatastreamFilter() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                plan(
                    List.of("sta_0_ref"),
                    List.of(THING_TERM),
                    null,
                    List.of(),
                    null,
                    "{\"result\":1}"));
    assertTrue(ex.getMessage().contains("observation body requires a datastream"));
  }

  @Test
  void aDatastreamBodyRequiresADatastreamFilter() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                plan(
                    List.of("sta_0_ref"),
                    List.of(THING_TERM),
                    null,
                    List.of(),
                    "{\"name\":\"x\"}",
                    null));
    assertTrue(ex.getMessage().contains("datastream body requires a datastream lookup filter"));
  }

  @Test
  void rejectsAFilterTermOnAnUncapturedFlatKey() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                plan(
                    List.of("sta_0_ref"), // sta_1_ref (the DS term) is not captured
                    List.of(THING_TERM),
                    null,
                    List.of(DS_TERM),
                    null,
                    null));
    assertTrue(ex.getMessage().contains("uncaptured flat key: sta_1_ref"));
  }

  @Test
  void acceptsALookupOnlyChainWithMatchingCaptures() {
    FrostEntityPlan plan =
        assertDoesNotThrow(
            () ->
                plan(
                    List.of("sta_0_ref", "sta_1_ref"),
                    List.of(THING_TERM),
                    null,
                    List.of(DS_TERM),
                    null,
                    "{\"result\":1}"));
    assertEquals(List.of(THING_TERM), plan.thingFilter());
    assertEquals(List.of(DS_TERM), plan.datastreamFilter());
  }

  @Test
  void relatedBodiesSupportLookupOnlyParentsButRequireTheirLookupStage() {
    assertDoesNotThrow(
        () ->
            new FrostEntityPlan(
                List.of("sta_0_ref", "sta_1_ref"),
                List.of(THING_TERM),
                null,
                null,
                "{\"name\":\"loc\"}",
                List.of(DS_TERM),
                null,
                null,
                "{\"name\":\"sensor\"}",
                "{\"name\":\"property\"}",
                null));

    IllegalArgumentException sensorWithoutDatastream =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FrostEntityPlan(
                    List.of("sta_0_ref"),
                    List.of(THING_TERM),
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    "{\"name\":\"sensor\"}",
                    null,
                    null));
    assertTrue(sensorWithoutDatastream.getMessage().contains("lookup filter"));
  }

  private static FrostEntityPlan plan(
      List<String> flatKeys,
      List<FilterTerm> thingFilter,
      String thingBody,
      List<FilterTerm> datastreamFilter,
      String datastreamBody,
      String observationBody) {
    return new FrostEntityPlan(
        flatKeys,
        thingFilter,
        thingBody,
        null,
        null,
        datastreamFilter,
        datastreamBody,
        null,
        null,
        null,
        observationBody);
  }
}
