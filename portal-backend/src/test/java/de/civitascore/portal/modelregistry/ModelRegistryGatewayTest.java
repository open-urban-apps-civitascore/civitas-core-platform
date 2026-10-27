/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.CopyVersionCommand;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ModelMissingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelRegistryGateway")
class ModelRegistryGatewayTest {

  @Mock private ModelForge modelForge;

  @InjectMocks private ModelRegistryGateway gateway;

  @BeforeEach
  void injectRealObjectMapper() {
    ReflectionTestUtils.setField(gateway, "objectMapper", new ObjectMapper());
  }

  @Nested
  @DisplayName("the participating closure")
  class ParticipatingClosure {

    private static final String PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:a8ff27eb-c6c3-8136-9fac-596b9b92356a:1.0.0";
    private static final String STRUCTURE_URN =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:1.0.0";
    private static final String GHOST_URN =
        "urn:core:platform:civitas:element:common:158a41e4-f47b-8e87-bc6f-d6573a02ab99:1.0.0";

    @Test
    @DisplayName("asks the registry for a bounded walk and passes the depth through")
    void asksForABoundedWalk() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(new ArtifactId(PIPELINE_URN), List.of(), List.of(), false));

      gateway.closure(PIPELINE_URN, 6);

      ArgumentCaptor<DependencyQuery> query = ArgumentCaptor.forClass(DependencyQuery.class);
      verify(modelForge).closure(query.capture());
      assertThat(query.getValue().maxDepth()).isEqualTo(6);
      assertThat(query.getValue().artifactId().value()).isEqualTo(PIPELINE_URN);
    }

    @Test
    @DisplayName("passes on that the depth bound stopped the walk short")
    void carriesTruncation() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(
                  new ArtifactId(PIPELINE_URN),
                  List.of(new ArtifactId(STRUCTURE_URN)),
                  List.of(),
                  true));

      assertThat(gateway.closure(PIPELINE_URN, 10).truncated()).isTrue();
    }

    @Test
    @DisplayName("reports what the flow reaches and what of it is missing")
    void reportsReachedAndMissing() {
      when(modelForge.closure(any()))
          .thenReturn(
              new DependencyClosureView(
                  new ArtifactId(PIPELINE_URN),
                  List.of(new ArtifactId(STRUCTURE_URN), new ArtifactId(GHOST_URN)),
                  List.of(new ArtifactId(GHOST_URN)),
                  false));

      ModelRegistryGateway.ArtifactClosure closure = gateway.closure(PIPELINE_URN, 10);

      assertThat(closure.artifacts()).containsExactly(STRUCTURE_URN, GHOST_URN);
      assertThat(closure.unresolved()).containsExactly(GHOST_URN);
    }

    @Test
    @DisplayName("walks nothing for a blank artifact or a non-positive depth")
    void nothingToWalk() {
      assertThat(gateway.closure(null, 10).artifacts()).isEmpty();
      assertThat(gateway.closure("  ", 10).artifacts()).isEmpty();
      assertThat(gateway.closure(PIPELINE_URN, 0).artifacts()).isEmpty();
      verify(modelForge, never()).closure(any());
    }
  }

  @Nested
  @DisplayName("the direct dependencies")
  class DirectDependencies {

    private static final String PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:a8ff27eb-c6c3-8136-9fac-596b9b92356a:1.0.0";
    private static final String SOURCE_URN =
        "urn:core:platform:civitas:datasource:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:1.1.0";
    private static final String MAPPING_URN =
        "urn:core:platform:civitas:mapping:common:158a41e4-f47b-8e87-bc6f-d6573a02ab99:1.0.0";

    private DependencyGraphView.Edge edge(String target) {
      return new DependencyGraphView.Edge(
          new ArtifactId(PIPELINE_URN), new ArtifactId(target), "depends-on");
    }

    @Test
    @DisplayName("are the edge targets, without the queried artifact and without duplicates")
    void edgeTargetsWithoutTheQueriedArtifact() {
      when(modelForge.dependencies(any()))
          .thenReturn(
              new DependencyGraphView(
                  List.of(
                      new DependencyGraphView.Node(new ArtifactId(PIPELINE_URN), "pipeline"),
                      new DependencyGraphView.Node(new ArtifactId(SOURCE_URN), "source"),
                      new DependencyGraphView.Node(new ArtifactId(MAPPING_URN), "mapping")),
                  List.of(edge(SOURCE_URN), edge(MAPPING_URN), edge(SOURCE_URN))));

      assertThat(gateway.dependencyUrns(PIPELINE_URN)).containsExactly(SOURCE_URN, MAPPING_URN);
      ArgumentCaptor<DependencyQuery> query = ArgumentCaptor.forClass(DependencyQuery.class);
      verify(modelForge).dependencies(query.capture());
      assertThat(query.getValue().artifactId().value()).isEqualTo(PIPELINE_URN);
      assertThat(query.getValue().maxDepth()).isNull();
    }

    @Test
    @DisplayName("of a blank artifact are empty, without asking Model Forge")
    void nothingForABlankArtifact() {
      assertThat(gateway.dependencyUrns(null)).isEmpty();
      assertThat(gateway.dependencyUrns("  ")).isEmpty();
      verify(modelForge, never()).dependencies(any());
    }

    @Test
    @DisplayName("of one type are the pins of those nodes, once each, with the id of their URN")
    void pinsOfOneType() {
      when(modelForge.dependencies(any()))
          .thenReturn(
              new DependencyGraphView(
                  List.of(
                      new DependencyGraphView.Node(new ArtifactId(PIPELINE_URN), "pipeline"),
                      new DependencyGraphView.Node(new ArtifactId(SOURCE_URN), "source"),
                      new DependencyGraphView.Node(new ArtifactId(MAPPING_URN), "mapping"),
                      new DependencyGraphView.Node(new ArtifactId(MAPPING_URN), "mapping")),
                  List.of(edge(SOURCE_URN), edge(MAPPING_URN))));

      List<ModelRegistryGateway.ModelPin> pins =
          gateway.dependencyPinsOfType(PIPELINE_URN, "mapping");

      assertThat(pins)
          .containsExactly(
              new ModelRegistryGateway.ModelPin(
                  "urn:core:platform:civitas:mapping:common:158a41e4-f47b-8e87-bc6f-d6573a02ab99",
                  MAPPING_URN,
                  "1.0.0"));
      assertThat(pins.getFirst().id())
          .isEqualTo(UUID.fromString("158a41e4-f47b-8e87-bc6f-d6573a02ab99"));
    }
  }

  @Nested
  @DisplayName("storing a model under a host id")
  class StoringUnderAHostId {

    private static final UUID HOST_ID = UUID.fromString("0b6f1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
    private static final String HOST_ELEMENT_URN =
        "urn:core:platform:civitas:element:common:" + HOST_ID;
    private static final String HOST_DATA_STRUCTURE_URN =
        "urn:core:platform:civitas:datastructure:common:" + HOST_ID;
    private static final String FOREIGN_DATA_STRUCTURE_URN =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2";

    private ModelRegistryGateway.ModelPin store(String id) {
      return store(Optional.of(HOST_ELEMENT_URN), id);
    }

    private ModelRegistryGateway.ModelPin store(Optional<String> existingLogicalUrn, String id) {
      Map<String, Object> model = new HashMap<>();
      model.put("$id", id);
      model.put("type", "object");
      return gateway.storeModel(
          existingLogicalUrn, HOST_ID, "Host", model, null, VersionBump.MINOR, "1.0.0");
    }

    @Test
    @DisplayName("refuses a $id naming another data structure and writes nothing")
    void refusesAForeignDataStructureId() {
      assertThatThrownBy(() -> store(FOREIGN_DATA_STRUCTURE_URN))
          .isInstanceOf(InvalidInputException.class);
      verifyNoInteractions(modelForge);
    }

    @Test
    @DisplayName("refuses a $id naming another data structure on a first store")
    void refusesAForeignDataStructureIdOnAFirstStore() {
      assertThatThrownBy(() -> store(Optional.empty(), FOREIGN_DATA_STRUCTURE_URN))
          .isInstanceOf(InvalidInputException.class);
      verifyNoInteractions(modelForge);
    }

    @Test
    @DisplayName("folds a $id naming the host's own data structure into it")
    void foldsTheHostsOwnDataStructureId() {
      when(modelForge.importSchema(any()))
          .thenReturn(
              new ImportResult(
                  new ArtifactId(HOST_DATA_STRUCTURE_URN + ":1.1.0"), List.of(), Map.of()));

      assertThat(store(HOST_DATA_STRUCTURE_URN).logicalUrn()).isEqualTo(HOST_DATA_STRUCTURE_URN);
    }
  }

  @Nested
  @DisplayName("copying a model version")
  class CopyingAModelVersion {

    private static final String SOURCE_PIN =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:1.0.0";
    private static final String COPY_PIN =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:3.0.0";

    @Test
    @DisplayName("copies the pinned version and returns the copy's pin")
    void copiesThePinnedVersion() {
      ArtifactId source = new ArtifactId(SOURCE_PIN);
      when(modelForge.existing(List.of(source))).thenReturn(Set.of(source));
      when(modelForge.copyVersion(any()))
          .thenReturn(new ArtifactWriteResult(new ArtifactId(COPY_PIN), Map.of()));

      ModelRegistryGateway.ModelPin pin = gateway.copyModelVersion(SOURCE_PIN);

      verify(modelForge).copyVersion(new CopyVersionCommand(source));
      assertThat(pin.versionedUrn()).isEqualTo(COPY_PIN);
      assertThat(pin.version()).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("refuses a version the registry does not hold")
    void refusesAMissingVersion() {
      when(modelForge.existing(any())).thenReturn(Set.of());

      assertThatThrownBy(() -> gateway.copyModelVersion(SOURCE_PIN))
          .isInstanceOf(ModelMissingException.class);
      verify(modelForge, never()).copyVersion(any());
    }
  }

  @Nested
  @DisplayName("deleting a model version")
  class DeletingAModelVersion {

    private static final String ROOT_PIN =
        "urn:core:platform:civitas:element:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2:2.1.0";
    private static final String ROOT =
        "urn:core:platform:civitas:element:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2";
    private static final String GROUPING =
        "urn:core:platform:civitas:datastructure:common:6ddf8001-3ba6-83db-8d23-b547419c1ff2";

    @Test
    @DisplayName("deletes the version line the pin names")
    void deletesTheLineOfThePin() {
      gateway.deleteModelVersion(ROOT_PIN);

      verify(modelForge).deleteVersionLine(new ArtifactId(ROOT_PIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("does nothing for a version without a model")
    void doesNothingWithoutAModel(String modelUrn) {
      gateway.deleteModelVersion(modelUrn);
      gateway.deleteModelVersion(null);

      assertThat(gateway.versionReferencesTo(modelUrn)).isEmpty();
      assertThat(gateway.versionReferencesTo(null)).isEmpty();
      verifyNoInteractions(modelForge);
    }

    @Test
    @DisplayName("asks what blocks the version line the pin names")
    void asksWhatBlocksTheLine() {
      String sink =
          "urn:core:platform:civitas:datasink:common:762a05c3-4783-8446-b3cb-8240eee1227a";
      when(modelForge.versionLineDeletionBlockers(new ArtifactId(ROOT_PIN)))
          .thenReturn(List.of(sink));

      assertThat(gateway.versionReferencesTo(ROOT_PIN)).containsExactly(sink);
    }

    /** Without the second delete a root Element whose first line was deleted would outlive it. */
    @Test
    @DisplayName("deleting a root Element's model deletes its grouping and the Element itself")
    void deletingARootModelDeletesTheGroupingAndTheElement() {
      gateway.deleteModel(ROOT);

      verify(modelForge).deleteArtifact(new ArtifactId(GROUPING), true, false);
      verify(modelForge).deleteArtifact(new ArtifactId(ROOT), true, false);
    }
  }

  @Nested
  @DisplayName("the model id of a URN")
  class ModelIdOf {

    private static final UUID ID = UUID.fromString("6ddf8001-3ba6-83db-8d23-b547419c1ff2");

    @ParameterizedTest
    @ValueSource(strings = {"element", "datastructure"})
    @DisplayName("is the UUID of an Element or Data structure URN")
    void isTheUuidOfAModelUrn(String type) {
      assertThat(
              gateway.modelIdOf("urn:core:platform:civitas:" + type + ":common:" + ID + ":1.0.0"))
          .contains(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pipeline", "datasink", "datasource", "mapping", "dataset"})
    @DisplayName("is empty for an artifact that carries its own entity id")
    void isEmptyForAnotherArtifactType(String type) {
      assertThat(
              gateway.modelIdOf("urn:core:platform:civitas:" + type + ":common:" + ID + ":1.0.0"))
          .isEmpty();
    }

    @Test
    @DisplayName("is empty for a value that is no CORE URN")
    void isEmptyForANonUrn() {
      assertThat(gateway.modelIdOf("ds-1")).isEmpty();
    }
  }

  @Nested
  @DisplayName("comparing against a fetched document")
  class UnchangedAgainstAFetchedDocument {

    @Test
    @DisplayName("ignores the registry's identity stamps and compares the styles")
    void ignoresStampsAndComparesStyles() {
      ModelRegistryGateway.RegistryDocument stored =
          new ModelRegistryGateway.RegistryDocument(
              Map.of("$id", "urn:stamped", "qos", 1), Map.of("x", 1));

      assertThat(gateway.isUnchanged(stored, Map.of("qos", 1), Map.of("x", 1))).isTrue();
      assertThat(gateway.isUnchanged(stored, Map.of("qos", 2), Map.of("x", 1))).isFalse();
      assertThat(gateway.isUnchanged(stored, Map.of("qos", 1), null)).isFalse();
    }

    @Test
    @DisplayName("treats a stored document without content as an empty one")
    void emptyContentEqualsNoContent() {
      ModelRegistryGateway.RegistryDocument stored =
          new ModelRegistryGateway.RegistryDocument(null, null);

      assertThat(gateway.isUnchanged(stored, Map.of(), null)).isTrue();
    }
  }
}
