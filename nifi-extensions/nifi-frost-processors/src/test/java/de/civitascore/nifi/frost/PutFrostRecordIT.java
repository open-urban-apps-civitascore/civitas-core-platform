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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.web.client.provider.service.StandardWebClientServiceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * Writes with each of the three ports against a <b>real FROST-Server</b> and reads back what
 * arrived.
 *
 * <p>The unit tests answer the processor from a loopback endpoint, which proves the document it
 * builds. They cannot prove that FROST accepts that document: the batch extension, the atomicity
 * group, the {@code if} condition and the back-reference are the server's behaviour, not ours. This
 * test closes that gap — it asserts entities in FROST, not sub-requests in a document.
 *
 * <p>Skipped without a Docker daemon, so a workstation without one still builds.
 */
class PutFrostRecordIT {

  private static final String WEB_CLIENT = "web-client";

  private static FrostStack frost;
  private static String projectId;

  @BeforeAll
  static void startFrost() throws Exception {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping the FROST integration test");
    frost = new FrostStack();
    projectId = frost.createProject("put-frost-record-it");
  }

  @AfterAll
  static void stopFrost() {
    if (frost != null) {
      frost.close();
    }
  }

  @Test
  void thingsPort_createsTheThingOnceAndUpdatesItOnTheSecondDelivery() throws Exception {
    String reference = "IT-THINGS-1";
    TestRunner runner = runner("Things");

    runner.enqueue(thing(reference, "Station one").getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    // The same reference again, with a changed name: the port must find the Thing and patch it.
    runner.clearTransferState();
    runner.enqueue(thing(reference, "Station one, renamed").getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    JsonNode things = frost.query(things(reference));
    assertEquals(1, things.size(), "a second delivery must not create a second Thing");
    assertEquals("Station one, renamed", things.get(0).path("name").asText());
  }

  @Test
  void thingTreePort_writesTheThingItsLocationItsDatastreamAndItsMeasurement() throws Exception {
    String reference = "IT-TREE-1";
    TestRunner runner = runner("ThingTree");

    runner.enqueue(tree(reference, "Tree station", 21.5).getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    assertEquals(1, frost.count(things(reference)), "the Thing must be in the project");
    JsonNode datastreams = frost.query(datastreams(reference + "-DS", reference));
    assertEquals(1, datastreams.size(), "the Datastream must be scoped to its Thing");
    long datastreamId = datastreams.get(0).path("@iot.id").asLong();
    assertEquals(
        1,
        frost.count("/Datastreams(" + datastreamId + ")/Observations"),
        "the measurement must be in the Datastream of the tree");
    assertEquals(
        1,
        frost.count(
            "/Locations" + FrostStack.filter("properties/reference eq '" + reference + "'")),
        "the Location must carry the Thing's reference");
    // The nested entities have no reference of their own; they exist through the deep insert.
    assertEquals(
        "S",
        frost.get("/Datastreams(" + datastreamId + ")/Sensor").path("name").asText(),
        "the Sensor must be deep-inserted with the Datastream");
  }

  @Test
  void thingTreePort_onASecondDelivery_writesNoSecondEntityButAppendsTheMeasurement()
      throws Exception {
    String reference = "IT-TREE-2";
    TestRunner runner = runner("ThingTree");

    runner.enqueue(tree(reference, "Tree station", 10.0).getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.clearTransferState();
    runner.enqueue(tree(reference, "Tree station", 11.0).getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    assertEquals(1, frost.count(things(reference)), "the Thing is an upsert on its reference");
    JsonNode datastreams = frost.query(datastreams(reference + "-DS", reference));
    assertEquals(1, datastreams.size(), "the Datastream is an upsert on its reference");
    long datastreamId = datastreams.get(0).path("@iot.id").asLong();
    // A measurement is an event, not a state: the tree appends it, which is what the graph it
    // replaces did.
    assertEquals(
        2,
        frost.count("/Datastreams(" + datastreamId + ")/Observations"),
        "the second measurement must be appended");
  }

  @Test
  void observationsPort_appendsToTheDatastreamTheRecordNames() throws Exception {
    String thingReference = "IT-OBS-THING-1";
    String datastreamReference = "IT-OBS-DS-1";
    long datastreamId = provisionDatastream(thingReference, datastreamReference);
    TestRunner runner = runner("Observations");

    runner.enqueue(
        observation(thingReference, datastreamReference, null, 7.5)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
    runner.clearTransferState();
    runner.enqueue(
        observation(thingReference, datastreamReference, null, 7.6)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();

    // Without a reference of its own every delivery is a new measurement.
    assertEquals(2, frost.count("/Datastreams(" + datastreamId + ")/Observations"));
  }

  @Test
  void observationsPort_withItsOwnReference_updatesInsteadOfAppending() throws Exception {
    String thingReference = "IT-OBS-THING-2";
    String datastreamReference = "IT-OBS-DS-2";
    long datastreamId = provisionDatastream(thingReference, datastreamReference);
    TestRunner runner = runner("Observations");

    runner.enqueue(
        observation(thingReference, datastreamReference, "IT-OBS-2-A", 1.0)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.clearTransferState();
    runner.enqueue(
        observation(thingReference, datastreamReference, "IT-OBS-2-A", 2.0)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    JsonNode observations = frost.query("/Datastreams(" + datastreamId + ")/Observations");
    assertEquals(1, observations.size(), "a corrected measurement must not be a second one");
    assertEquals(2.0, observations.get(0).path("result").asDouble());
  }

  @Test
  void observationsPort_whenTheDatastreamIsUnknown_routesTheRecordToFailure() throws Exception {
    TestRunner runner = runner("Observations");

    runner.enqueue(
        observation("IT-OBS-ABSENT", "IT-OBS-ABSENT-DS", null, 1.0)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();

    // The port writes its own entity only. A parent it cannot resolve is a defect of the data, and
    // the record goes to the error sink instead of provoking a Datastream nobody modelled.
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 0);
    MockFlowFile failed = runner.getFlowFilesForRelationship(PutFrostRecord.FAILURE).get(0);
    failed.assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Datastream");
  }

  @Test
  void aBatchWithOneDefectiveRecord_writesTheOthersAndFailsOnlyThatOne() throws Exception {
    TestRunner runner = runner("Things");
    runner.setProperty(PutFrostRecord.RECORDS_PER_REQUEST, "3");

    runner.enqueue(thing("IT-GROUP-A", "Group A").getBytes(StandardCharsets.UTF_8));
    // FROST rejects a Thing without a name. The record passes the planner — it carries its
    // reference — so the defect appears in the answer of the group, which is what isolation has to
    // survive.
    runner.enqueue(
        "{\"description\":\"no name\",\"properties\":{\"reference\":\"IT-GROUP-B\"}}"
            .getBytes(StandardCharsets.UTF_8));
    runner.enqueue(thing("IT-GROUP-C", "Group C").getBytes(StandardCharsets.UTF_8));

    runner.run();

    runner.assertTransferCount(PutFrostRecord.SUCCESS, 2);
    runner.assertTransferCount(PutFrostRecord.FAILURE, 1);
    assertEquals(1, frost.count(things("IT-GROUP-A")), "a correct record must write its Thing");
    assertEquals(1, frost.count(things("IT-GROUP-C")), "a correct record must write its Thing");
    assertEquals(
        0, frost.count(things("IT-GROUP-B")), "the defective record must write no half entity");
    runner
        .getFlowFilesForRelationship(PutFrostRecord.FAILURE)
        .get(0)
        .assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Thing");
  }

  /** The Things of the project carrying a reference. */
  private static String things(String reference) {
    return "/Projects("
        + projectId
        + ")/Things"
        + FrostStack.filter("properties/reference eq '" + reference + "'");
  }

  /** The Datastreams carrying a reference, scoped to their Thing. */
  private static String datastreams(String reference, String thingReference) {
    return "/Datastreams"
        + FrostStack.filter(
            "properties/reference eq '"
                + reference
                + "' and properties/thingReference eq '"
                + thingReference
                + "'");
  }

  /**
   * Creates the Thing and the Datastream an Observations-port record refers to. The Thing is
   * created inside the project because the Datastream lookup scopes through it, and it carries a
   * Location so FROST can generate the Observation's FeatureOfInterest.
   */
  private long provisionDatastream(String thingReference, String datastreamReference)
      throws Exception {
    String thingBody =
        "{\"name\":\"Obs host\",\"description\":\"provisioned by the IT\","
            + "\"properties\":{\"reference\":\""
            + thingReference
            + "\"},"
            + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
            + "\"encodingType\":\"application/geo+json\","
            + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}]}";
    frost.post("/Projects(" + projectId + ")/Things", thingBody);
    long thingId = frost.query(things(thingReference)).get(0).path("@iot.id").asLong();

    String datastreamBody =
        "{\"name\":\"Provisioned\",\"description\":\"provisioned by the IT\","
            + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
            + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\","
            + "\"definition\":\"ucum:Cel\"},"
            + "\"properties\":{\"reference\":\""
            + datastreamReference
            + "\",\"thingReference\":\""
            + thingReference
            + "\"},"
            + "\"Sensor\":{\"name\":\"S\",\"description\":\"s\","
            + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
            + "\"ObservedProperty\":{\"name\":\"Temperature\","
            + "\"definition\":\"http://example.org/temperature\",\"description\":\"t\"}}";
    frost.post("/Things(" + thingId + ")/Datastreams", datastreamBody);
    return frost
        .query(datastreams(datastreamReference, thingReference))
        .get(0)
        .path("@iot.id")
        .asLong();
  }

  private static String thing(String reference, String name) {
    return "{\"name\":\""
        + name
        + "\",\"description\":\"written by the IT\","
        + "\"properties\":{\"reference\":\""
        + reference
        + "\"}}";
  }

  /**
   * The record shape the ThingTree port expects: a Thing with its Location, Datastream and one
   * measurement.
   */
  private static String tree(String reference, String name, double result) {
    return "{\"name\":\""
        + name
        + "\",\"description\":\"written by the IT\","
        + "\"properties\":{\"reference\":\""
        + reference
        + "\"},"
        + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
        + "\"encodingType\":\"application/geo+json\","
        + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}],"
        + "\"Datastreams\":[{\"name\":\"Temperature\",\"description\":\"ds\","
        + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
        + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\","
        + "\"definition\":\"ucum:Cel\"},"
        + "\"properties\":{\"reference\":\""
        + reference
        + "-DS\"},"
        + "\"Sensor\":{\"name\":\"S\",\"description\":\"s\","
        + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
        + "\"ObservedProperty\":{\"name\":\"Temperature\","
        + "\"definition\":\"http://example.org/temperature\",\"description\":\"t\"},"
        + "\"Observations\":[{\"result\":"
        + result
        + ",\"phenomenonTime\":\"2026-01-01T00:00:00Z\"}]}]}";
  }

  private static String observation(
      String thingReference, String datastreamReference, String ownReference, double result) {
    String own = ownReference == null ? "" : ",\"reference\":\"" + ownReference + "\"";
    return "{\"result\":"
        + result
        + ",\"phenomenonTime\":\"2026-01-01T00:00:00Z\","
        + "\"parameters\":{\"thingReference\":\""
        + thingReference
        + "\",\"datastreamReference\":\""
        + datastreamReference
        + "\""
        + own
        + "}}";
  }

  private TestRunner runner(String port) throws InitializationException {
    TestRunner runner = TestRunners.newTestRunner(PutFrostRecord.class);
    StandardWebClientServiceProvider webClient = new StandardWebClientServiceProvider();
    runner.addControllerService(WEB_CLIENT, webClient);
    runner.enableControllerService(webClient);

    runner.setProperty(PutFrostRecord.WEB_CLIENT_SERVICE, WEB_CLIENT);
    runner.setProperty(PutFrostRecord.PORT, port);
    runner.setProperty(PutFrostRecord.BASE_URL, frost.baseUrl());
    runner.setProperty(PutFrostRecord.PROJECT_ID, projectId);
    return runner;
  }
}
