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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  private static final ObjectMapper MAPPER = new ObjectMapper();

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
    // The Dataset's API serves the project: what lies outside it is written but never listed.
    assertEquals(
        1, frost.count(projectLocations(reference)), "the Location must be in the project");
    assertEquals(1, frost.count(projectSensors(datastreamId)), "the Sensor must be in the project");
    // The measurement brings no FeatureOfInterest. FROST generates one from the Location and gives
    // it the Location's projects, which is why the port does not assign it itself.
    assertEquals(
        1,
        frost.count(
            "/Projects("
                + projectId
                + ")/FeaturesOfInterest"
                + FrostStack.filter("Observations/Datastream/id eq " + datastreamId)),
        "the generated FeatureOfInterest must be in the project");
  }

  @Test
  void thingTreePort_writesEveryDatastreamOfTheTreeAndReusesThemOnTheSecondDelivery()
      throws Exception {
    String reference = "IT-TREE-MANY";
    ObjectNode record = (ObjectNode) MAPPER.readTree(tree(reference, "Many streams", 21.5));
    ObjectNode humidity = ((ObjectNode) record.at("/Datastreams/0")).deepCopy();
    humidity.put("name", "Humidity");
    ((ObjectNode) humidity.get("properties")).put("reference", reference + "-HUM");
    ((ObjectNode) humidity.get("Sensor")).put("name", "S2");
    ((ArrayNode) humidity.get("Observations")).add(humidity.at("/Observations/0").deepCopy());
    ((ArrayNode) record.get("Datastreams")).add(humidity);
    TestRunner runner = runner("ThingTree");

    for (int delivery = 0; delivery < 2; delivery++) {
      runner.clearTransferState();
      runner.enqueue(MAPPER.writeValueAsBytes(record));
      runner.run();
      runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
    }

    assertEquals(1, frost.count(things(reference)), "a second delivery must reuse the Thing");
    long thingId = frost.query(things(reference)).get(0).path("@iot.id").asLong();
    assertEquals(2, frost.count("/Things(" + thingId + ")/Datastreams"));
    JsonNode temperature = frost.query(datastreams(reference + "-DS", reference));
    JsonNode humidities = frost.query(datastreams(reference + "-HUM", reference));
    assertEquals(1, temperature.size(), "a second delivery must reuse the first Datastream");
    assertEquals(1, humidities.size(), "a second delivery must reuse the second Datastream");
    long temperatureId = temperature.get(0).path("@iot.id").asLong();
    long humidityId = humidities.get(0).path("@iot.id").asLong();
    assertEquals(2, frost.count("/Datastreams(" + temperatureId + ")/Observations"));
    assertEquals(4, frost.count("/Datastreams(" + humidityId + ")/Observations"));
    // Each Datastream keeps its own Sensor: the nested update read through its own back-reference.
    assertEquals(
        "S", frost.get("/Datastreams(" + temperatureId + ")/Sensor").path("name").asText());
    assertEquals("S2", frost.get("/Datastreams(" + humidityId + ")/Sensor").path("name").asText());
  }

  @Test
  void thingTreePort_assignsALocationAndASensorWrittenWithoutTheProject() throws Exception {
    String reference = "IT-TREE-3";
    // The tree as the port wrote it before it assigned anything but the Thing.
    ObjectNode unassigned = (ObjectNode) MAPPER.readTree(tree(reference, "Tree station", 1.0));
    ((ObjectNode) unassigned.at("/Locations/0"))
        .putObject("properties")
        .put("reference", reference)
        .put("thingReference", reference);
    ((ObjectNode) unassigned.at("/Datastreams/0/properties")).put("thingReference", reference);
    frost.post("/Projects(" + projectId + ")/Things", unassigned.toString());
    long datastreamId =
        frost.query(datastreams(reference + "-DS", reference)).get(0).path("@iot.id").asLong();
    assertEquals(0, frost.count(projectLocations(reference)));
    assertEquals(0, frost.count(projectSensors(datastreamId)));

    TestRunner runner = runner("ThingTree");
    runner.enqueue(tree(reference, "Tree station", 2.0).getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    // The update assigns the entities it finds, so the next delivery repairs the older ones.
    assertEquals(
        1, frost.count(projectLocations(reference)), "the Location must be in the project");
    assertEquals(1, frost.count(projectSensors(datastreamId)), "the Sensor must be in the project");
    assertEquals(
        1,
        frost.count(
            "/Locations" + FrostStack.filter("properties/reference eq '" + reference + "'")),
        "the update must not create a second Location");
  }

  @Test
  void thingsPort_assignsWhatTheRecordDeepInsertsToTheProject() throws Exception {
    String reference = "IT-THINGS-2";
    TestRunner runner = runner("Things");

    runner.enqueue(tree(reference, "Deep station", 1.0).getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    long thingId = frost.query(things(reference)).get(0).path("@iot.id").asLong();
    long datastreamId =
        frost.query("/Things(" + thingId + ")/Datastreams").get(0).path("@iot.id").asLong();
    assertEquals(
        1,
        frost.count(
            "/Projects("
                + projectId
                + ")/Locations"
                + FrostStack.filter("Things/id eq " + thingId)),
        "the Location must be in the project");
    assertEquals(1, frost.count(projectSensors(datastreamId)), "the Sensor must be in the project");
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
  void observationsPort_assignsTheFeatureOfInterestOfTheMeasurementToTheProject() throws Exception {
    String thingReference = "IT-OBS-THING-3";
    String datastreamReference = "IT-OBS-DS-3";
    long datastreamId = provisionDatastream(thingReference, datastreamReference);
    TestRunner runner = runner("Observations");
    ObjectNode measurement =
        (ObjectNode) MAPPER.readTree(observation(thingReference, datastreamReference, null, 4.0));
    measurement
        .putObject("FeatureOfInterest")
        .put("name", "Garden")
        .put("description", "The garden")
        .put("encodingType", "application/geo+json")
        .set("feature", MAPPER.readTree("{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}"));

    runner.enqueue(measurement.toString().getBytes(StandardCharsets.UTF_8));
    runner.run();
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);

    assertEquals(
        1,
        frost.count(
            "/Projects("
                + projectId
                + ")/FeaturesOfInterest"
                + FrostStack.filter("Observations/Datastream/id eq " + datastreamId)),
        "the FeatureOfInterest must be in the project");
  }

  @Test
  void observationsPort_whenTheDatastreamIsUnknown_routesTheRecordToRetry() throws Exception {
    TestRunner runner = runner("Observations");

    runner.enqueue(
        observation("IT-OBS-ABSENT", "IT-OBS-ABSENT-DS", null, 1.0)
            .getBytes(StandardCharsets.UTF_8));
    runner.run();

    // The port writes its own entity only, and does not provoke a Datastream nobody modelled. But
    // the Pipeline that writes it may not have run yet, so the record is retried — NiFi sends it
    // to the error sink only once the attempts are spent.
    runner.assertTransferCount(PutFrostRecord.SUCCESS, 0);
    runner.assertTransferCount(PutFrostRecord.FAILURE, 0);
    MockFlowFile retried = runner.getFlowFilesForRelationship(PutFrostRecord.RETRY).get(0);
    retried.assertAttributeEquals(PutFrostRecord.ERROR_ENTITY, "Datastream");
  }

  @Test
  void observationsPort_aMeasurementBeforeItsMasterData_isWrittenOnceTheMasterDataArrives()
      throws Exception {
    String thingReference = "IT-OBS-LATE-THING";
    String datastreamReference = "IT-OBS-LATE-DS";
    TestRunner runner = runner("Observations");
    byte[] measurement =
        observation(thingReference, datastreamReference, null, 3.5)
            .getBytes(StandardCharsets.UTF_8);

    runner.enqueue(measurement);
    runner.run();
    runner.assertTransferCount(PutFrostRecord.RETRY, 1);

    // The master data arrives. TestRunner does not re-queue a retried FlowFile the way NiFi does,
    // so the same record is sent again by hand — which is what the framework retry amounts to.
    long datastreamId = provisionDatastream(thingReference, datastreamReference);
    runner.clearTransferState();
    runner.enqueue(measurement);
    runner.run();

    runner.assertTransferCount(PutFrostRecord.SUCCESS, 1);
    assertEquals(1, frost.count("/Datastreams(" + datastreamId + ")/Observations"));
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

  /** The Locations of the project carrying a reference. */
  private static String projectLocations(String reference) {
    return "/Projects("
        + projectId
        + ")/Locations"
        + FrostStack.filter("properties/reference eq '" + reference + "'");
  }

  /** The Sensor of a Datastream, if it is in the project. */
  private static String projectSensors(long datastreamId) {
    return "/Projects("
        + projectId
        + ")/Sensors"
        + FrostStack.filter("Datastreams/id eq " + datastreamId);
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
