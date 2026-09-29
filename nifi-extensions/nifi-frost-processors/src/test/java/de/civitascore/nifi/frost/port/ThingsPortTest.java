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

class ThingsPortTest {

  private static final String THING =
      """
      {
        "name": "Station A7",
        "description": "Weather station",
        "properties": {
          "reference": "A7",
          "referenceName": "stationsnummer"
        }
      }
      """;

  @Test
  void plan_withAThing_buildsTheUpsertOfOneThing() {
    BatchDocuments.assertMatches("things-port.json", BatchDocuments.of(SinkPort.THINGS, THING));
  }

  @Test
  void plan_withAThing_addressesTheUpdateByTheBackReferenceAlone() {
    JsonNode requests = BatchDocuments.of(SinkPort.THINGS, THING).get("requests");
    String url = requests.get(1).get("url").asText();

    // FROST reads a back-reference in a URL at the start only, where it becomes the self link of
    // the entity. Written inside a path segment — Things($r0-thing) — it stays as it is, and the
    // update goes to an entity whose identifier is the text of the reference: FROST answers 404.
    assertEquals("$r0-thing", url);
  }

  @Test
  void plan_withTwoRecords_keepsTheGroupsApart() {
    JsonNode document = BatchDocuments.of(SinkPort.THINGS, THING, THING);
    JsonNode requests = document.get("requests");

    assertEquals(6, requests.size());
    assertTrue(
        requests.findValuesAsText("atomicityGroup").containsAll(java.util.List.of("r0", "r1")));
    // A back-reference resolves to the latest request carrying the identifier, so two records
    // sharing one would write each other's entities.
    assertEquals("r0-thing", requests.get(0).get("id").asText());
    assertEquals("r1-thing", requests.get(3).get("id").asText());
  }

  @Test
  void plan_withoutAReference_rejectsTheRecord() {
    RecordRejectedException rejected =
        assertThrows(
            RecordRejectedException.class,
            () ->
                SinkPort.THINGS
                    .planner()
                    .plan(
                        BatchDocuments.parse("{\"name\":\"no key\"}"),
                        new RecordPlan(0),
                        BatchDocuments.PROJECT_ID));

    assertEquals("Thing", rejected.entity());
    assertTrue(rejected.getMessage().contains("properties/reference"));
  }

  @Test
  void plan_withAQuoteInTheReference_keepsTheFilterIntact() {
    JsonNode document =
        BatchDocuments.of(
            SinkPort.THINGS, "{\"properties\":{\"reference\":\"A7' or name eq 'x\"}}");
    String url = document.get("requests").get(0).get("url").asText();

    // The quote is doubled, which is how OData escapes it, and nothing else is escaped: the URL of
    // a batch sub-request reaches the query parser of FROST undecoded. A reference cannot end the
    // literal and start a new term.
    assertTrue(url.endsWith(" eq 'A7'' or name eq ''x'"), url);
  }
}
