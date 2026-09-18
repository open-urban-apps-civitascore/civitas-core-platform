/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.web.client.provider.service.StandardWebClientServiceProvider;
import org.junit.jupiter.api.Test;

class PutFrostRecordTest {

  private static final String WEB_CLIENT = "web-client";

  private static final String THING_A = "{\"name\":\"A7\",\"properties\":{\"reference\":\"A7\"}}";
  private static final String THING_B = "{\"name\":\"B2\",\"properties\":{\"reference\":\"B2\"}}";

  @Test
  void validate_withoutAPort_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080/v1.1");
    runner.removeProperty(PutFrostRecord.PORT);

    // A sink without a port is not configured. There is no default to fall back to, because the
    // platform must not decide which entities a Pipeline writes.
    runner.assertNotValid();
  }

  @Test
  void validate_withHalfACredential_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080/v1.1");
    runner.setProperty(PutFrostRecord.USERNAME, "frost");

    runner.assertNotValid();
  }

  @Test
  void onTrigger_withOneRecord_routesItToSuccess() throws InitializationException {
    try (FrostEndpoint endpoint =
        FrostEndpoint.answering(
            document ->
                answers(
                    "{\"id\":\"r0-thing\",\"status\":200,\"body\":{\"value\":[]}}",
                    "{\"id\":\"r0-thing\",\"status\":201,\"body\":{\"@iot.id\":9}}"),
            200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
      runner.assertTransferCount(PutFrostRecord.FAILURE, 0);
      assertEquals(1, endpoint.documents().size());
      assertNull(endpoint.authorizations().get(0));
    }
  }

  @Test
  void onTrigger_whenOneGroupFails_routesOnlyThatRecordToFailure() throws InitializationException {
    try (FrostEndpoint endpoint =
        FrostEndpoint.answering(
            document ->
                answers(
                    "{\"id\":\"r0-thing\",\"status\":200,\"body\":{\"value\":[]}}",
                    "{\"id\":\"r0-thing\",\"status\":201,\"body\":{\"@iot.id\":9}}",
                    "{\"id\":\"r1-thing\",\"status\":200,\"body\":{\"value\":[]}}",
                    "{\"id\":\"r1-thing\",\"status\":400,"
                        + "\"body\":{\"message\":\"name must not be empty\"}}"),
            200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(PutFrostRecord.RECORDS_PER_REQUEST, "2");
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));
      runner.enqueue(THING_B.getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
      runner.assertTransferCount(PutFrostRecord.FAILURE, 1);
      runner
          .getFlowFilesForRelationship(PutFrostRecord.SUCCESS)
          .get(0)
          .assertContentEquals(THING_A);

      MockFlowFile failed = runner.getFlowFilesForRelationship(PutFrostRecord.FAILURE).get(0);
      failed.assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Thing");
      failed.assertAttributeEquals(PutFrostRecord.ERROR_STATUS, "400");
      failed.assertAttributeEquals(PutFrostRecord.ERROR_MESSAGE, "name must not be empty");
      // The record leaves unchanged, so an operator can send it again after the data is corrected.
      failed.assertContentEquals(THING_B);
    }
  }

  @Test
  void onTrigger_whenTheParentReferenceMisses_routesTheRecordToFailure()
      throws InitializationException {
    try (FrostEndpoint endpoint =
        FrostEndpoint.answering(
            document -> answers("{\"id\":\"r0-ds\",\"status\":200,\"body\":{\"value\":[]}}"),
            200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(PutFrostRecord.PORT, "Observations");
      runner.enqueue(
          "{\"result\":1,\"parameters\":{\"thingReference\":\"A7\",\"datastreamReference\":\"temp\"}}"
              .getBytes(StandardCharsets.UTF_8));

      runner.run();

      MockFlowFile failed = runner.getFlowFilesForRelationship(PutFrostRecord.FAILURE).get(0);
      failed.assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Datastream");
      failed.assertAttributeEquals(PutFrostRecord.ERROR_STATUS, "404");
    }
  }

  @Test
  void onTrigger_withARecordThatIsNotPlannable_neverSendsIt() throws InitializationException {
    try (FrostEndpoint endpoint = FrostEndpoint.answering(document -> answers(), 200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.enqueue("{\"name\":\"no key\"}".getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.FAILURE, 1);
      // A request built from this record would be answered with a status that describes the
      // request, not the defect in the record.
      assertTrue(endpoint.documents().isEmpty());
      runner
          .getFlowFilesForRelationship(PutFrostRecord.FAILURE)
          .get(0)
          .assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Thing");
    }
  }

  @Test
  void onTrigger_whenFrostCannotAnswerNow_routesTheRecordToRetry() throws InitializationException {
    try (FrostEndpoint endpoint = FrostEndpoint.answering(document -> "{}", 503)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.RETRY, 1);
      runner.assertTransferCount(PutFrostRecord.FAILURE, 0);
    }
  }

  @Test
  void onTrigger_whenTheAnswerIsNotABatchResponse_routesTheRecordToFailure()
      throws InitializationException {
    // A FROST server without the batch extension answers the same URL with an entity document.
    try (FrostEndpoint endpoint = FrostEndpoint.answering(document -> "{\"value\":[]}", 200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.SUCCESS, 0);
      runner.assertTransferCount(PutFrostRecord.FAILURE, 1);
    }
  }

  @Test
  void onTrigger_withACredential_authenticatesTheRequest() throws InitializationException {
    try (FrostEndpoint endpoint =
        FrostEndpoint.answering(
            document ->
                answers(
                    "{\"id\":\"r0-thing\",\"status\":200,\"body\":{\"value\":[]}}",
                    "{\"id\":\"r0-thing\",\"status\":201,\"body\":{\"@iot.id\":9}}"),
            200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(PutFrostRecord.USERNAME, "frost");
      runner.setProperty(PutFrostRecord.PASSWORD, "secret");
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));

      runner.run();

      runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
      assertEquals("Basic ZnJvc3Q6c2VjcmV0", endpoint.authorizations().get(0));
      // The credential is in the header alone. It reaches no attribute of the record.
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(PutFrostRecord.SUCCESS)) {
        assertTrue(flowFile.getAttributes().values().stream().noneMatch(v -> v.contains("secret")));
      }
    }
  }

  @Test
  void onTrigger_withOneRecordPerRequest_sendsOneBatchForEachRecord()
      throws InitializationException {
    try (FrostEndpoint endpoint =
        FrostEndpoint.answering(PutFrostRecordTest::createEveryThing, 200)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.enqueue(THING_A.getBytes(StandardCharsets.UTF_8));
      runner.enqueue(THING_B.getBytes(StandardCharsets.UTF_8));

      runner.run(2);

      // The default sends every record as it arrives. The window that collects them is a later
      // step; nothing here waits for a second record.
      runner.assertTransferCount(PutFrostRecord.SUCCESS, 2);
      assertEquals(2, endpoint.documents().size());
      for (JsonNode document : endpoint.documents()) {
        assertEquals(3, document.get("requests").size());
      }
    }
  }

  /** Answers every lookup of the document with a miss and every create with a new entity. */
  private static String createEveryThing(JsonNode document) {
    List<String> answers = new ArrayList<>();
    for (JsonNode request : document.get("requests")) {
      String id = request.get("id").asText();
      if ("get".equals(request.get("method").asText())) {
        answers.add("{\"id\":\"" + id + "\",\"status\":200,\"body\":{\"value\":[]}}");
      } else if (request.has("if") && request.get("if").asText().startsWith("not ")) {
        answers.add("{\"id\":\"" + id + "\",\"status\":201,\"body\":{\"@iot.id\":9}}");
      }
    }
    return answers(answers.toArray(new String[0]));
  }

  private static String answers(String... answers) {
    return "{\"responses\":[" + String.join(",", answers) + "]}";
  }

  private TestRunner runner(String baseUrl) throws InitializationException {
    TestRunner runner = TestRunners.newTestRunner(PutFrostRecord.class);
    StandardWebClientServiceProvider webClient = new StandardWebClientServiceProvider();
    runner.addControllerService(WEB_CLIENT, webClient);
    runner.enableControllerService(webClient);

    runner.setProperty(PutFrostRecord.WEB_CLIENT_SERVICE, WEB_CLIENT);
    runner.setProperty(PutFrostRecord.PORT, "Things");
    runner.setProperty(PutFrostRecord.BASE_URL, baseUrl);
    runner.setProperty(PutFrostRecord.PROJECT_ID, "5");
    return runner;
  }
}
