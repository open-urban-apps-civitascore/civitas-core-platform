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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThingTreePortTest {

  private static final String TREE =
      """
      {
        "name": "Station A7",
        "description": "Weather station",
        "properties": { "reference": "A7", "referenceName": "stationsnummer" },
        "Locations": [
          {
            "name": "A7 site",
            "encodingType": "application/geo+json",
            "location": { "type": "Point", "coordinates": [9.18, 48.78] }
          }
        ],
        "Datastreams": [
          {
            "name": "Air temperature",
            "description": "Air temperature at A7",
            "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
            "unitOfMeasurement": {
              "name": "degree Celsius",
              "symbol": "degC",
              "definition": "http://unitsofmeasure.org/ucum.html#para-30"
            },
            "properties": { "reference": "temp" },
            "Sensor": {
              "name": "DS18B20",
              "description": "Air temperature sensor",
              "encodingType": "application/pdf",
              "metadata": "http://example.org/ds18b20.pdf"
            },
            "ObservedProperty": {
              "name": "Temperature",
              "definition": "http://dd.eionet.europa.eu/vocabulary/aq/meteoparameter/54",
              "description": "Air temperature"
            },
            "Observations": [
              { "phenomenonTime": "2026-09-18T07:00:00Z", "result": 21.5 }
            ]
          }
        ]
      }
      """;

  @Test
  void plan_withATree_buildsTheChainOfSixEntities() {
    BatchDocuments.assertMatches(
        "thing-tree-port.json", BatchDocuments.of(SinkPort.THING_TREE, TREE));
  }

  @Test
  void plan_withATree_writesTheThingReferenceIntoEveryEntityBelowIt() {
    // A later Pipeline resolves a Datastream with a direct query on the collection. It can do that
    // only if the tree wrote the reference of the Thing into the Datastream.
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TREE).get("requests");

    JsonNode datastreamCreate = requestWith(requests, "not $r0-ds");
    assertEquals(
        "A7", datastreamCreate.get("body").get("properties").get("thingReference").asText());

    JsonNode locationCreate = requestWith(requests, "not $r0-loc");
    assertEquals("A7", locationCreate.get("body").get("properties").get("thingReference").asText());
  }

  @Test
  void plan_withATree_keepsNavigationOutOfEveryUpdate() {
    // SensorThings rejects a PATCH that carries a navigation member, and the tree writes every
    // nested entity through its own sub-request anyway.
    for (JsonNode request : BatchDocuments.of(SinkPort.THING_TREE, TREE).get("requests")) {
      if (!"patch".equals(request.get("method").asText())) {
        continue;
      }
      JsonNode body = request.get("body");
      assertFalse(body.has("Locations"), request.get("url").asText());
      assertFalse(body.has("Datastreams"), request.get("url").asText());
      assertFalse(body.has("Sensor"), request.get("url").asText());
      assertFalse(body.has("ObservedProperty"), request.get("url").asText());
      assertFalse(body.has("Observations"), request.get("url").asText());
    }
  }

  @Test
  void plan_withATree_asksForTheNestedEntitiesBeforeItCreatesTheDatastream() {
    // The lookup and the create of the Datastream share an identifier. A request that must see the
    // lookup result alone therefore has to stand before the create, which after a create resolves
    // to the entity the create wrote.
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TREE).get("requests");

    assertTrue(
        indexOfCondition(requests, "$r0-ds", "get")
            < indexOfCondition(requests, "not $r0-ds", "post"));
  }

  @Test
  void plan_withATree_readsANestedEntityThroughTheBackReferenceOfItsDatastream() {
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TREE).get("requests");

    // The reference stands at the start of the URL, which is the only place FROST reads one, and
    // the navigation follows it: $r0-ds/Sensor becomes /Datastreams(7)/Sensor.
    JsonNode sensorLookup = requestWithUrl(requests, "$r0-ds/Sensor?$select=id");
    assertEquals("get", sensorLookup.get("method").asText());
  }

  @Test
  void plan_withATree_looksTheLocationUpAmongTheLocationsOfItsThing() {
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TREE).get("requests");

    // The Location collection belongs to no project, and a reference is local to its Dataset: two
    // Datasets modelling the same device share it. A direct query on /Locations found the first
    // Dataset's Location, patched it, and left the second Dataset's Thing without one — and
    // FROST then could not generate a FeatureOfInterest for any measurement of that Thing.
    JsonNode lookup =
        requestWithUrl(
            requests, "$r0-thing/Locations?$select=id&$top=1&$filter=properties/reference eq 'A7'");
    assertEquals("get", lookup.get("method").asText());
    for (JsonNode request : requests) {
      assertFalse(
          request.path("url").asText().startsWith("Locations?"),
          "a Location lookup must not query the unscoped collection: " + request);
    }
  }

  @Test
  void plan_withAFeatureOfInterestInTheMeasurement_assignsItToTheProject() {
    String withFeature =
        """
        {
          "properties": { "reference": "A7" },
          "Datastreams": [
            {
              "name": "Air temperature",
              "properties": { "reference": "temp" },
              "Observations": [
                {
                  "result": 21.5,
                  "FeatureOfInterest": {
                    "name": "Garden",
                    "encodingType": "application/geo+json",
                    "feature": { "type": "Point", "coordinates": [8.4, 49.0] }
                  }
                }
              ]
            }
          ]
        }
        """;
    JsonNode observationCreate =
        requestWithUrl(
            BatchDocuments.of(SinkPort.THING_TREE, withFeature).get("requests"), "Observations");

    // The Dataset's API serves its project: a feature outside it is written but never listed.
    assertEquals(5, observationCreate.at("/body/FeatureOfInterest/Projects/0/@iot.id").asLong());
    assertFalse(observationCreate.get("body").has("Projects"));
  }

  @Test
  void plan_withoutADatastream_endsAfterTheMetadata() {
    String metadataOnly =
        """
        { "name": "Station A7", "properties": { "reference": "A7" } }
        """;
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, metadataOnly).get("requests");

    assertEquals(3, requests.size());
  }

  private static final String TWO_DATASTREAMS =
      """
      {
        "properties": { "reference": "A7" },
        "Locations": [
          {
            "name": "A7 site",
            "encodingType": "application/geo+json",
            "location": { "type": "Point", "coordinates": [9.18, 48.78] }
          }
        ],
        "Datastreams": [
          {
            "name": "Air temperature",
            "properties": { "reference": "temp" },
            "Sensor": { "name": "DS18B20" },
            "Observations": [ { "result": 21.5 } ]
          },
          {
            "name": "Humidity",
            "properties": { "reference": "hum" },
            "Sensor": { "name": "SHT31" },
            "ObservedProperty": { "name": "Relative humidity" },
            "Observations": [ { "result": 40 }, { "result": 41 } ]
          }
        ]
      }
      """;

  @Test
  void plan_withTwoDatastreams_upsertsEachOnItsOwnReference() {
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TWO_DATASTREAMS).get("requests");

    JsonNode first = requestWith(requests, "not $r0-ds");
    assertEquals("temp", first.at("/body/properties/reference").asText());
    assertEquals("DS18B20", first.at("/body/Sensor/name").asText());
    JsonNode second = requestWith(requests, "not $r0-ds1");
    assertEquals("hum", second.at("/body/properties/reference").asText());
    assertEquals("SHT31", second.at("/body/Sensor/name").asText());
    assertEquals("A7", second.at("/body/properties/thingReference").asText());
    assertEquals("$r0-thing", second.at("/body/Thing/@iot.id").asText());

    // The nested update of the second Datastream reads through its own back-reference.
    JsonNode sensorLookup = requestWithUrl(requests, "$r0-ds1/Sensor?$select=id");
    assertEquals("r0-ds1-sensor", sensorLookup.get("id").asText());
    assertEquals("$r0-ds1", sensorLookup.get("if").asText());
    requestWithUrl(requests, "$r0-ds1/ObservedProperty?$select=id");
  }

  @Test
  void plan_withTwoDatastreams_linksEveryMeasurementToItsOwnDatastream() {
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TWO_DATASTREAMS).get("requests");

    List<String> links = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    for (JsonNode request : requests) {
      if ("Observations".equals(request.path("url").asText())) {
        links.add(request.at("/body/Datastream/@iot.id").asText());
        ids.add(request.get("id").asText());
      }
    }
    assertEquals(List.of("$r0-ds", "$r0-ds1", "$r0-ds1"), links);
    assertEquals(List.of("r0-obs", "r0-ds1-obs", "r0-ds1-obs1"), ids);
  }

  @Test
  void plan_withTwoDatastreams_postsEachMeasurementAfterTheCreateOfItsDatastream() {
    // A back-reference resolves to the latest request carrying the identifier, so a measurement
    // must follow the create it links to.
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, TWO_DATASTREAMS).get("requests");

    int secondCreate = indexOfCondition(requests, "not $r0-ds1", "post");
    for (int index = 0; index < requests.size(); index++) {
      JsonNode request = requests.get(index);
      if ("$r0-ds1".equals(request.at("/body/Datastream/@iot.id").asText())) {
        assertTrue(index > secondCreate, request.toString());
      }
    }
  }

  @Test
  void plan_withTwoDatastreamsOfOneReference_rejectsTheRecord() {
    String twice =
        """
        {
          "properties": { "reference": "A7" },
          "Datastreams": [
            { "properties": { "reference": "temp" } },
            { "properties": { "reference": "temp" } }
          ]
        }
        """;

    // The second upsert would find the first Datastream and patch it, and one of them would be
    // lost without a trace.
    RecordRejectedException rejected =
        assertThrows(
            RecordRejectedException.class, () -> BatchDocuments.of(SinkPort.THING_TREE, twice));
    assertEquals("Datastream[1]", rejected.entity());
  }

  @Test
  void plan_withADatastreamWithoutReference_namesItsPosition() {
    String keyless =
        """
        {
          "properties": { "reference": "A7" },
          "Datastreams": [ { "properties": { "reference": "temp" } }, { "name": "Humidity" } ]
        }
        """;

    RecordRejectedException rejected =
        assertThrows(
            RecordRejectedException.class, () -> BatchDocuments.of(SinkPort.THING_TREE, keyless));
    assertEquals("Datastream[1]", rejected.entity());
  }

  @Test
  void plan_withTwoLocationsOfTheirOwnReference_upsertsBoth() {
    String twoSites =
        """
        {
          "properties": { "reference": "A7" },
          "Locations": [
            { "name": "Mast", "properties": { "reference": "mast" } },
            { "name": "Cabinet", "properties": { "reference": "cabinet" } }
          ]
        }
        """;
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, twoSites).get("requests");

    assertEquals(
        "mast", requestWith(requests, "not $r0-loc").at("/body/properties/reference").asText());
    assertEquals(
        "cabinet", requestWith(requests, "not $r0-loc1").at("/body/properties/reference").asText());
    requestWithUrl(
        requests,
        "$r0-thing/Locations?$select=id&$top=1&$filter=properties/reference eq 'cabinet'");
  }

  @Test
  void plan_withTwoLocationsWithoutReference_rejectsTheRecord() {
    // Each would fall back to the reference of the Thing, and the second would overwrite the first.
    String twoSites =
        """
        {
          "properties": { "reference": "A7" },
          "Locations": [ { "name": "Mast" }, { "name": "Cabinet" } ]
        }
        """;

    RecordRejectedException rejected =
        assertThrows(
            RecordRejectedException.class, () -> BatchDocuments.of(SinkPort.THING_TREE, twoSites));
    assertEquals("Location[0]", rejected.entity());
  }

  private static JsonNode requestWithUrl(JsonNode requests, String url) {
    for (JsonNode request : requests) {
      if (url.equals(request.path("url").asText())) {
        return request;
      }
    }
    throw new AssertionError("no request carries the url " + url);
  }

  private static JsonNode requestWith(JsonNode requests, String condition) {
    for (JsonNode request : requests) {
      if (request.has("if") && condition.equals(request.get("if").asText())) {
        return request;
      }
    }
    throw new AssertionError("no request with the condition " + condition);
  }

  private static int indexOfCondition(JsonNode requests, String condition, String method) {
    for (int index = 0; index < requests.size(); index++) {
      JsonNode request = requests.get(index);
      if (request.has("if")
          && condition.equals(request.get("if").asText())
          && method.equals(request.get("method").asText())) {
        return index;
      }
    }
    throw new AssertionError("no " + method + " with the condition " + condition);
  }
}
