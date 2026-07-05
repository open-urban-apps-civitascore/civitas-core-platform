/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineDeploymentRequestTest {

  @Test
  void postgisSinkWithoutTableNameIsRejectedAtConstruction() {
    // The invalid state must be unrepresentable: PutDatabaseRecord without a table has no target.
    assertThrows(IllegalArgumentException.class, () -> new PostgisSinkSpec(null));
    assertThrows(IllegalArgumentException.class, () -> new PostgisSinkSpec("  "));
  }

  @Test
  void postgisSinkWithTableNameIsAccepted() {
    assertEquals("sensor_observations", new PostgisSinkSpec("sensor_observations").tableName());
  }

  @Test
  void frostSinkCarriesItsProjectId() {
    FrostSinkSpec sink = new FrostSinkSpec("42");
    assertEquals(SinkType.FROST, sink.type());
    assertEquals("42", sink.projectId());
  }

  @Test
  void frostSinkRequiresANumericProjectId() {
    // The id scopes the flow to the dataset's FROST project and is interpolated into processor
    // URLs/$filters — missing or non-numeric values must be unrepresentable.
    assertThrows(IllegalArgumentException.class, () -> new FrostSinkSpec(null));
    assertThrows(IllegalArgumentException.class, () -> new FrostSinkSpec(" "));
    assertThrows(IllegalArgumentException.class, () -> new FrostSinkSpec("1) or true"));
  }

  @Test
  void primaryKeyColumnsAreTrimmedAndDeduplicated() {
    // names are joined verbatim into NiFi Update Keys → trim + de-duplicate (preserving order)
    PostgisSinkSpec sink = new PostgisSinkSpec("t", List.of(" tenant ", "id", "tenant"));
    assertEquals(List.of("tenant", "id"), sink.primaryKeyColumns());
  }

  @Test
  void blankPrimaryKeyColumnIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> new PostgisSinkSpec("t", List.of("id", "  ")));
  }

  @Test
  void blankPipelineIdIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PipelineDeploymentRequest(" ", Map.of(), new Datasource(), new FrostSinkSpec("1")));
  }

  @Test
  void graphDataIsDefensivelyCopiedAndUnmodifiable() {
    Map<String, Object> mutable = new HashMap<>();
    mutable.put("nodes", new ArrayList<>());

    PipelineDeploymentRequest request =
        new PipelineDeploymentRequest("p-1", mutable, new Datasource(), new FrostSinkSpec("1"));

    // mutating the caller's map after construction must not leak into the record
    mutable.put("edges", new ArrayList<>());
    assertFalse(request.graphData().containsKey("edges"), "record must not alias the caller's map");
    assertThrows(UnsupportedOperationException.class, () -> request.graphData().put("x", "y"));
  }

  @Test
  void nullGraphDataBecomesEmptyMap() {
    PipelineDeploymentRequest request =
        new PipelineDeploymentRequest("p-1", null, new Datasource(), new FrostSinkSpec("1"));
    assertTrue(request.graphData().isEmpty());
  }
}
