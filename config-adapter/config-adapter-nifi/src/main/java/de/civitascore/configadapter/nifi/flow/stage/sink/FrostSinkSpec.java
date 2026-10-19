/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.SinkPort;

/**
 * A FROST sink's resolved configuration.
 *
 * @param projectId the dataset's FROST project id (from the saga's create-project step); scopes the
 *     find-or-create flow to the dataset's project
 * @param port the write logic the modeller selected. Required: without it the platform would have
 *     to derive from the Mapping what the Pipeline writes, which is the condition this field
 *     replaces.
 * @param staProperties the {@code properties} bag of the mapping's Thing-shaped target structure
 *     per entity (its attributes and match key), or {@code null} when the datasink carries no
 *     target structure — valid for a passthrough flow (the source delivers the STA envelope
 *     itself), rejected when a mapping is compiled against this sink
 */
public record FrostSinkSpec(String projectId, SinkPort port, StaProperties staProperties)
    implements SinkSpec {

  public FrostSinkSpec {
    if (port == null) {
      throw new IllegalArgumentException(
          "the FROST sink node carries no port; select one of: " + SinkPort.labels());
    }
    // The project id is interpolated into NiFi processor URLs and $filter expressions, so it must
    // be the numeric id FROST's create-project step returned — anything else is a mis-wired
    // payload (or an injection attempt). The numeric check assumes FROST-Server's default LONG
    // entity-id type; revisit it before ever operating FROST with string ids. Without the id a
    // FROST flow would post to the server root, invisible to the dataset's project-scoped named
    // API.
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalArgumentException("a FROST sink requires a non-blank projectId");
    }
    if (!projectId.matches("\\d+")) {
      throw new IllegalArgumentException("frostProjectId must be numeric: " + projectId);
    }
  }

  @Override
  public SinkType type() {
    return SinkType.FROST;
  }
}
