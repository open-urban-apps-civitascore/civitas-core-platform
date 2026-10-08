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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Writes a Thing with its Locations, its Datastreams and their measurements: the chain the mapped
 * flow builds today, as one atomicity group.
 *
 * <p>Each entity of the tree is an upsert on its own reference. The nested entities carry {@code
 * thingReference} into their own reference block, so that a later Pipeline — an Observations port
 * on the same data — resolves them with a direct query and does not depend on this tree.
 *
 * <p>The Sensor and the ObservedProperty are an exception: they have no reference of their own in
 * this stage. The create deep-inserts them with the Datastream, and the update resolves them
 * through the Datastream's navigation and patches them by identifier. That is the behaviour of the
 * generated graph this port replaces.
 *
 * <p>Every member of a collection gets sub-requests of its own. The first member keeps the plain
 * identifier ({@code ds}), so a tree with one member per collection builds the same document as
 * before; a further member carries its position ({@code ds1}, {@code ds1-sensor}, {@code ds1-obs}).
 * A member of a collection with more than one also carries its position in the entity it reports
 * ({@code Datastream[1]}), which is what tells the response division that each of them must see a
 * write of its own.
 */
public final class ThingTreePort implements PortPlanner {

  private static final String THING = "thing";
  private static final String THING_UPDATE = "thing-update";
  private static final String LOCATION = "loc";
  private static final String DATASTREAM = "ds";
  private static final String SENSOR = "-sensor";
  private static final String OBSERVED_PROPERTY = "-op";
  private static final String OBSERVATION = "obs";
  private static final String UPDATE = "-update";

  @Override
  public void plan(ObjectNode record, RecordPlan plan, String projectId) {
    String thingReference =
        ReferenceBlock.require(
            record, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, StaEntities.THING);

    planThing(record, plan, projectId, thingReference);

    List<ObjectNode> locations = StaEntities.membersOf(record, "Locations", StaEntities.LOCATION);
    Set<String> locationReferences = new HashSet<>();
    for (int index = 0; index < locations.size(); index++) {
      ObjectNode location = locations.get(index);
      String entity = label(StaEntities.LOCATION, index, locations.size());
      String reference = locationReference(location, entity, locations.size(), thingReference);
      requireUnique(locationReferences, reference, entity);
      planLocation(
          location,
          reference,
          positioned(LOCATION, index),
          entity,
          plan,
          projectId,
          thingReference);
    }

    List<ObjectNode> datastreams =
        StaEntities.membersOf(record, "Datastreams", StaEntities.DATASTREAM);
    Set<String> datastreamReferences = new HashSet<>();
    for (int index = 0; index < datastreams.size(); index++) {
      ObjectNode datastream = datastreams.get(index);
      String entity = label(StaEntities.DATASTREAM, index, datastreams.size());
      String reference =
          ReferenceBlock.require(
              datastream, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, entity);
      requireUnique(datastreamReferences, reference, entity);
      boolean several = datastreams.size() > 1;
      String datastreamId = positioned(DATASTREAM, index);
      planDatastream(
          datastream, reference, datastreamId, entity, several, plan, projectId, thingReference);

      String observationEntity = below(entity, StaEntities.OBSERVATION, several);
      List<ObjectNode> observations =
          StaEntities.membersOf(datastream, "Observations", observationEntity);
      // The first Datastream keeps the plain identifier for its measurements as well.
      String observationBase = index == 0 ? OBSERVATION : datastreamId + "-" + OBSERVATION;
      for (int position = 0; position < observations.size(); position++) {
        planObservation(
            observations.get(position),
            positioned(observationBase, position),
            below(entity, label(StaEntities.OBSERVATION, position, observations.size()), several),
            datastreamId,
            plan,
            projectId);
      }
    }
  }

  /**
   * The upsert key of a Location. A Thing has one current Location, and the mapped shape carries
   * one: without a reference of its own it takes the Thing's. Several Locations cannot share that
   * fallback — each would upsert onto the one before it — so then each must name its own.
   */
  private static String locationReference(
      ObjectNode location, String entity, int count, String thingReference) {
    Optional<String> own =
        ReferenceBlock.read(location, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE);
    if (own.isPresent()) {
      return own.get();
    }
    if (count > 1) {
      throw new RecordRejectedException(
          entity,
          "the record carries "
              + count
              + " Locations, so each needs a "
              + ReferenceBlock.PROPERTIES
              + "/"
              + ReferenceBlock.REFERENCE
              + " of its own");
    }
    return thingReference;
  }

  /**
   * Rejects a second member with the same reference. Its lookup would find the entity the first
   * member just wrote and patch it, so the record would lose one member without a trace.
   */
  private static void requireUnique(Set<String> seen, String reference, String entity) {
    if (!seen.add(reference)) {
      throw new RecordRejectedException(
          entity, "the reference " + reference + " stands twice in the record");
    }
  }

  /** The identifier suffix of a collection member: plain for the first, positioned after it. */
  private static String positioned(String base, int index) {
    return index == 0 ? base : base + index;
  }

  /** The entity a member reports: its position only where the collection has more than one. */
  private static String label(String entity, int index, int count) {
    return count > 1 ? entity + "[" + index + "]" : entity;
  }

