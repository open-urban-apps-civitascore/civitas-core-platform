/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.frost;

import static org.junit.jupiter.api.Assertions.*;

import com.civitas.configadapter.frost.StaResourcePathParser.EntityType;
import com.civitas.configadapter.frost.StaResourcePathParser.ResourceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive tests for {@link StaResourcePathParser} covering all OGC SensorThings API paths.
 *
 * <p>Tests are organized according to OGC 18-088 (SensorThings API Part 1: Sensing) specification
 * sections and FROST-Server extensions.
 */
@DisplayName("STA Resource Path Parser")
class StaResourcePathParserTest {

  // ==========================================================================
  // ENTITY TYPE RESOLUTION
  // ==========================================================================

  @Nested
  @DisplayName("EntityType.fromPathSegment")
  class EntityTypeResolution {

    @Test
    @DisplayName("resolves Thing from plural and singular forms")
    void resolvesThingFromPluralAndSingularForms() {
      assertEquals(EntityType.THING, EntityType.fromPathSegment("Things"));
      assertEquals(EntityType.THING, EntityType.fromPathSegment("Thing"));
      assertEquals(EntityType.THING, EntityType.fromPathSegment("things"));
      assertEquals(EntityType.THING, EntityType.fromPathSegment("THINGS"));
    }

    @Test
    @DisplayName("resolves Location from plural and singular forms")
    void resolvesLocationFromPluralAndSingularForms() {
      assertEquals(EntityType.LOCATION, EntityType.fromPathSegment("Locations"));
      assertEquals(EntityType.LOCATION, EntityType.fromPathSegment("Location"));
      assertEquals(EntityType.LOCATION, EntityType.fromPathSegment("locations"));
    }

    @Test
    @DisplayName("resolves HistoricalLocation from plural and singular forms")
    void resolvesHistoricalLocationFromPluralAndSingularForms() {
      assertEquals(
          EntityType.HISTORICAL_LOCATION, EntityType.fromPathSegment("HistoricalLocations"));
      assertEquals(
          EntityType.HISTORICAL_LOCATION, EntityType.fromPathSegment("HistoricalLocation"));
      assertEquals(
          EntityType.HISTORICAL_LOCATION, EntityType.fromPathSegment("historicallocations"));
    }

    @Test
    @DisplayName("resolves Datastream from plural and singular forms")
    void resolvesDatastreamFromPluralAndSingularForms() {
      assertEquals(EntityType.DATASTREAM, EntityType.fromPathSegment("Datastreams"));
      assertEquals(EntityType.DATASTREAM, EntityType.fromPathSegment("Datastream"));
      assertEquals(EntityType.DATASTREAM, EntityType.fromPathSegment("datastreams"));
    }

    @Test
    @DisplayName("resolves Sensor from plural and singular forms")
    void resolvesSensorFromPluralAndSingularForms() {
      assertEquals(EntityType.SENSOR, EntityType.fromPathSegment("Sensors"));
      assertEquals(EntityType.SENSOR, EntityType.fromPathSegment("Sensor"));
      assertEquals(EntityType.SENSOR, EntityType.fromPathSegment("sensors"));
    }

    @Test
    @DisplayName("resolves ObservedProperty from plural and singular forms")
    void resolvesObservedPropertyFromPluralAndSingularForms() {
      assertEquals(EntityType.OBSERVED_PROPERTY, EntityType.fromPathSegment("ObservedProperties"));
      assertEquals(EntityType.OBSERVED_PROPERTY, EntityType.fromPathSegment("ObservedProperty"));
      assertEquals(EntityType.OBSERVED_PROPERTY, EntityType.fromPathSegment("observedproperties"));
    }

    @Test
    @DisplayName("resolves Observation from plural and singular forms")
    void resolvesObservationFromPluralAndSingularForms() {
      assertEquals(EntityType.OBSERVATION, EntityType.fromPathSegment("Observations"));
      assertEquals(EntityType.OBSERVATION, EntityType.fromPathSegment("Observation"));
      assertEquals(EntityType.OBSERVATION, EntityType.fromPathSegment("observations"));
    }

