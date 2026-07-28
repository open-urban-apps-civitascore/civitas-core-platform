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

import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.List;

/**
 * The compiled output of one mapping node — the unit the flow builder turns into that node's own
 * {@code UpdateRecord} processor chain. A pipeline may chain several mapping nodes; each keeps its
 * compiled properties separate so the processors materialize per node, in flow order.
 *
 * @param properties the node's {@code UpdateRecord} properties, in mapping order
 * @param fork the array fan-out this node needs ahead of its own properties; the properties are
 *     already compiled against the post-fork record shape
 */
public record CompiledMapping(List<UpdateRecordProperty> properties, ForkPlan fork)
    implements CompiledTransform {
  public CompiledMapping {
    properties = List.copyOf(properties);
  }
}