  /**
   * The entity a nested member reports, prefixed with its parent where the parent is positioned.
   */
  private static String below(String parent, String child, boolean parentPositioned) {
    return parentPositioned ? parent + "/" + child : child;
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

  private void planLocation(
      ObjectNode location,
      String reference,
      String locationId,
      String entity,
      RecordPlan plan,
      String projectId,
      String thingReference) {
    ObjectNode body = StaEntities.withoutNavigation(location);
    ReferenceBlock.write(body, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, reference);
    ReferenceBlock.write(
        body, ReferenceBlock.PROPERTIES, ReferenceBlock.THING_REFERENCE, thingReference);
    StaEntities.assignToProject(body, projectId);

    // The lookup runs through the Thing just resolved or created — a direct query on the Location
    // collection would find the Location another Dataset wrote for the same device, and one on the
    // project's Locations would miss a Location written before the port assigned it.
    plan.ownLookup(
        entity,
        locationId,
        FrostUrls.locationLookup(SubRequest.reference(plan.id(THING)), reference));
    plan.write(
        entity,
        locationId + UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(locationId)),
        SubRequest.ifResolved(plan.id(locationId)),
        body,
        true);

    ObjectNode created = body.deepCopy();
    StaEntities.linkMany(created, "Things", SubRequest.reference(plan.id(THING)));
    plan.write(
        entity,
        locationId,
        BatchMethod.POST,
        "Locations",
        SubRequest.ifMissing(plan.id(locationId)),
        created,
        true);
  }

  private void planDatastream(
      ObjectNode datastream,
      String reference,
      String datastreamId,
      String entity,
      boolean several,
      RecordPlan plan,
      String projectId,
      String thingReference) {
    ObjectNode body = StaEntities.withoutNavigation(datastream);
    ReferenceBlock.write(body, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, reference);
    ReferenceBlock.write(
        body, ReferenceBlock.PROPERTIES, ReferenceBlock.THING_REFERENCE, thingReference);

    plan.ownLookup(
        entity, datastreamId, FrostUrls.datastreamLookup(projectId, reference, thingReference));
    plan.write(
        entity,
        datastreamId + UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(datastreamId)),
        SubRequest.ifResolved(plan.id(datastreamId)),
        body,
        true);

    planNestedUpdate(
        datastream,
        datastreamId,
        below(entity, StaEntities.SENSOR, several),
        StaEntities.SENSOR,
        datastreamId + SENSOR,
        plan,
        projectId);
    // An ObservedProperty belongs to no project: the Projects plugin has no navigation to it.
    planNestedUpdate(
        datastream,
        datastreamId,
        below(entity, StaEntities.OBSERVED_PROPERTY, several),
        StaEntities.OBSERVED_PROPERTY,
        datastreamId + OBSERVED_PROPERTY,
        plan,
        null);

    // The create stands last because it shares the lookup identifier: every request that must see
    // the lookup result alone has to run before it.
    ObjectNode created = body.deepCopy();
    StaEntities.link(created, StaEntities.THING, SubRequest.reference(plan.id(THING)));
    copyNested(datastream, created, StaEntities.SENSOR);
    copyNested(datastream, created, StaEntities.OBSERVED_PROPERTY);
    StaEntities.assignNestedToProject(created, projectId);
    plan.write(
        entity,
        datastreamId,
        BatchMethod.POST,
        "Datastreams",
        SubRequest.ifMissing(plan.id(datastreamId)),
        created,
        true);
  }

  /**
   * Resolves a single-valued navigation of an existing Datastream and patches the entity behind it.
   * Runs only when the Datastream was found; on the other branch the create deep-inserts the same
   * entity, which is why neither write is required for the record to count as written.
   *
   * <p>With a project the update also assigns the entity to it, null for an entity no project can
   * hold.
   */
  private void planNestedUpdate(
      ObjectNode datastream,
      String datastreamId,
      String entity,
      String navigation,
      String lookupId,
      RecordPlan plan,
      String projectId) {
    if (!(datastream.get(navigation) instanceof ObjectNode nested)) {
      return;
    }
    ObjectNode body = StaEntities.withoutNavigation(nested);
    if (projectId != null) {
      StaEntities.assignToProject(body, projectId);
    }
    plan.ownLookup(
        entity,
        lookupId,
        FrostUrls.navigation(SubRequest.reference(plan.id(datastreamId)), navigation),
        SubRequest.ifResolved(plan.id(datastreamId)));
    plan.write(
        entity,
        lookupId + UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(lookupId)),
        SubRequest.ifResolved(plan.id(lookupId)),
        body,
        false);
  }

  private void copyNested(ObjectNode datastream, ObjectNode target, String navigation) {
    if (datastream.get(navigation) instanceof ObjectNode nested) {
      target.set(navigation, nested.deepCopy());
    }
  }

  private void planObservation(
      ObjectNode observation,
      String observationId,
      String entity,
      String datastreamId,
      RecordPlan plan,
      String projectId) {
    // The tree appends: the mapped flow it replaces posts every measurement, and a measurement of
    // the tree carries no reference of its own.
    ObjectNode body = observation.deepCopy();
    StaEntities.assignNestedToProject(body, projectId);
    StaEntities.link(body, StaEntities.DATASTREAM, SubRequest.reference(plan.id(datastreamId)));
    plan.write(entity, observationId, BatchMethod.POST, "Observations", null, body, true);
  }
}