    @Test
    @DisplayName("resolves FeatureOfInterest from plural and singular forms")
    void resolvesFeatureOfInterestFromPluralAndSingularForms() {
      assertEquals(
          EntityType.FEATURE_OF_INTEREST, EntityType.fromPathSegment("FeaturesOfInterest"));
      assertEquals(EntityType.FEATURE_OF_INTEREST, EntityType.fromPathSegment("FeatureOfInterest"));
      assertEquals(
          EntityType.FEATURE_OF_INTEREST, EntityType.fromPathSegment("featuresofinterest"));
    }

    @Test
    @DisplayName("resolves Project (FROST extension) from plural and singular forms")
    void resolvesProjectFromPluralAndSingularForms() {
      assertEquals(EntityType.PROJECT, EntityType.fromPathSegment("Projects"));
      assertEquals(EntityType.PROJECT, EntityType.fromPathSegment("Project"));
      assertEquals(EntityType.PROJECT, EntityType.fromPathSegment("projects"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "Unknown", "InvalidType", "123", "Thing s"})
    @DisplayName("returns null for invalid or unrecognized segments")
    void returnsNullForInvalidSegments(String segment) {
      assertNull(EntityType.fromPathSegment(segment));
    }
  }

  // ==========================================================================
  // SIMPLE COLLECTION PATHS (CREATE operations)
  // ==========================================================================

  @Nested
  @DisplayName("Simple Collection Paths")
  class SimpleCollectionPaths {

    @Test
    @DisplayName("parses Things collection")
    void parsesThingsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Things");

      assertEquals(EntityType.THING, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
      assertFalse(result.hasId());
    }

    @Test
    @DisplayName("parses Locations collection")
    void parsesLocationsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Locations");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Sensors collection")
    void parsesSensorsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Sensors");

