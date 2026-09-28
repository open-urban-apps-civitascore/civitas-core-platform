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

/**
 * Writes a Thing with its Location, its Datastream and one measurement: the chain the mapped flow
 * builds today, as one atomicity group.
 *
 * <p>Each entity of the tree is an upsert on its own reference. The nested entities carry {@code
 * thingReference} into their own reference block, so that a later Pipeline — an Observations port
 * on the same data — resolves them with a direct query and does not depend on this tree.
 *
 * <p>The Sensor and the ObservedProperty are an exception: they have no reference of their own in
 * this stage. The create deep-inserts them with the Datastream, and the update resolves them
 * through the Datastream's navigation and patches them by identifier. That is the behaviour of the
 * generated graph this port replaces.
 */
public final class ThingTreePort implements PortPlanner {

  private static final String THING = "thing";
  private static final String THING_UPDATE = "thing-update";
  private static final String LOCATION = "loc";
  private static final String LOCATION_UPDATE = "loc-update";
  private static final String DATASTREAM = "ds";
  private static final String DATASTREAM_UPDATE = "ds-update";
  private static final String SENSOR = "ds-sensor";
  private static final String SENSOR_UPDATE = "ds-sensor-update";
  private static final String OBSERVED_PROPERTY = "ds-op";
  private static final String OBSERVED_PROPERTY_UPDATE = "ds-op-update";
  private static final String OBSERVATION = "obs";

  @Override
  public void plan(ObjectNode record, RecordPlan plan, String projectId) {
    String thingReference =
        ReferenceBlock.require(
            record, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, StaEntities.THING);

    planThing(record, plan, projectId, thingReference);

    ObjectNode location = StaEntities.firstOf(record, "Locations", StaEntities.LOCATION);
    if (location != null) {
      planLocation(location, plan, thingReference);
    }

    ObjectNode datastream = StaEntities.firstOf(record, "Datastreams", StaEntities.DATASTREAM);
    if (datastream == null) {
      return;
    }
    planDatastream(datastream, plan, projectId, thingReference);

    ObjectNode observation =
        StaEntities.firstOf(datastream, "Observations", StaEntities.OBSERVATION);
    if (observation != null) {
      planObservation(observation, plan);
    }
  }

