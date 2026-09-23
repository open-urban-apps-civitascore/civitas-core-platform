package de.civitascore.portal.frost;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the published structures.
 *
 * <p>The rendered files are in the repository, so a change to the declaration shows up as a diff a
 * reviewer reads instead of as a change of behaviour nobody sees. This test renders the declaration
 * again and compares. Run it with {@code -Dfrost.structures.write=true} to write the files after a
 * deliberate change to the declaration.
 */
@DisplayName("FROST port structures")
class PortStructureGeneratorTest {

  private static final Path RESOURCES = Path.of("src", "main", "resources");
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("Renders the same document that is in the repository")
  void isReproducible() throws Exception {
    boolean write = Boolean.getBoolean("frost.structures.write");
    List<String> differences = new ArrayList<>();

    for (PortStructure structure : PortStructureCatalog.all()) {
      Path file = RESOURCES.resolve(PortStructureGenerator.resourcePath(structure));
      String rendered = PortStructureGenerator.render(structure);
      if (write) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, rendered, StandardCharsets.UTF_8);
        continue;
      }
      if (!Files.exists(file)) {
        differences.add(file + " is missing");
      } else if (!Files.readString(file, StandardCharsets.UTF_8).equals(rendered)) {
        differences.add(file + " differs from the declaration");
      }
    }

    assertThat(differences)
        .as("re-run with -Dfrost.structures.write=true to write the declared structures")
        .isEmpty();
  }

  @Test
  @DisplayName("Publishes a structure for every port")
  void coversEveryPort() {
    assertThat(PortStructureCatalog.all())
        .extracting(PortStructure::port)
        .containsExactly("Things", "Observations", "ThingTree");
  }

  @Test
  @DisplayName("Holds only writable fields")
  void holdsOnlyWritableFields() {
    for (PortStructure structure : PortStructureCatalog.all()) {
      String rendered = PortStructureGenerator.render(structure);
      // A Mapping author cannot set these, and a structure that offers them would invite a mapping
      // FROST rejects.
      assertThat(rendered)
          .as("%s must not offer a link or the server-assigned identifier", structure.port())
          .doesNotContain("@iot.id", "@iot.selfLink", "@iot.navigationLink");
    }
  }

  @Test
  @DisplayName("Puts the reference of a Thing in its properties bag, as the key")
  void carriesTheReferenceBlock() throws Exception {
    JsonNode things = model("Things");
    JsonNode reference =
        things.path("$defs").path("ThingProperties").path("properties").path("reference");

    assertThat(reference.path("type").asText()).isEqualTo("string");
    // The reference is the key the port upserts on, and the editor marks it as such.
    assertThat(reference.path("x-core-primaryKey").asBoolean()).isTrue();
    assertThat(things.path("$defs").path("ThingProperties").path("required"))
        .singleElement()
        .satisfies(node -> assertThat(node.asText()).isEqualTo("reference"));
  }

  @Test
  @DisplayName("Shows the external reference of a measurement's Datastream, not its identifier")
  void resolvesTheParentByReference() throws Exception {
    JsonNode parameters =
        model("Observations").path("$defs").path("ObservationParameters").path("properties");

    assertThat(parameters.has("thingReference")).isTrue();
    assertThat(parameters.has("datastreamReference")).isTrue();
    assertThat(parameters.has("Datastream")).isFalse();
  }

  @Test
  @DisplayName("Expresses the six entities of the ThingTree chain and their references")
  void expressesTheChain() throws Exception {
    JsonNode defs = model("ThingTree").path("$defs");

    assertThat(defs.propertyNames())
        .contains("Thing", "Location", "Datastream", "Sensor", "ObservedProperty", "Observation");
    // The nesting is what a flat structure cannot express: which reference belongs to which entity.
    assertThat(defs.path("Thing").path("properties").path("Datastreams").path("type").asText())
        .isEqualTo("array");
    assertThat(
            defs.path("Thing")
                .path("properties")
                .path("Datastreams")
                .path("items")
                .path("$ref")
                .asText())
        .isEqualTo("#/$defs/Datastream");
    assertThat(defs.path("Datastream").path("properties").path("properties").path("$ref").asText())
        .isEqualTo("#/$defs/DatastreamProperties");
    JsonNode reference = defs.path("DatastreamProperties").path("properties").path("reference");
    assertThat(reference.isMissingNode()).isFalse();
    assertThat(reference.path("x-core-primaryKey").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Covers every target path the deploy engine accepts today")
  void coversTheTargetPathsOfToday() throws Exception {
    // The paths of StaTargetCatalog, which both catalogues hold today. A path missing here would
    // break a Pipeline that maps it, so the catalogues cannot go until every one is covered.
    List<String> paths =
        List.of(
            "$.name",
            "$.description",
            "$.Locations[].name",
            "$.Locations[].description",
            "$.Locations[].encodingType",
            "$.Locations[].location",
            "$.Locations[].properties",
            "$.Datastreams[].name",
            "$.Datastreams[].description",
            "$.Datastreams[].observationType",
            "$.Datastreams[].unitOfMeasurement.name",
            "$.Datastreams[].unitOfMeasurement.symbol",
            "$.Datastreams[].unitOfMeasurement.definition",
            "$.Datastreams[].Sensor.name",
            "$.Datastreams[].Sensor.description",
            "$.Datastreams[].Sensor.encodingType",
            "$.Datastreams[].Sensor.metadata",
            "$.Datastreams[].Sensor.properties",
            "$.Datastreams[].ObservedProperty.name",
            "$.Datastreams[].ObservedProperty.definition",
            "$.Datastreams[].ObservedProperty.description",
            "$.Datastreams[].ObservedProperty.properties",
            "$.Datastreams[].Observations[].result",
            "$.Datastreams[].Observations[].phenomenonTime",
            "$.Datastreams[].Observations[].resultTime",
            "$.Datastreams[].Observations[].resultQuality",
            "$.Datastreams[].Observations[].validTime",
            "$.Datastreams[].Observations[].FeatureOfInterest.name",
            "$.Datastreams[].Observations[].FeatureOfInterest.description",
            "$.Datastreams[].Observations[].FeatureOfInterest.encodingType",
            "$.Datastreams[].Observations[].FeatureOfInterest.feature",
            "$.Datastreams[].Observations[].FeatureOfInterest.properties");

    assertThat(PortStructurePaths.of(model("ThingTree"))).containsAll(paths);
  }

  private JsonNode model(String port) throws Exception {
    PortStructure structure = PortStructureCatalog.of(port).orElseThrow();
    return mapper.readTree(PortStructureGenerator.render(structure));
  }
}
