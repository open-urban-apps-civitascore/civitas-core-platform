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
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
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
        { "dataStructure": {
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
            { "dataStructure": {
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
            { "dataStructure": {
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
            { "dataStructure": {
                "$id": "urn:core:platform:civitas:datastructure:common:ALittleThing:91zjftfn1i:1.0.0",
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
    // A bag with the match key + a free scalar + a free object: scalar → ANY, object → RAW_JSON,
    // only the marked attribute is the key. Declaration order is preserved.
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
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
            new FreeAttribute("meta", StaJsonType.RAW_JSON)),
        spec.staProperties().thing());
  }

  @Test
  void aNonScalarMarkedAttributeIsNotTheKeyAndStaysRawJson() throws Exception {
    // A {id} marker on a non-scalar (array/$ref) cannot back a match key — it must fall through to
    // the scalar 'reference' fallback for the key, while the marked non-scalar stays a free
    // RAW_JSON attribute (a marked object never quietly becomes a STRING key). array and $ref
    // attributes both render RAW_JSON.
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
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
        List.of(
            new FreeAttribute("tags", StaJsonType.RAW_JSON),
            new KeyAttribute("reference"),
            new FreeAttribute("region", StaJsonType.RAW_JSON)),
        spec.staProperties().thing());
  }

  @Test
  void derivesFreeBagAttributesOnTheDatastream() throws Exception {
    // The Datastream bag carries free attributes just like the Thing's — its own reference key plus
    // a free scalar.
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
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
  void missingDatastreamsClassYieldsEmptyDatastreamKeys() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
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
  void missingDataStructureYieldsNullKeysForPassthrough() throws Exception {
    FrostSinkSpec spec = stage.parseSpec(Map.of("configuration", Map.of()), ctx);
    assertNull(spec.staProperties());
  }

  @Test
  void aStructurallyBrokenSchemaFailsThePlanInsteadOfDegradingToNoKeys() {
    // An item-less Datastreams array is a broken schema, not an absent class — degrading it to
    // "no keys" would surface as the misleading "declares no match key" compiler error.
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
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
            { "dataStructure": {
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