  private void planThing(
      ObjectNode record, RecordPlan plan, String projectId, String thingReference) {
    plan.ownLookup(StaEntities.THING, THING, FrostUrls.thingLookup(projectId, thingReference));
    // The tree writes every nested entity itself, so the Thing carries its own fields only. A deep
    // insert here would create a second Location beside the one the Location step upserts.
    ObjectNode body = StaEntities.withoutNavigation(record);
    plan.write(
        StaEntities.THING,
        THING_UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(THING)),
        SubRequest.ifResolved(plan.id(THING)),
        body,
        true);
    plan.write(
        StaEntities.THING,
        THING,
        BatchMethod.POST,
        FrostUrls.things(projectId),
        SubRequest.ifMissing(plan.id(THING)),
        body.deepCopy(),
        true);
  }

  private void planLocation(ObjectNode location, RecordPlan plan, String thingReference) {
    // A Thing has one current Location, and the mapped shape carries one. Without a reference of
    // its own the Location takes the Thing's. The lookup runs through the Thing just resolved or
    // created — the Location collection belongs to no project, so a direct query would find the
    // Location another Dataset wrote for the same device.
    String reference =
        ReferenceBlock.read(location, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE)
            .orElse(thingReference);
    ObjectNode body = StaEntities.withoutNavigation(location);
    ReferenceBlock.write(body, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, reference);
    ReferenceBlock.write(
        body, ReferenceBlock.PROPERTIES, ReferenceBlock.THING_REFERENCE, thingReference);

    plan.ownLookup(
        StaEntities.LOCATION,
        LOCATION,
        FrostUrls.locationLookup(SubRequest.reference(plan.id(THING)), reference));
    plan.write(
        StaEntities.LOCATION,
        LOCATION_UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(LOCATION)),
        SubRequest.ifResolved(plan.id(LOCATION)),
        body,
        true);

    ObjectNode created = body.deepCopy();
    StaEntities.linkMany(created, "Things", SubRequest.reference(plan.id(THING)));
    plan.write(
        StaEntities.LOCATION,
        LOCATION,
        BatchMethod.POST,
        "Locations",
        SubRequest.ifMissing(plan.id(LOCATION)),
        created,
        true);
  }

  private void planDatastream(
      ObjectNode datastream, RecordPlan plan, String projectId, String thingReference) {
    String reference =
        ReferenceBlock.require(
            datastream,
            ReferenceBlock.PROPERTIES,
            ReferenceBlock.REFERENCE,
            StaEntities.DATASTREAM);
    ObjectNode body = StaEntities.withoutNavigation(datastream);
    ReferenceBlock.write(body, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, reference);
    ReferenceBlock.write(
        body, ReferenceBlock.PROPERTIES, ReferenceBlock.THING_REFERENCE, thingReference);

    plan.ownLookup(
        StaEntities.DATASTREAM,
        DATASTREAM,
        FrostUrls.datastreamLookup(projectId, reference, thingReference));
    plan.write(
        StaEntities.DATASTREAM,
        DATASTREAM_UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(DATASTREAM)),
        SubRequest.ifResolved(plan.id(DATASTREAM)),
        body,
        true);

    planNestedUpdate(datastream, plan, StaEntities.SENSOR, SENSOR, SENSOR_UPDATE);
    planNestedUpdate(
        datastream,
        plan,
        StaEntities.OBSERVED_PROPERTY,
        OBSERVED_PROPERTY,
        OBSERVED_PROPERTY_UPDATE);

    // The create stands last because it shares the lookup identifier: every request that must see
    // the lookup result alone has to run before it.
    ObjectNode created = body.deepCopy();
    StaEntities.link(created, StaEntities.THING, SubRequest.reference(plan.id(THING)));
    copyNested(datastream, created, StaEntities.SENSOR);
    copyNested(datastream, created, StaEntities.OBSERVED_PROPERTY);
    plan.write(
        StaEntities.DATASTREAM,
        DATASTREAM,
        BatchMethod.POST,
        "Datastreams",
        SubRequest.ifMissing(plan.id(DATASTREAM)),
        created,
        true);
  }

  /**
   * Resolves a single-valued navigation of an existing Datastream and patches the entity behind it.
   * Runs only when the Datastream was found; on the other branch the create deep-inserts the same
   * entity, which is why neither write is required for the record to count as written.
   */
  private void planNestedUpdate(
      ObjectNode datastream,
      RecordPlan plan,
      String navigation,
      String lookupSuffix,
      String updateSuffix) {
    if (!(datastream.get(navigation) instanceof ObjectNode nested)) {
      return;
    }
    plan.ownLookup(
        navigation,
        lookupSuffix,
        FrostUrls.navigation(SubRequest.reference(plan.id(DATASTREAM)), navigation),
        SubRequest.ifResolved(plan.id(DATASTREAM)));
    plan.write(
        navigation,
        updateSuffix,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(lookupSuffix)),
        SubRequest.ifResolved(plan.id(lookupSuffix)),
        StaEntities.withoutNavigation(nested),
        false);
  }

  private void copyNested(ObjectNode datastream, ObjectNode target, String navigation) {
    if (datastream.get(navigation) instanceof ObjectNode nested) {
      target.set(navigation, nested.deepCopy());
    }
  }

  private void planObservation(ObjectNode observation, RecordPlan plan) {
    // The tree appends: the mapped flow it replaces posts every measurement, and a measurement of
    // the tree carries no reference of its own.
    ObjectNode body = observation.deepCopy();
    StaEntities.link(body, StaEntities.DATASTREAM, SubRequest.reference(plan.id(DATASTREAM)));
    plan.write(
        StaEntities.OBSERVATION, OBSERVATION, BatchMethod.POST, "Observations", null, body, true);
  }
}