      assertEquals(EntityType.SENSOR, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses ObservedProperties collection")
    void parsesObservedPropertiesCollection() {
      ResourceInfo result = StaResourcePathParser.parse("ObservedProperties");

      assertEquals(EntityType.OBSERVED_PROPERTY, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Datastreams collection")
    void parsesDatastreamsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Observations collection")
    void parsesObservationsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Observations");

      assertEquals(EntityType.OBSERVATION, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses FeaturesOfInterest collection")
    void parsesFeaturesOfInterestCollection() {
      ResourceInfo result = StaResourcePathParser.parse("FeaturesOfInterest");

      assertEquals(EntityType.FEATURE_OF_INTEREST, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses HistoricalLocations collection")
    void parsesHistoricalLocationsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("HistoricalLocations");

      assertEquals(EntityType.HISTORICAL_LOCATION, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Projects collection (FROST extension)")
    void parsesProjectsCollection() {
      ResourceInfo result = StaResourcePathParser.parse("Projects");

      assertEquals(EntityType.PROJECT, result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }
  }

  // ==========================================================================
  // ENTITY PATHS WITH ID (UPDATE/DELETE operations)
  // ==========================================================================

  @Nested
  @DisplayName("Entity Paths with ID")
  class EntityPathsWithId {

    @Test
    @DisplayName("parses Things with numeric ID using slash notation")
    void parsesThingsWithNumericIdSlashNotation() {
      ResourceInfo result = StaResourcePathParser.parse("Things/123");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("123", result.id());
      assertNull(result.parentPath());
      assertTrue(result.hasId());
    }

    @Test
    @DisplayName("parses Things with numeric ID using parentheses notation")
    void parsesThingsWithNumericIdParenthesesNotation() {
      ResourceInfo result = StaResourcePathParser.parse("Things(123)");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("123", result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Things with string ID using quoted parentheses notation")
    void parsesThingsWithStringIdQuotedParenthesesNotation() {
      ResourceInfo result = StaResourcePathParser.parse("Things('my-thing-uuid')");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("my-thing-uuid", result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Locations with ID")
    void parsesLocationsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Locations/456");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertEquals("456", result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("parses Sensors with ID")
    void parsesSensorsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Sensors/789");

      assertEquals(EntityType.SENSOR, result.entityType());
      assertEquals("789", result.id());
    }

    @Test
    @DisplayName("parses Datastreams with ID")
    void parsesDatastreamsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams/101");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertEquals("101", result.id());
    }

    @Test
    @DisplayName("parses ObservedProperties with ID")
    void parsesObservedPropertiesWithId() {
      ResourceInfo result = StaResourcePathParser.parse("ObservedProperties/202");

      assertEquals(EntityType.OBSERVED_PROPERTY, result.entityType());
      assertEquals("202", result.id());
    }

    @Test
    @DisplayName("parses Observations with ID")
    void parsesObservationsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Observations/303");

      assertEquals(EntityType.OBSERVATION, result.entityType());
      assertEquals("303", result.id());
    }

    @Test
    @DisplayName("parses FeaturesOfInterest with ID")
    void parsesFeaturesOfInterestWithId() {
      ResourceInfo result = StaResourcePathParser.parse("FeaturesOfInterest/404");

      assertEquals(EntityType.FEATURE_OF_INTEREST, result.entityType());
      assertEquals("404", result.id());
    }

    @Test
    @DisplayName("parses HistoricalLocations with ID")
    void parsesHistoricalLocationsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("HistoricalLocations/505");

      assertEquals(EntityType.HISTORICAL_LOCATION, result.entityType());
      assertEquals("505", result.id());
    }

    @Test
    @DisplayName("parses Projects with ID (FROST extension)")
    void parsesProjectsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/606");

      assertEquals(EntityType.PROJECT, result.entityType());
      assertEquals("606", result.id());
    }

    @Test
    @DisplayName("parses UUID string IDs")
    void parsesUuidStringIds() {
      ResourceInfo result =
          StaResourcePathParser.parse("Things/550e8400-e29b-41d4-a716-446655440000");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("550e8400-e29b-41d4-a716-446655440000", result.id());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - Thing relationships (OGC 18-088 Section 8.2.1)
  // ==========================================================================

  @Nested
  @DisplayName("Thing Navigation Paths")
  class ThingNavigationPaths {

    @Test
    @DisplayName("parses Things/{id}/Locations")
    void parsesThingsLocations() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/Locations");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Things(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things({id})/Locations")
    void parsesThingsLocationsParenthesesNotation() {
      ResourceInfo result = StaResourcePathParser.parse("Things(1)/Locations");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Things(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things/{id}/HistoricalLocations")
    void parsesThingsHistoricalLocations() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/HistoricalLocations");

      assertEquals(EntityType.HISTORICAL_LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Things(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things/{id}/Datastreams")
    void parsesThingsDatastreams() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("Things(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things/{id}/Locations/{locId}")
    void parsesThingsLocationsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/Locations/2");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertEquals("2", result.id());
      assertEquals("Things(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things/{id}/Datastreams/{dsId}")
    void parsesThingsDatastreamsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/Datastreams/3");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertEquals("3", result.id());
      assertEquals("Things(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - Location relationships (OGC 18-088 Section 8.2.2)
  // ==========================================================================

  @Nested
  @DisplayName("Location Navigation Paths")
  class LocationNavigationPaths {

    @Test
    @DisplayName("parses Locations/{id}/Things")
    void parsesLocationsThings() {
      ResourceInfo result = StaResourcePathParser.parse("Locations/1/Things");

      assertEquals(EntityType.THING, result.entityType());
      assertNull(result.id());
      assertEquals("Locations(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Locations/{id}/HistoricalLocations")
    void parsesLocationsHistoricalLocations() {
      ResourceInfo result = StaResourcePathParser.parse("Locations/1/HistoricalLocations");

      assertEquals(EntityType.HISTORICAL_LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Locations(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - Datastream relationships (OGC 18-088 Section 8.2.4)
  // ==========================================================================

  @Nested
  @DisplayName("Datastream Navigation Paths")
  class DatastreamNavigationPaths {

    @Test
    @DisplayName("parses Datastreams/{id}/Thing")
    void parsesDatastreamsThing() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams/1/Thing");

      assertEquals(EntityType.THING, result.entityType());
      assertNull(result.id());
      assertEquals("Datastreams(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Datastreams/{id}/Sensor")
    void parsesDatastreamsSensor() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams/1/Sensor");

      assertEquals(EntityType.SENSOR, result.entityType());
      assertNull(result.id());
      assertEquals("Datastreams(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Datastreams/{id}/ObservedProperty")
    void parsesDatastreamsObservedProperty() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams/1/ObservedProperty");

      assertEquals(EntityType.OBSERVED_PROPERTY, result.entityType());
      assertNull(result.id());
      assertEquals("Datastreams(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Datastreams/{id}/Observations")
    void parsesDatastreamsObservations() {
      ResourceInfo result = StaResourcePathParser.parse("Datastreams/1/Observations");

      assertEquals(EntityType.OBSERVATION, result.entityType());
      assertNull(result.id());
      assertEquals("Datastreams(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - Sensor relationships (OGC 18-088 Section 8.2.5)
  // ==========================================================================

  @Nested
  @DisplayName("Sensor Navigation Paths")
  class SensorNavigationPaths {

    @Test
    @DisplayName("parses Sensors/{id}/Datastreams")
    void parsesSensorsDatastreams() {
      ResourceInfo result = StaResourcePathParser.parse("Sensors/1/Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("Sensors(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - ObservedProperty relationships (OGC 18-088 Section 8.2.6)
  // ==========================================================================

  @Nested
  @DisplayName("ObservedProperty Navigation Paths")
  class ObservedPropertyNavigationPaths {

    @Test
    @DisplayName("parses ObservedProperties/{id}/Datastreams")
    void parsesObservedPropertiesDatastreams() {
      ResourceInfo result = StaResourcePathParser.parse("ObservedProperties/1/Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("ObservedProperties(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - Observation relationships (OGC 18-088 Section 8.2.7)
  // ==========================================================================

  @Nested
  @DisplayName("Observation Navigation Paths")
  class ObservationNavigationPaths {

    @Test
    @DisplayName("parses Observations/{id}/Datastream")
    void parsesObservationsDatastream() {
      ResourceInfo result = StaResourcePathParser.parse("Observations/1/Datastream");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("Observations(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Observations/{id}/FeatureOfInterest")
    void parsesObservationsFeatureOfInterest() {
      ResourceInfo result = StaResourcePathParser.parse("Observations/1/FeatureOfInterest");

      assertEquals(EntityType.FEATURE_OF_INTEREST, result.entityType());
      assertNull(result.id());
      assertEquals("Observations(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // NAVIGATION PATHS - FeatureOfInterest relationships (OGC 18-088 Section 8.2.8)
  // ==========================================================================

  @Nested
  @DisplayName("FeatureOfInterest Navigation Paths")
  class FeatureOfInterestNavigationPaths {

    @Test
    @DisplayName("parses FeaturesOfInterest/{id}/Observations")
    void parsesFeaturesOfInterestObservations() {
      ResourceInfo result = StaResourcePathParser.parse("FeaturesOfInterest/1/Observations");

      assertEquals(EntityType.OBSERVATION, result.entityType());
      assertNull(result.id());
      assertEquals("FeaturesOfInterest(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // FROST PROJECTS EXTENSION
  // ==========================================================================

  @Nested
  @DisplayName("FROST Projects Extension Paths")
  class FrostProjectsExtensionPaths {

    @Test
    @DisplayName("parses Projects/{id}/Things")
    void parsesProjectsThings() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Things");

      assertEquals(EntityType.THING, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/Locations")
    void parsesProjectsLocations() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Locations");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/Sensors")
    void parsesProjectsSensors() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Sensors");

      assertEquals(EntityType.SENSOR, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/ObservedProperties")
    void parsesProjectsObservedProperties() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/ObservedProperties");

      assertEquals(EntityType.OBSERVED_PROPERTY, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/Datastreams")
    void parsesProjectsDatastreams() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/FeaturesOfInterest")
    void parsesProjectsFeaturesOfInterest() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/FeaturesOfInterest");

      assertEquals(EntityType.FEATURE_OF_INTEREST, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects with string ID")
    void parsesProjectsWithStringId() {
      ResourceInfo result = StaResourcePathParser.parse("Projects('my-project')/Things");

      assertEquals(EntityType.THING, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(my-project)", result.parentPath());
    }

    @Test
    @DisplayName("parses Projects/{id}/Things/{thingId}")
    void parsesProjectsThingsWithId() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Things/2");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("2", result.id());
      assertEquals("Projects(1)", result.parentPath());
    }
  }

  // ==========================================================================
  // DEEP NAVIGATION PATHS
  // ==========================================================================

  @Nested
  @DisplayName("Deep Navigation Paths")
  class DeepNavigationPaths {

    @Test
    @DisplayName("parses Projects/{id}/Things/{thingId}/Datastreams")
    void parsesProjectsThingsDatastreams() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Things/2/Datastreams");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertNull(result.id());
      assertEquals("Projects(1)/Things(2)", result.parentPath());
    }

    @Test
    @DisplayName("parses Things/{id}/Datastreams/{dsId}/Observations")
    void parsesThingsDatastreamsObservations() {
      ResourceInfo result = StaResourcePathParser.parse("Things/1/Datastreams/2/Observations");

      assertEquals(EntityType.OBSERVATION, result.entityType());
      assertNull(result.id());
      assertEquals("Things(1)/Datastreams(2)", result.parentPath());
    }

    @Test
    @DisplayName("parses deep path with specific entity ID at the end")
    void parsesDeepPathWithEntityId() {
      ResourceInfo result = StaResourcePathParser.parse("Projects/1/Things/2/Datastreams/3");

      assertEquals(EntityType.DATASTREAM, result.entityType());
      assertEquals("3", result.id());
      assertEquals("Projects(1)/Things(2)", result.parentPath());
    }
  }

  // ==========================================================================
  // API PATH BUILDING
  // ==========================================================================

  @Nested
  @DisplayName("API Path Building")
  class ApiPathBuilding {

    @Test
    @DisplayName("builds collection path for simple entity")
    void buildsCollectionPathForSimpleEntity() {
      ResourceInfo info = StaResourcePathParser.parse("Things");
      String path = info.collectionPath();

      assertEquals("Things", path);
    }

    @Test
    @DisplayName("builds collection path for entity with ID")
    void buildsCollectionPathForEntityWithId() {
      ResourceInfo info = StaResourcePathParser.parse("Things/123");
      String path = info.collectionPath();

      assertEquals("Things", path);
    }

    @Test
    @DisplayName("builds collection path for nested entity")
    void buildsCollectionPathForNestedEntity() {
      ResourceInfo info = StaResourcePathParser.parse("Projects/1/Things");
      String path = info.collectionPath();

      assertEquals("Projects(1)/Things", path);
    }
  }

  // ==========================================================================
  // EDGE CASES AND ERROR HANDLING
  // ==========================================================================

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesAndErrorHandling {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("throws exception for null or empty input")
    void throwsExceptionForNullOrEmptyInput(String input) {
      assertThrows(IllegalArgumentException.class, () -> StaResourcePathParser.parse(input));
    }

    @Test
    @DisplayName("handles leading and trailing slashes")
    void handlesLeadingAndTrailingSlashes() {
      ResourceInfo result = StaResourcePathParser.parse("/Things/123/");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("123", result.id());
    }

    @Test
    @DisplayName("handles multiple consecutive slashes")
    void handlesMultipleConsecutiveSlashes() {
      ResourceInfo result = StaResourcePathParser.parse("Things//123");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("123", result.id());
    }

    @Test
    @DisplayName("returns null entity type for unrecognized path")
    void returnsNullEntityTypeForUnrecognizedPath() {
      ResourceInfo result = StaResourcePathParser.parse("UnknownEntity/123");

      assertNull(result.entityType());
      assertNull(result.id());
      assertNull(result.parentPath());
    }

    @Test
    @DisplayName("handles whitespace in path segments")
    void handlesWhitespaceInPathSegments() {
      ResourceInfo result = StaResourcePathParser.parse(" Things / 123 ");

      assertEquals(EntityType.THING, result.entityType());
      assertEquals("123", result.id());
    }

    @Test
    @DisplayName("handles case variations in entity types")
    void handlesCaseVariationsInEntityTypes() {
      ResourceInfo result = StaResourcePathParser.parse("THINGS/123/LOCATIONS");

      assertEquals(EntityType.LOCATION, result.entityType());
      assertNull(result.id());
      assertEquals("Things(123)", result.parentPath());
    }
  }

  // ==========================================================================
  // RESOURCE INFO RECORD
  // ==========================================================================

  @Nested
  @DisplayName("ResourceInfo Record")
  class ResourceInfoRecord {

    @Test
    @DisplayName("hasId returns true when ID is present")
    void hasIdReturnsTrueWhenIdPresent() {
      ResourceInfo info = new ResourceInfo(EntityType.THING, "123", null);
      assertTrue(info.hasId());
    }

    @Test
    @DisplayName("hasId returns false when ID is null")
    void hasIdReturnsFalseWhenIdNull() {
      ResourceInfo info = new ResourceInfo(EntityType.THING, null, null);
      assertFalse(info.hasId());
    }

    @Test
    @DisplayName("hasId returns false when ID is blank")
    void hasIdReturnsFalseWhenIdBlank() {
      ResourceInfo info = new ResourceInfo(EntityType.THING, "   ", null);
      assertFalse(info.hasId());
    }
  }
}
