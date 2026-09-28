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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    // FROST refuses the record as it is; sending it again gives the same answer.
    assertFalse(failed.retryable());
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
  void divide_whenAParentReferenceMisses_retriesTheRecord() {
    BatchDocument document = document(SinkPort.OBSERVATIONS, OBSERVATION);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(document, response(answer("r0-ds", 200, "{\"value\":[]}")));

    RecordOutcome outcome = outcomes.get(0);
    assertFalse(outcome.successful());
    assertEquals("Datastream", outcome.entity());
    assertEquals(404, outcome.status());
    assertTrue(outcome.message().contains("Datastream"));
    // The Pipeline that writes the Datastream may not have run yet.
    assertTrue(outcome.retryable());
  }

  @Test
  void divide_whenTheThingHasNoPositionYet_retriesWithTheReason() {
    BatchDocument document = document(SinkPort.OBSERVATIONS, OBSERVATION);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-ds", 200, "{\"value\":[{\"@iot.id\":19}]}"),
                answer("r0-ds-loc", 200, "{\"value\":[]}")));

    // Without a Location FROST would refuse the write with a 400 and no reason. The check before
    // it names the reason, and the master data may still bring the Location.
    RecordOutcome outcome = outcomes.get(0);
    assertFalse(outcome.successful());
    assertTrue(outcome.retryable());
    assertEquals("Location", outcome.entity());
    assertTrue(outcome.message().contains("FeatureOfInterest"), outcome.message());
  }

  @Test
  void divide_whenASubRequestHitsAServerError_retriesTheRecord() {
    BatchDocument document = document(SinkPort.THINGS, THING);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[]}"),
                answer("r0-thing", 503, "{\"message\":\"database is starting\"}")));

    // A server error says nothing about the record.
    RecordOutcome outcome = outcomes.get(0);
    assertTrue(outcome.retryable());
    assertEquals(503, outcome.status());
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429})
  void divide_whenASubRequestTimesOutOrIsThrottled_retriesTheRecord(int status) {
    BatchDocument document = document(SinkPort.THINGS, THING);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[]}"),
                answer("r0-thing", status, "{\"message\":\"try again later\"}")));

    // The batch answer retries these two already; one sub-request with them is no different.
    RecordOutcome outcome = outcomes.get(0);
    assertTrue(outcome.retryable());
    assertEquals(status, outcome.status());
  }

  @Test
  void divide_whenTheDatastreamMisses_reportsItBeforeThePosition() {
    BatchDocument document = document(SinkPort.OBSERVATIONS, OBSERVATION);

    // The position check waits for the Datastream; skipped, it must not hide the real reason.
    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-ds", 200, "{\"value\":[]}"),
                answer("r0-ds-loc", 200, "\"Skipped due to if.\"")));

    assertEquals("Datastream", outcomes.get(0).entity());
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
  void divide_whenBothHalvesOfTheUpsertWereSkipped_failsTheRecord() {
    BatchDocument document = document(SinkPort.THINGS, THING);

    // FROST answers a request whose condition did not hold with a success and says so in the
    // body. Counting that as a write would report a record nothing wrote as written.
    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[{\"@iot.id\":7}]}"),
                answer("r0-thing-update", 200, "\"Skipped due to if.\""),
                answer("r0-thing", 200, "\"Skipped due to if.\"")));

    RecordOutcome outcome = outcomes.get(0);
    assertFalse(outcome.successful());
    assertEquals("Thing", outcome.entity());
    assertEquals(0, outcome.status());
  }

  @Test
  void divide_whenTheUpdateRanAndTheCreateWasSkipped_writesTheRecord() {
    BatchDocument document = document(SinkPort.THINGS, THING);

    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[{\"@iot.id\":7}]}"),
                answer("r0-thing-update", 200, null),
                answer("r0-thing", 200, "\"Skipped due to if.\"")));

    assertTrue(outcomes.get(0).successful());
  }

  @Test
  void divide_whenTheGroupWasSkipped_reportsTheFailureThatStoppedIt() {
    BatchDocument document = document(SinkPort.THINGS, THING, THING);

    // FROST answers the requests it skips after a failure inside an atomicity group without an
    // identifier. The failure that stopped the group is the answer before them, and it carries
    // the reason.
    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document,
            response(
                answer("r0-thing", 200, "{\"value\":[]}"),
                answer("r0-thing", 400, "{\"message\":\"name is required\"}"),
                answer(null, 400, "\"Skipped due to previous failure in atomicityGroup.\""),
                answer("r1-thing", 200, "{\"value\":[]}"),
                answer("r1-thing", 201, "{\"@iot.id\":9}")));

    RecordOutcome failed = outcomes.get(0);
    assertFalse(failed.successful());
    assertEquals(400, failed.status());
    assertEquals("name is required", failed.message());
    assertTrue(outcomes.get(1).successful());
  }

  @Test
  void divide_whenFrostCouldNotReadTheDocument_failsEveryRecordWithItsReason() {
    BatchDocument document = document(SinkPort.THINGS, THING, THING);

    // The one answer to a document FROST cannot read names no request either. Reading it as an
    // answer to nothing would report both records as unwritten without the reason.
    Map<Integer, RecordOutcome> outcomes =
        ResponseDivision.divide(
            document, response(answer(null, 400, "\"Failed to parse json: unknown field\"")));

    for (int index = 0; index < 2; index++) {
      RecordOutcome outcome = outcomes.get(index);
      assertFalse(outcome.successful());
      assertEquals(400, outcome.status());
      assertEquals("Failed to parse json: unknown field", outcome.message());
    }
  }

  @Test
  void of_withAnAnswerWithoutAnIdentifier_keepsTheOtherAnswers() {
    BatchResponse response =
        BatchResponse.of(
            parse(
                "{\"responses\":[{\"id\":\"r0-thing\",\"status\":201},"
                    + "{\"status\":400,\"body\":\"Skipped due to previous failure in"
                    + " atomicityGroup.\"}]}"));

    assertEquals(2, response.responses().size());
    assertTrue(response.responses().get(0).identified());
    assertFalse(response.responses().get(1).identified());
    assertEquals(400, response.responses().get(1).status());
  }

  @Test
  void of_withAnAnswerWithoutAStatus_isRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BatchResponse.of(parse("{\"responses\":[{\"id\":\"r0-thing\"}]}")));
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
