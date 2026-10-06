/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FreeAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.KeyAttribute;
import de.civitascore.configadapter.nifi.mapping.SinkPort;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaEntity;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaTarget;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.TargetKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code parseSpec}'s schema→match-key resolution — the seam every mapped FROST deployment
 * crosses: a wrong result here deploys wrong {@code $filter}s (cross-entity dedup) or fails every
 * publish.
 */
class FrostSinkStageTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FrostSinkStage stage =
      new FrostSinkStage("http://frost:8080/v1.1", FrostSinkAuth.basicAuth("frost", "secret"));
  private final SinkResolutionContext ctx = new SinkResolutionContext("7", null);

  private static Map<String, Object> json(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A wrapper-rooted Thing structure, parameterized on the two entity classes' {@code properties}
   * bag content — the match key lives inside {@code properties}, not on the entity class itself.
   */
  private static Map<String, Object> datasinkWith(String thingProps, String datastreamProps) {
    return json(
        """
        { "configuration": { "port": "ThingTree" },
          "dataStructure": {
            "title": "SensorThingsDataModel",
            "properties": { "thing": { "$ref": "#/$defs/Thing" } },
            "$defs": {
              "Thing": { "properties": {
                  "properties": { "type": "object", "properties": { %s } },
                  "Datastreams": { "type": "array", "items": { "$ref": "#/$defs/Datastream" } } } },
              "Datastream": { "properties": {
                  "properties": { "type": "object", "properties": { %s } },
                  "name": { "type": "string" } } } } } }
        """
            .formatted(thingProps, datastreamProps));
  }

  @Test
  void resolvesDatastreamBagThroughLowercaseRelationshipName() throws Exception {
    Map<String, Object> datasink =
        datasinkWith(
            "\"reference\": { \"type\": \"string\" }", "\"reference\": { \"type\": \"string\" }");
    @SuppressWarnings("unchecked")
    Map<String, Object> schema = (Map<String, Object>) datasink.get("dataStructure");
    @SuppressWarnings("unchecked")
    Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
    @SuppressWarnings("unchecked")
    Map<String, Object> thing = (Map<String, Object>) defs.get("Thing");
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) thing.get("properties");
    properties.put("datastream", properties.remove("Datastreams"));

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(List.of("reference"), spec.staProperties().datastreamKeys());
  }

  @Test
  void primaryKeyMarkerWinsOverACoexistingReferenceAttribute() throws Exception {
    FrostSinkSpec spec =
        stage.parseSpec(
            datasinkWith(
                "\"stationId\": { \"type\": \"string\", \"x-core-primaryKey\": true },"
                    + " \"reference\": { \"type\": \"string\" }",
                "\"dsId\": { \"type\": \"string\", \"x-core-primaryKey\": true }"),
            ctx);

    assertEquals(List.of("stationId"), spec.staProperties().thingKeys());
    assertEquals(List.of("dsId"), spec.staProperties().datastreamKeys());
  }

  @Test
  void fallsBackToADeclaredReferenceAttributeWithoutAMarker() throws Exception {
    FrostSinkSpec spec =
        stage.parseSpec(
            datasinkWith(
                "\"reference\": { \"type\": \"string\" }",
                "\"reference\": { \"type\": \"string\" }"),
            ctx);

    assertEquals(List.of("reference"), spec.staProperties().thingKeys());
    assertEquals(List.of("reference"), spec.staProperties().datastreamKeys());
  }

  @Test
  void yieldsEmptyKeysWhenNeitherMarkerNorReferenceExists() throws Exception {
    FrostSinkSpec spec = stage.parseSpec(datasinkWith("", ""), ctx);

    assertTrue(spec.staProperties().thingKeys().isEmpty());
    assertTrue(spec.staProperties().datastreamKeys().isEmpty());
  }

  @Test
  void aBrokenPropertiesBagRefFailsThePlanInsteadOfDegradingToNoKeys() {
    // A properties bag that DECLARES content via a dangling $ref is structurally broken, not an
    // absent bag — degrading it to "no keys" would surface as the misleading "declares no match
    // key" error instead of naming the broken reference.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": { "Thing": { "properties": {
                    "properties": { "$ref": "#/$defs/Missing" } } } } } }
            """);

    assertThrows(FatalAdapterException.class, () -> stage.parseSpec(datasink, ctx));
  }

  @Test
  void resolvesAPropertiesBagModelledAsASharedNamedClassRef() throws Exception {
    // The UML modeller authors the properties bag as a shared named class both entities compose,
    // so the entity's 'properties' is a $ref the derivation must follow, not an inline object.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Properties": { "properties": {
                      "reference": { "type": "string", "x-core-primaryKey": true } } },
                  "Thing": { "properties": {
                      "properties": { "$ref": "#/$defs/Properties" },
                      "Datastreams": { "type": "array", "items": { "$ref": "#/$defs/Datastream" } } } },
                  "Datastream": { "properties": {
                      "properties": { "$ref": "#/$defs/Properties" } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(List.of("reference"), spec.staProperties().thingKeys());
    assertEquals(List.of("reference"), spec.staProperties().datastreamKeys());
  }

  @Test
  void resolvesReferenceFromALittleThingModel() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "$id": "urn:core:platform:civitas:datastructure:common:0830d1e3-2c72-80b1-b2ff-5138756fad56:1.0.0",
                "type": "object",
                "$defs": {
                  "Thing": {
                    "type": "object",
                    "required": ["name", "description", "definition", "properties"],
                    "properties": {
                      "name": { "type": "string" },
                      "definition": { "type": "string" },
                      "properties": { "$ref": "#/$defs/properties" },
                      "description": { "type": "string" }
                    }
                  },
                  "properties": {
                    "type": "object",
                    "required": ["reference"],
                    "properties": { "reference": { "type": "string" } }
                  }
                },
                "properties": { "thing": { "$ref": "#/$defs/Thing" } }
              }
            }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(List.of("reference"), spec.staProperties().thingKeys());
    assertTrue(spec.staProperties().datastreamKeys().isEmpty());
  }

  @Test
  void derivesFreeBagAttributesWithTypesAndTheMatchKeyFlag() throws Exception {
    // A bag with the match key + a free scalar + a free object: both render type-inferred — a free
    // object has no type of its own and the editor shows it as text. Only the marked attribute is
    // the key. Declaration order is preserved.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Thing": {
                    "properties": {
                      "properties": {
                        "type": "object",
                        "properties": {
                          "reference": { "type": "string", "x-core-primaryKey": true },
                          "owner": { "type": "string" },
                          "meta": { "type": "object" }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(
            new KeyAttribute("reference"),
            new FreeAttribute("owner", StaJsonType.ANY),
            new FreeAttribute("meta", StaJsonType.ANY)),
        spec.staProperties().thing());
  }

  @Test
  void aNonScalarMarkedAttributeIsNotTheKeyAndStaysRawJson() throws Exception {
    // A {id} marker on a non-scalar (array/$ref) cannot back a match key — it must fall through to
    // the scalar 'reference' fallback for the key, while the marked array stays a free RAW_JSON
    // attribute (a marked object never quietly becomes a STRING key). A class inside the bag is no
    // attribute of its own: its attributes become modelled targets nested under it.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Geo": { "type": "object", "properties": { "lat": { "type": "number" } } },
                  "Thing": {
                    "properties": {
                      "properties": {
                        "type": "object",
                        "properties": {
                          "tags": { "type": "array", "x-core-primaryKey": true },
                          "reference": { "type": "string" },
                          "region": { "$ref": "#/$defs/Geo" }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(new FreeAttribute("tags", StaJsonType.RAW_JSON), new KeyAttribute("reference")),
        spec.staProperties().thing());
    assertEquals(
        List.of(
            new StaTarget(
                "$.properties.region.lat", StaEntity.THING, StaJsonType.ANY, TargetKind.OPTIONAL)),
        spec.staProperties().modelled());
  }

  @Test
  void derivesFreeBagAttributesOnTheDatastream() throws Exception {
    // The Datastream bag carries free attributes just like the Thing's — its own reference key plus
    // a free scalar.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Thing": {
                    "properties": {
                      "properties": {
                        "type": "object",
                        "properties": { "reference": { "type": "string", "x-core-primaryKey": true } }
                      },
                      "Datastreams": { "type": "array", "items": { "$ref": "#/$defs/Datastream" } }
                    }
                  },
                  "Datastream": {
                    "properties": {
                      "properties": {
                        "type": "object",
                        "properties": {
                          "reference": { "type": "string", "x-core-primaryKey": true },
                          "unit": { "type": "string" }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(new KeyAttribute("reference"), new FreeAttribute("unit", StaJsonType.ANY)),
        spec.staProperties().datastream());
  }

  @Test
  void parseSpec_withALocationPropertiesClass_derivesTheLocationBag() throws Exception {
    // The shape of the published ThingTree structure: the Location's properties is a named class
    // whose reference carries the marker. Without its bag, a Mapping that writes the reference is
    // refused at publish although the port reads it.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Thing": {
                    "properties": {
                      "properties": { "$ref": "#/$defs/ThingProperties" },
                      "Locations": { "type": "array", "items": { "$ref": "#/$defs/Location" } }
                    }
                  },
                  "ThingProperties": {
                    "type": "object",
                    "properties": { "reference": { "type": "string", "x-core-primaryKey": true } }
                  },
                  "Location": {
                    "properties": {
                      "name": { "type": "string" },
                      "properties": { "$ref": "#/$defs/LocationProperties" }
                    }
                  },
                  "LocationProperties": {
                    "type": "object",
                    "properties": {
                      "reference": { "type": "string", "x-core-primaryKey": true },
                      "floor": { "type": "integer" }
                    }
                  }
                }
              }
            }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(new KeyAttribute("reference"), new FreeAttribute("floor", StaJsonType.ANY)),
        spec.staProperties().location());
  }

  @Test
  void parseSpec_withALocationWithoutAPropertiesClass_yieldsAnEmptyLocationBag() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": {
                  "Thing": { "properties": {
                      "properties": { "type": "object", "properties": {
                          "reference": { "type": "string", "x-core-primaryKey": true } } },
                      "Locations": { "type": "array", "items": { "$ref": "#/$defs/Location" } } } },
                  "Location": { "properties": { "name": { "type": "string" } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertTrue(spec.staProperties().location().isEmpty());
  }

  /** A ThingTree structure that models classes under every free field of SensorThings. */
  private static Map<String, Object> modelledThingTree() {
    return json(
        """
        { "configuration": { "port": "ThingTree" },
          "dataStructure": {
            "title": "Thing",
            "$ref": "#/$defs/Thing",
            "$defs": {
              "Thing": { "type": "object", "properties": {
                  "properties": { "$ref": "#/$defs/ThingProperties" },
                  "Datastreams": { "type": "array", "items": { "$ref": "#/$defs/Datastream" } } } },
              "ThingProperties": { "type": "object", "properties": {
                  "reference": { "type": "string", "x-core-primaryKey": true } } },
              "Datastream": { "type": "object", "properties": {
                  "properties": { "type": "object", "properties": {
                      "reference": { "type": "string" } } },
                  "Sensor": { "$ref": "#/$defs/Sensor" },
                  "ObservedProperty": { "$ref": "#/$defs/ObservedProperty" },
                  "Observations": { "type": "array", "items": { "$ref": "#/$defs/Observation" } } } },
              "Sensor": { "type": "object", "properties": {
                  "name": { "type": "string" },
                  "properties": { "$ref": "#/$defs/SensorProperties" } } },
              "SensorProperties": { "type": "object", "properties": {
                  "vendor": { "type": "string" },
                  "calibration": { "$ref": "#/$defs/Calibration" } } },
              "Calibration": { "type": "object", "properties": {
                  "date": { "type": "string", "format": "date" } } },
              "ObservedProperty": { "type": "object", "properties": {
                  "properties": { "type": "object" } } },
              "Observation": { "type": "object", "properties": {
                  "result": {},
                  "resultQuality": { "$ref": "#/$defs/Quality" },
                  "FeatureOfInterest": { "$ref": "#/$defs/FeatureOfInterest" } } },
              "Quality": { "type": "object", "properties": {
                  "accuracy": { "type": "number" } } },
              "FeatureOfInterest": { "type": "object", "properties": {
                  "name": { "type": "string" },
                  "feature": { "$ref": "https://geojson.org/schema/Point.json" },
                  "properties": { "$ref": "#/$defs/FeatureProperties" } } },
              "FeatureProperties": { "type": "object", "properties": {
                  "river": { "type": "string" } } } } } }
        """);
  }

  private static StaTarget optional(String path, StaEntity entity, StaJsonType type) {
    return new StaTarget(path, entity, type, TargetKind.OPTIONAL);
  }

  @Test
  void parseSpec_withClassesUnderTheFreeFields_derivesATargetPerAttribute() throws Exception {
    FrostSinkSpec spec = stage.parseSpec(modelledThingTree(), ctx);

    // The ObservedProperty declares its properties as a free object: no class, no target.
    String observation = "$.Datastreams[].Observations[]";
    assertEquals(
        List.of(
            optional(
                "$.Datastreams[].Sensor.properties.vendor", StaEntity.DATASTREAM, StaJsonType.ANY),
            optional(
                "$.Datastreams[].Sensor.properties.calibration.date",
                StaEntity.DATASTREAM,
                StaJsonType.ANY),
            optional(
                observation + ".resultQuality.accuracy", StaEntity.OBSERVATION, StaJsonType.ANY),
            optional(
                observation + ".FeatureOfInterest.properties.river",
                StaEntity.FEATURE_OF_INTEREST,
                StaJsonType.ANY),
            optional(
                observation + ".FeatureOfInterest.feature",
                StaEntity.FEATURE_OF_INTEREST,
                StaJsonType.RAW_JSON)),
        spec.staProperties().modelled());
  }

  @Test
  void parseSpec_intoTheObservationsPort_derivesTheFreeFieldsOfTheMeasurement() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "Observations" },
              "dataStructure": {
                "title": "Observation",
                "$ref": "#/$defs/Observation",
                "$defs": {
                  "Observation": { "type": "object", "properties": {
                      "result": {},
                      "resultQuality": { "$ref": "#/$defs/Quality" },
                      "parameters": { "type": "object", "properties": {
                          "thingReference": { "type": "string" },
                          "datastreamReference": { "type": "string" } } },
                      "FeatureOfInterest": { "$ref": "#/$defs/FeatureOfInterest" } } },
                  "Quality": { "type": "object", "properties": {
                      "accuracy": { "type": "number" } } },
                  "FeatureOfInterest": { "type": "object", "properties": {
                      "feature": { "$ref": "#/$defs/Site" } } },
                  "Site": { "type": "object", "properties": {
                      "station": { "type": "string" } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    String observation = "$.Datastreams[].Observations[]";
    assertEquals(
        List.of(
            optional(
                observation + ".resultQuality.accuracy", StaEntity.OBSERVATION, StaJsonType.ANY),
            optional(
                observation + ".FeatureOfInterest.feature.station",
                StaEntity.FEATURE_OF_INTEREST,
                StaJsonType.ANY)),
        spec.staProperties().modelled());
  }

  @Test
  void parseSpec_withClassesThatDeclareNoType_expandsThemInsteadOfTakingThemForValues()
      throws Exception {
    // An inline class needs no 'type: object', and an allOf class has no 'properties' of its own.
    // Both are classes; an allOf of plain values is not. The editor reads them the same way.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "title": "Thing",
                "$ref": "#/$defs/Thing",
                "$defs": {
                  "Base": { "type": "object", "properties": { "code": { "type": "string" } } },
                  "Thing": { "type": "object", "properties": {
                      "properties": { "type": "object", "properties": {
                          "reference": { "type": "string" },
                          "site": { "properties": { "floor": { "type": "integer" } } },
                          "area": { "allOf": [ { "$ref": "#/$defs/Base" } ] },
                          "label": { "allOf": [ { "type": "string" }, { "maxLength": 5 } ] } } } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(new KeyAttribute("reference"), new FreeAttribute("label", StaJsonType.ANY)),
        spec.staProperties().thing());
    assertEquals(
        List.of(
            optional("$.properties.site.floor", StaEntity.THING, StaJsonType.ANY),
            optional("$.properties.area.code", StaEntity.THING, StaJsonType.ANY)),
        spec.staProperties().modelled());
  }

  @Test
  void parseSpec_withAFreeFieldThatIsAnAllOfOfValues_addsNoTargetAndDoesNotFail() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "Observations" },
              "dataStructure": {
                "title": "Observation",
                "$ref": "#/$defs/Observation",
                "$defs": {
                  "Observation": { "type": "object", "properties": {
                      "result": {},
                      "resultQuality": { "allOf": [ { "type": "string" } ] },
                      "parameters": { "type": "object", "properties": {
                          "thingReference": { "type": "string" },
                          "datastreamReference": { "type": "string" } } } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertTrue(spec.staProperties().modelled().isEmpty());
  }

  @Test
  void parseSpec_withAClassThatContainsItself_stopsAtTheCycle() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "title": "Thing",
                "$ref": "#/$defs/Thing",
                "$defs": {
                  "Thing": { "type": "object", "properties": {
                      "properties": { "type": "object", "properties": {
                          "reference": { "type": "string" },
                          "part": { "$ref": "#/$defs/Part" } } } } },
                  "Part": { "type": "object", "properties": {
                      "code": { "type": "string" },
                      "parent": { "$ref": "#/$defs/Part" } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(
        List.of(optional("$.properties.part.code", StaEntity.THING, StaJsonType.ANY)),
        spec.staProperties().modelled());
  }

  @Test
  void parseSpec_withAnUnsafeAttributeNameInAModelledClass_isRejected() {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "title": "Thing",
                "$ref": "#/$defs/Thing",
                "$defs": {
                  "Thing": { "type": "object", "properties": {
                      "properties": { "type": "object", "properties": {
                          "reference": { "type": "string" },
                          "site": { "$ref": "#/$defs/Site" } } } } },
                  "Site": { "type": "object", "properties": {
                      "room no": { "type": "string" } } } } } }
            """);

    // The name would become a key of the generated body template.
    assertThrows(FatalAdapterException.class, () -> stage.parseSpec(datasink, ctx));
  }

  @Test
  void missingDatastreamsClassYieldsEmptyDatastreamKeys() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": { "Thing": { "properties": {
                    "properties": { "type": "object", "properties": {
                        "reference": { "type": "string", "x-core-primaryKey": true } } } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(List.of("reference"), spec.staProperties().thingKeys());
    assertTrue(spec.staProperties().datastreamKeys().isEmpty());
  }

  @Test
  void missingDataStructureYieldsNullKeys() throws Exception {
    // A sink may carry no target structure: the source then delivers the port's shape itself.
    FrostSinkSpec spec = stage.parseSpec(Map.of("configuration", Map.of("port", "ThingTree")), ctx);
    assertNull(spec.staProperties());
  }

  @Test
  void parseSpec_withoutAPort_isRejectedAndNamesTheChoice() {
    // There is no default and no fallback to the old derivation from the Mapping: deriving a port
    // would put the decision back where this field took it from.
    FatalAdapterException rejected =
        assertThrows(
            FatalAdapterException.class,
            () -> stage.parseSpec(Map.of("configuration", Map.of()), ctx));

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, rejected.getErrorCode());
    assertTrue(rejected.getMessage().contains("FROST sink node"), rejected.getMessage());
    assertTrue(rejected.getMessage().contains("ThingTree"), rejected.getMessage());
  }

  @Test
  void parseSpec_withAnUnknownPort_isRejected() {
    // The value reaches a processor property, so only the closed set may pass.
    FatalAdapterException rejected =
        assertThrows(
            FatalAdapterException.class,
            () -> stage.parseSpec(Map.of("configuration", Map.of("port", "Everything")), ctx));

    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, rejected.getErrorCode());
    assertTrue(rejected.getMessage().contains("unknown port"), rejected.getMessage());
  }

  @Test
  void parseSpec_withAPort_carriesItIntoTheSpec() throws Exception {
    FrostSinkSpec spec =
        stage.parseSpec(Map.of("configuration", Map.of("port", "Observations")), ctx);

    assertEquals(SinkPort.OBSERVATIONS, spec.port());
  }

  @Test
  void aStructurallyBrokenSchemaFailsThePlanInsteadOfDegradingToNoKeys() {
    // An item-less Datastreams array is a broken schema, not an absent class — degrading it to
    // "no keys" would surface as the misleading "declares no match key" compiler error.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": { "Thing": { "properties": {
                    "properties": { "type": "object", "properties": {
                        "reference": { "type": "string", "x-core-primaryKey": true } } },
                    "Datastreams": { "type": "array" } } } } } }
            """);

    FatalAdapterException ex =
        assertThrows(FatalAdapterException.class, () -> stage.parseSpec(datasink, ctx));
    assertTrue(
        ex.getMessage().contains("Datastreams") && ex.getMessage().contains("no items"),
        "the broken-schema error must name the offending array property, was: " + ex.getMessage());
  }

  @Test
  void aMultiClassStructureWithoutARootDesignatorSurfacesTheActionableErrorCode() {
    // The inlined form of a multi-element DataStructure released without a root designation (only
    // reachable via the raw import API — the editor enforces a root at release). The failure must
    // carry the dedicated code whose safe external message names the remedy, not INVALID_PAYLOAD's
    // generic "Validation failed" — the saga error is all the modeller gets to see.
    Map<String, Object> datasink =
        json(
            """
            { "configuration": { "port": "ThingTree" },
              "dataStructure": {
                "title": "OrderStructure",
                "$defs": {
                  "Customer": { "properties": { "id": { "type": "string" } } },
                  "Address":  { "properties": { "city": { "type": "string" } } } } } }
            """);

    FatalAdapterException ex =
        assertThrows(FatalAdapterException.class, () -> stage.parseSpec(datasink, ctx));
    assertEquals(AdapterErrorCode.UNRESOLVABLE_DATA_STRUCTURE, ex.getErrorCode());
    assertTrue(
        ex.getSafeExternalMessage().contains("designate a root element"),
        "the external message must carry the remedy, was: " + ex.getSafeExternalMessage());
  }
}
