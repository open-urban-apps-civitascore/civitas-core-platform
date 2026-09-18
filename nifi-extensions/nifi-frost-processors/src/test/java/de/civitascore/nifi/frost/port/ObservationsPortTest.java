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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.nifi.frost.batch.RecordPlan;
import org.junit.jupiter.api.Test;

class ObservationsPortTest {

  private static final String APPENDED =
      """
      {
        "phenomenonTime": "2026-09-18T07:00:00Z",
        "result": 21.5,
        "parameters": {
          "thingReference": "A7",
          "datastreamReference": "temp"
        }
      }
      """;

  private static final String UPSERTED =
      """
      {
        "phenomenonTime": "2026-09-18T07:00:00Z",
        "result": 21.5,
        "parameters": {
          "reference": "m-1",
          "thingReference": "A7",
          "datastreamReference": "temp"
        }
      }
      """;

  @Test
  void plan_withoutAnOwnReference_appendsTheMeasurement() {
    BatchDocuments.assertMatches(
        "observations-append-port.json", BatchDocuments.of(SinkPort.OBSERVATIONS, APPENDED));
  }

  @Test
  void plan_withAnOwnReference_upsertsTheMeasurement() {
    BatchDocuments.assertMatches(
        "observations-upsert-port.json", BatchDocuments.of(SinkPort.OBSERVATIONS, UPSERTED));
  }

  @Test
  void plan_withoutAnOwnReference_writesOneMeasurementForEveryDelivery() {
    // Two deliveries of the same message are two records, and each one appends. The port asks for
    // no measurement, so nothing can match and nothing is updated.
    JsonNode requests =
        BatchDocuments.of(SinkPort.OBSERVATIONS, APPENDED, APPENDED).get("requests");

    assertEquals(4, requests.size());
    assertEquals(2, count(requests, "post"));
    assertEquals(0, count(requests, "patch"));
  }

  @Test
  void plan_withAnOwnReference_asksForTheMeasurementBeforeItWrites() {
    // The second delivery finds the measurement of the first and updates it, so two deliveries
    // leave one measurement. The lookup that decides this is in the batch itself.
    JsonNode requests = BatchDocuments.of(SinkPort.OBSERVATIONS, UPSERTED).get("requests");

    assertEquals(2, count(requests, "get"));
    assertEquals("$r0-obs", requests.get(2).get("if").asText());
    assertEquals("not $r0-obs", requests.get(3).get("if").asText());
  }

  @Test
  void plan_withoutTheParentReferences_rejectsTheRecord() {
    RecordRejectedException rejected =
        assertThrows(
            RecordRejectedException.class,
            () ->
                SinkPort.OBSERVATIONS
                    .planner()
                    .plan(
                        BatchDocuments.parse("{\"result\":1}"),
                        new RecordPlan(0),
                        BatchDocuments.PROJECT_ID));

    assertEquals("Datastream", rejected.entity());
    assertTrue(rejected.getMessage().contains("datastreamReference"));
  }

  private static long count(JsonNode requests, String method) {
    return requests.findValuesAsText("method").stream().filter(method::equals).count();
  }
}
