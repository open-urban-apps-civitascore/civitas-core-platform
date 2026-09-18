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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.nifi.frost.batch.BatchDocument;
import de.civitascore.nifi.frost.batch.RecordPlan;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Builds a batch document from records and compares it with the document a port must produce. */
final class BatchDocuments {

  static final String PROJECT_ID = "5";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BatchDocuments() {}

  /** The document a port builds for the given records, each record its own atomicity group. */
  static JsonNode of(SinkPort port, String... records) {
    PortPlanner planner = port.planner();
    List<RecordPlan> plans = new ArrayList<>(records.length);
    for (int index = 0; index < records.length; index++) {
      RecordPlan plan = new RecordPlan(index);
      planner.plan(parse(records[index]), plan, PROJECT_ID);
      plans.add(plan);
    }
    return new BatchDocument(plans).toJson(MAPPER);
  }

  static ObjectNode parse(String json) {
    try {
      return (ObjectNode) MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Compares the document with the expected one, which lives beside the test as a resource. */
  static void assertMatches(String resource, JsonNode actual) {
    assertEquals(pretty(expected(resource)), pretty(actual));
  }

  private static JsonNode expected(String resource) {
    try (InputStream content = BatchDocuments.class.getResourceAsStream("/batch/" + resource)) {
      if (content == null) {
        throw new IllegalStateException("no expected document at /batch/" + resource);
      }
      return MAPPER.readTree(content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Renders both sides the same way, so that a difference reads as a difference in content. */
  private static String pretty(JsonNode node) {
    return node.toPrettyString();
  }
}
