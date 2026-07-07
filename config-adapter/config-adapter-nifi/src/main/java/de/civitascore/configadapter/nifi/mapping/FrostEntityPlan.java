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

import java.util.List;
import java.util.Objects;

/**
 * The plan-time product of compiling a record mapping against a FROST sink: everything the sink's
 * linear find-or-create chain needs per entity, rendered from the flat capture attributes. A {@code
 * null} body means the entity is lookup-only (its miss routes to the error sink instead of a
 * create); an empty filter means the entity is not part of the mapping at all.
 *
 * <p>The body templates reference the chain's id attributes by the constants below — the compiler
 * renders the references, the sink build wires the processors that populate them.
 *
 * @param flatKeys the flat record-field/attribute names, in mapping order — an ordered list because
 *     the EvaluateJsonPath capture properties are appended in exactly this order (the snapshot is
 *     byte-deterministic, so no map iteration may decide it)
 * @param thingFilter the Thing lookup terms (never empty — a FROST mapping always maps the Thing)
 * @param thingBody the Thing create body (with Locations deep-inserted), or null when lookup-only
 * @param datastreamFilter the Datastream lookup terms, empty when the mapping maps no datastream
 * @param datastreamBody the Datastream create body (Sensor/ObservedProperty/unitOfMeasurement
 *     deep-inserted, Thing linked via {@link #THING_ID_ATTRIBUTE}), or null when lookup-only
 * @param observationBody the Observation body (Datastream linked via {@link #DS_ID_ATTRIBUTE}), or
 *     null when the mapping maps no observation
 */
public record FrostEntityPlan(
    List<String> flatKeys,
    List<FilterTerm> thingFilter,
    String thingBody,
    List<FilterTerm> datastreamFilter,
    String datastreamBody,
    String observationBody)
    implements SinkPreRegionPlan {

  /** Attribute holding the resolved Thing {@code @iot.id} after the Thing stage. */
  public static final String THING_ID_ATTRIBUTE = "frost.thing.id";

  /** Attribute holding the resolved Datastream {@code @iot.id} after the Datastream stage. */
  public static final String DS_ID_ATTRIBUTE = "frost.ds.id";

  /**
   * One conjunct of an entity's OData lookup filter: the FROST-side property path (from the closed
   * vocabulary plus a whitelisted key name, e.g. {@code properties/stationRef}) matched against the
   * value of a flat capture attribute.
   */
  public record FilterTerm(String frostPath, String flatKey) {
    public FilterTerm {
      Objects.requireNonNull(frostPath, "frostPath");
      Objects.requireNonNull(flatKey, "flatKey");
    }
  }

  public FrostEntityPlan {
    flatKeys = List.copyOf(Objects.requireNonNull(flatKeys, "flatKeys"));
    thingFilter = List.copyOf(Objects.requireNonNull(thingFilter, "thingFilter"));
    datastreamFilter = List.copyOf(Objects.requireNonNull(datastreamFilter, "datastreamFilter"));
    if (thingFilter.isEmpty()) {
      throw new IllegalArgumentException("a FROST entity plan requires a Thing lookup filter");
    }
    if (observationBody != null && datastreamFilter.isEmpty()) {
      throw new IllegalArgumentException("an observation body requires a datastream to attach to");
    }
  }
}
