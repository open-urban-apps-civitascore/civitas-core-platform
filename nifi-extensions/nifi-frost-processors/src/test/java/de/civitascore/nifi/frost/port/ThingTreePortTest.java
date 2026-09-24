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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
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
  void plan_withoutADatastream_endsAfterTheMetadata() {
    String metadataOnly =
        """
        { "name": "Station A7", "properties": { "reference": "A7" } }
        """;
    JsonNode requests = BatchDocuments.of(SinkPort.THING_TREE, metadataOnly).get("requests");

    assertEquals(3, requests.size());
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
