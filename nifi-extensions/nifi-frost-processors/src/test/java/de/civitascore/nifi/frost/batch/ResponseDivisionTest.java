/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.nifi.frost.port.SinkPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseDivisionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String THING = "{\"properties\":{\"reference\":\"A7\"}}";

  private static final String OBSERVATION =
      "{\"result\":1,\"parameters\":{\"thingReference\":\"A7\",\"datastreamReference\":\"temp\"}}";

  @Test
  void divide_whenOneGroupFails_writesTheOtherRecords() {
    BatchDocument document = document(SinkPort.THINGS, THING, THING, THING);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[{\"@iot.id\":7}]}"),
                answer("r0-thing-update", 200, null),
                answer("r1-thing", 200, "{\"value\":[]}"),
                answer("r1-thing", 400, "{\"message\":\"unit of measurement is malformed\"}"),
                answer("r2-thing", 200, "{\"value\":[]}"),
                answer("r2-thing", 201, "{\"@iot.id\":9}")));

    assertTrue(outcomes.get(0).successful());
    assertTrue(outcomes.get(2).successful());
    RecordOutcome failed = outcomes.get(1);
    assertFalse(failed.successful());
    assertEquals("Thing", failed.entity());
    assertEquals(400, failed.status());
    assertEquals("unit of measurement is malformed", failed.message());
  }

  @Test
  void divide_whenTheOwnReferenceMisses_createsTheEntity() {
    BatchDocument document = document(SinkPort.THINGS, THING);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[]}"),
                answer("r0-thing", 201, "{\"@iot.id\":9}")));

    // The update was skipped by its condition, and no answer for it is the usual condition of an
    // upsert rather than a lost write.
    assertTrue(outcomes.get(0).successful());
  }

  @Test
  void divide_whenAParentReferenceMisses_failsTheRecord() {
    BatchDocument document = document(SinkPort.OBSERVATIONS, OBSERVATION);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(document, response(answer("r0-ds", 200, "{\"value\":[]}")));

    RecordOutcome outcome = outcomes.get(0);
    assertFalse(outcome.successful());
    assertEquals("Datastream", outcome.entity());
    assertEquals(404, outcome.status());
    assertTrue(outcome.message().contains("Datastream"));
  }

  @Test
  void divide_whenNoWriteRan_failsTheRecord() {
    BatchDocument document = document(SinkPort.THINGS, THING);

    // The lookup answered, both halves of the upsert did not. Reporting the record as written here
    // would lose it without a trace.
    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document, response(answer("r0-thing", 200, "{\"value\":[{\"@iot.id\":7}]}")));

    RecordOutcome outcome = outcomes.get(0);
    assertFalse(outcome.successful());
    assertEquals("Thing", outcome.entity());
    assertEquals(0, outcome.status());
  }

  @Test
  void of_withAnAnswerThatIsNotABatchResponse_isRejected() {
    // A FROST server without the batch extension answers the same URL with an entity document.
    // Reading that as an empty response list would report every record as written.
    assertThrows(IllegalArgumentException.class, () -> BatchResponse.of(parse("{\"value\":[]}")));
  }

  private static BatchDocument document(SinkPort port, String... records) {
    List<RecordPlan> plans = new ArrayList<>(records.length);
    for (int index = 0; index < records.length; index++) {
      RecordPlan plan = new RecordPlan(index);
      port.planner().plan((ObjectNode) parse(records[index]), plan, "5");
      plans.add(plan);
    }
    return new BatchDocument(plans);
  }

  private static BatchResponse response(SubResponse... answers) {
    return new BatchResponse(List.of(answers));
  }

  private static SubResponse answer(String id, int status, String body) {
    return new SubResponse(id, status, body == null ? null : parse(body));
  }

  private static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
