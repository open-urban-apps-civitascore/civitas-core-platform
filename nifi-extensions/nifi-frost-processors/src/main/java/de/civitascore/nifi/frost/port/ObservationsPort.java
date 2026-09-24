/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.nifi.frost.batch.BatchMethod;
import de.civitascore.nifi.frost.batch.RecordPlan;
import de.civitascore.nifi.frost.batch.SubRequest;
import java.util.Optional;

/**
 * Appends an Observation to a Datastream that must exist.
 *
 * <p>The Datastream is resolved through {@code datastreamReference} and {@code thingReference}; a
 * miss is a data error, because this port creates only its own entity. The Observation's own {@code
 * reference} is optional and decides the write: with one the port upserts, so a second delivery of
 * the same message updates the measurement, and without one every delivery appends a new one.
 */
public final class ObservationsPort implements PortPlanner {

  private static final String PARENT = "ds";
  private static final String POSITION = "ds-loc";
  private static final String LOOKUP = "obs";
  private static final String UPDATE = "obs-update";

  private static final String FEATURE_OF_INTEREST = "FeatureOfInterest";

  @Override
  public void plan(ObjectNode record, RecordPlan plan, String projectId) {
    String datastreamReference =
        ReferenceBlock.require(
            record,
            ReferenceBlock.PARAMETERS,
            ReferenceBlock.DATASTREAM_REFERENCE,
            StaEntities.DATASTREAM);
    String thingReference =
        ReferenceBlock.require(
            record,
            ReferenceBlock.PARAMETERS,
            ReferenceBlock.THING_REFERENCE,
            StaEntities.DATASTREAM);

    plan.parentLookup(
        StaEntities.DATASTREAM,
        PARENT,
        FrostUrls.datastreamLookup(projectId, datastreamReference, thingReference));

    // The request the writes wait for: the Datastream, or — when the measurement brings no
    // FeatureOfInterest — the position FROST derives one from. Without that check FROST refuses
    // the write with a 400 whose reason the batch drops, and a Thing whose Location the master
    // data has not written yet would look like a broken record instead of an early one.
    String gate = PARENT;
    if (!record.has(FEATURE_OF_INTEREST)) {
      plan.parentLookup(
          StaEntities.LOCATION,
          POSITION,
          FrostUrls.positionOfThing(SubRequest.reference(plan.id(PARENT))),
          SubRequest.ifResolved(plan.id(PARENT)),
          "the Thing of the Datastream has no Location yet, so FROST cannot generate the"
              + " FeatureOfInterest of the measurement");
      gate = POSITION;
    }

    Optional<String> ownReference =
        ReferenceBlock.read(record, ReferenceBlock.PARAMETERS, ReferenceBlock.REFERENCE);
    if (ownReference.isEmpty()) {
      plan.write(
          StaEntities.OBSERVATION,
          LOOKUP,
          BatchMethod.POST,
          "Observations",
          // The append is the only write, so it can carry the condition that the parent resolved.
          // The upsert below cannot: 'if' names one request, and there the condition has to be the
          // Observation lookup.
          SubRequest.ifResolved(plan.id(gate)),
          observation(record, plan),
          true);
      return;
    }

    plan.ownLookup(
        StaEntities.OBSERVATION,
        LOOKUP,
        FrostUrls.observationLookup(
            projectId, ownReference.get(), datastreamReference, thingReference),
        SubRequest.ifResolved(plan.id(gate)));

    plan.write(
        StaEntities.OBSERVATION,
        UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(LOOKUP)),
        SubRequest.ifResolved(plan.id(LOOKUP)),
        StaEntities.withoutNavigation(record),
        true);

    plan.write(
        StaEntities.OBSERVATION,
        LOOKUP,
        BatchMethod.POST,
        "Observations",
        SubRequest.ifMissing(plan.id(LOOKUP)),
        observation(record, plan),
        true);
  }

  /** The Observation as delivered, linked to the Datastream the lookup resolved. */
  private ObjectNode observation(ObjectNode record, RecordPlan plan) {
    ObjectNode body = record.deepCopy();
    StaEntities.link(body, StaEntities.DATASTREAM, SubRequest.reference(plan.id(PARENT)));
    return body;
  }
}
