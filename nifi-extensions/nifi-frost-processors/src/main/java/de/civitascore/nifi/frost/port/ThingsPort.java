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
 * Upserts a Thing by its own reference, inside the Dataset's FROST project. One record is one
 * Thing.
 */
public final class ThingsPort implements PortPlanner {

  private static final String LOOKUP = "thing";
  private static final String UPDATE = "thing-update";

  @Override
  public void plan(ObjectNode record, RecordPlan plan, String projectId) {
    String reference =
        ReferenceBlock.require(
            record, ReferenceBlock.PROPERTIES, ReferenceBlock.REFERENCE, StaEntities.THING);

    plan.ownLookup(StaEntities.THING, LOOKUP, FrostUrls.thingLookup(projectId, reference));

    // The update runs before the create although it is the second case, because both share the
    // lookup identifier: after a successful create the identifier resolves again, and an update
    // placed behind it would patch the entity the create just wrote.
    plan.write(
        StaEntities.THING,
        UPDATE,
        BatchMethod.PATCH,
        SubRequest.reference(plan.id(LOOKUP)),
        SubRequest.ifResolved(plan.id(LOOKUP)),
        // SensorThings rejects a navigation member in a PATCH, so the update carries the Thing's
        // own fields only. The create keeps the record as it arrived, which leaves a deep insert
        // the source delivers intact.
        StaEntities.withoutNavigation(record),
        true);

    plan.write(
        StaEntities.THING,
        LOOKUP,
        BatchMethod.POST,
        FrostUrls.things(projectId),
        SubRequest.ifMissing(plan.id(LOOKUP)),
        record.deepCopy(),
        true);
  }
}
