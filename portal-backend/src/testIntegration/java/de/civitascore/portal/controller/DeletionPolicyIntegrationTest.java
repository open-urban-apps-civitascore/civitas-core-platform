package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.MappingOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The deletion rules as a user meets them, exercised through the HTTP routes rather than the
 * services behind them, and asserted by what the registry still holds afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Deletion policy, through the routes a user reaches")
class DeletionPolicyIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private ModelRegistryGateway registry;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataStructureRepository dataStructureRepository;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<String> delete(String path) {
    return restTemplate.exchange(
        path, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
  }

  private ResponseEntity<String> post(String path, Object body) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), String.class);
  }

  /**
   * A data set created through its own route, so the registry holds the manifest that carries its
   * mappings. The factory builds only the host row.
   */
  private DataSet dataSetWithManifest() {
    ResponseEntity<String> created =
        post(
            "/datasets",
            Map.of("name", "deletion_policy_" + UUID.randomUUID(), "description", "d"));
    assertThat(created.getStatusCode()).as("%s", created.getBody()).isEqualTo(HttpStatus.CREATED);
    UUID id = UUID.fromString(field(created.getBody(), "id"));
    return dataSetRepository.findById(id).orElseThrow();
  }

  /** A released data structure carrying one version whose model is stored in the registry. */
  private DataStructureVersion structureWithModel(String title) {
    DataStructure structure = portalData.dataStructure();
    DataStructureVersion version = portalData.dataStructureVersion(structure);
    return portalData.attachModel(version, portalData.dataStructureVersionModel(title));
  }

  @Test
  @DisplayName("a structure only a superseded version still references can be deleted")
  void aStructureOnlyASupersededVersionReferencesCanBeDeleted() {
    DataStructureVersion target = structureWithModel("Reading");
    DataStructureVersion replacement = structureWithModel("Replacement");

    DataSet dataSet = dataSetWithManifest();
    Pipeline pipeline = portalData.pipeline(dataSet);
    DataSink sink = portalData.dataSink(dataSet, pipeline);
    // The sink writes into the target's model, then is repointed at the replacement. Its first
    // configuration version keeps the original edge.
    sink = portalData.attachSinkConfiguration(sink, Map.of("element", target.getModelUrn()));
    portalData.attachSinkConfiguration(sink, Map.of("element", replacement.getModelUrn()));

    UUID structureId = target.getDataStructure().getId();
    assertThat(delete("/datastructures/" + structureId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(registry.fetchPayload(target.getModelUrn())).isEmpty();
  }

  @Test
  @DisplayName("a structure a mapping still uses is refused, and says so before the attempt")
  void aStructureAMappingUsesIsRefusedAndReportsItself() {
    DataStructureVersion source = structureWithModel("Source");
    DataStructureVersion target = structureWithModel("Target");
    DataSet dataSet = dataSetWithManifest();

    assertThat(
            post(
                    "/datasets/" + dataSet.getId() + "/mappings",
                    Map.of(
                        "title",
                        "Source to Target",
                        "source",
                        source.getModelUrn(),
                        "target",
                        target.getModelUrn(),
                        "fields",
                        Map.of()))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    // The refusal and the flag have to agree: reporting the structure free and then refusing the
    // delete leaves the user with an error and no way to act on it.
    assertThat(registry.versionReferencesTo(source.getModelUrn())).isNotEmpty();
    assertThat(delete("/datastructures/" + source.getDataStructure().getId()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(registry.fetchPayload(source.getModelUrn())).isPresent();
  }

  @Test
  @DisplayName("a force parameter on the mapping delete reaches past nothing")
  void aForceParameterOnTheMappingDeleteReachesPastNothing() {
    DataStructureVersion source = structureWithModel("Source");
    DataStructureVersion target = structureWithModel("Target");
    DataSet dataSet = dataSetWithManifest();
    Pipeline pipeline = portalData.pipeline(dataSet);

    MappingOutputDTO mapping = createMapping(dataSet, source, target);
    portalData.attachPipelineDefinition(
        pipeline,
        Map.of(
            "title",
            "Ingest",
            "nodes",
            List.of(Map.of("id", "map", "kind", "mapping", "mappingRef", mapping.logicalUrn())),
            "edges",
            List.of()),
        null);

    ResponseEntity<String> refused =
        delete("/datasets/" + dataSet.getId() + "/mappings/" + mapping.mappingId() + "?force=true");

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(registry.fetchPayload(mapping.logicalUrn())).isPresent();
  }

  @Test
  @DisplayName("deleting a pipeline takes the mapping no other pipeline uses")
  void deletingAPipelineTakesItsMapping() {
    DataStructureVersion source = structureWithModel("Source");
    DataStructureVersion target = structureWithModel("Target");
    DataSet dataSet = dataSetWithManifest();
    Pipeline pipeline = portalData.pipeline(dataSet);

    String mappingUrn = createMapping(dataSet, source, target).logicalUrn();
    pipeline =
        portalData.attachPipelineDefinition(
            pipeline,
            Map.of(
                "title",
                "Ingest",
                "nodes",
                List.of(Map.of("id", "map", "kind", "mapping", "mappingRef", mappingUrn)),
                "edges",
                List.of()),
            null);

    assertThat(
            delete("/datasets/" + dataSet.getId() + "/pipelines/" + pipeline.getId())
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // Left behind, the mapping would refuse both structures it joined, and no route reaches it.
    assertThat(registry.fetchPayload(mappingUrn)).isEmpty();
    assertThat(registry.versionReferencesTo(source.getModelUrn())).isEmpty();
    assertThat(registry.versionReferencesTo(target.getModelUrn())).isEmpty();
  }

  @Test
  @DisplayName("a version a mapping uses is refused, and the list flag says so")
  void aVersionAMappingUsesIsRefused() {
    DataStructureVersion source = structureWithModel("Source");
    DataStructureVersion target = structureWithModel("Target");
    createMapping(dataSetWithManifest(), source, target);

    assertThat(versionInUse(source)).isTrue();
    assertThat(delete(versionPath(source)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(registry.fetchStoredModel(source.getModelUrn())).isPresent();
  }

  @Test
  @DisplayName("a version is refused while a sink writes into an earlier minor of it")
  void aVersionASinkPinsAtAnEarlierMinorIsRefused() {
    DataStructureVersion first = structureWithModel("Reading");
    String earlierMinor = first.getModelUrn();
    first.setDataStructure(dataStructureOf(first.getDataStructure()));
    DataStructureVersion edited =
        portalData.attachModel(first, portalData.dataStructureVersionModel("Reading edited"));
    assertThat(edited.getModelUrn()).isNotEqualTo(earlierMinor);
    DataSet dataSet = dataSetWithManifest();
    DataSink sink = portalData.dataSink(dataSet, portalData.pipeline(dataSet));
    portalData.attachSinkConfiguration(sink, Map.of("element", earlierMinor));

    assertThat(versionInUse(edited)).isTrue();
    ResponseEntity<String> refused = delete(versionPath(edited));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(refused.getBody()).contains("RESOURCE_IN_USE");
    assertThat(registry.fetchStoredModel(earlierMinor)).isPresent();
  }

  @Test
  @DisplayName("a version an older version of another model pins is refused")
  void aVersionAnOlderVersionOfAnotherModelPinsIsRefused() {
    DataStructureVersion target = structureWithModel("Reading");
    portalData.attachModel(
        portalData.dataStructureVersion(dataStructureOf(target.getDataStructure())),
        portalData.dataStructureVersionModel("Reading v2"));
    // The other model's first version pins the target; its second version, now current, does not.
    DataStructure other = portalData.dataStructure();
    portalData.attachModel(
        portalData.dataStructureVersion(other),
        Map.of(
            "title",
            "Visit",
            "type",
            "object",
            "properties",
            Map.of("reading", Map.of("$ref", target.getModelUrn()))));
    portalData.attachModel(
        portalData.dataStructureVersion(dataStructureOf(other)),
        portalData.dataStructureVersionModel("Visit v2"));

    assertThat(versionInUse(target)).isTrue();
    assertThat(delete(versionPath(target)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(registry.fetchStoredModel(target.getModelUrn())).isPresent();
  }

  @Test
  @DisplayName("deleting a version keeps an element another version still holds")
  void deletingAVersionKeepsAnElementAnotherVersionHolds() {
    DataStructure structure = portalData.dataStructure();
    portalData.attachModel(portalData.dataStructureVersion(structure), library("Person"));
    DataStructureVersion second =
        portalData.attachModel(
            portalData.dataStructureVersion(dataStructureOf(structure)),
            library("Person", "Address"));
    Map<String, Object> members = membersOf(second.getModelUrn());

    assertThat(versionInUse(second)).isFalse();
    assertThat(delete(versionPath(second)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(registry.fetchStoredModel(memberUrn(members, "Person"))).isPresent();
    assertThat(registry.fetchStoredModel(memberUrn(members, "Address"))).isEmpty();
    assertThat(registry.fetchStoredModel(second.getModelUrn())).isEmpty();
  }

  @Test
  @DisplayName("deleting a structure removes its root element after its first version is gone")
  void deletingAStructureRemovesItsRootElement() {
    DataStructureVersion first = structureWithModel("Reading");
    DataStructureVersion second =
        portalData.attachModel(
            portalData.dataStructureVersion(dataStructureOf(first.getDataStructure())),
            portalData.dataStructureVersionModel("Reading v2"));
    String rootElement = dataStructureOf(first.getDataStructure()).getModelLogicalUrn();
    assertThat(delete(versionPath(first)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(delete("/datastructures/" + first.getDataStructure().getId()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(registry.holds(rootElement)).isFalse();
    assertThat(registry.fetchStoredModel(second.getModelUrn())).isEmpty();
  }

  private DataStructure dataStructureOf(DataStructure structure) {
    return dataStructureRepository.findById(structure.getId()).orElseThrow();
  }

  private static String versionPath(DataStructureVersion version) {
    return "/datastructures/" + version.getDataStructure().getId() + "/versions/" + version.getId();
  }

  /** The flag the Data structure list shows for the version. */
  private boolean versionInUse(DataStructureVersion version) {
    ResponseEntity<String> structure =
        restTemplate.exchange(
            "/datastructures/" + version.getDataStructure().getId(),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);
    assertThat(structure.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode row :
        new ObjectMapper().readTree(structure.getBody()).get("dataStructureVersions")) {
      if (row.get("id").asText().equals(version.getId().toString())) {
        return row.get("inUse").asBoolean();
      }
    }
    throw new AssertionError("version " + version.getId() + " is not listed");
  }

  private static Map<String, Object> library(String... members) {
    Map<String, Object> defs = new HashMap<>();
    for (String member : members) {
      defs.put(
          member, Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));
    }
    return Map.of("title", "People", "$defs", defs, "$ref", "#/$defs/Person");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> membersOf(String modelUrn) {
    return (Map<String, Object>)
        registry.fetchStoredModel(modelUrn).orElseThrow().content().get("$defs");
  }

  @SuppressWarnings("unchecked")
  private static String memberUrn(Map<String, Object> members, String key) {
    return (String) ((Map<String, Object>) members.get(key)).get("$ref");
  }

  /** Reads one top-level field, so an added field cannot shift what a regex would have matched. */
  private String field(String body, String name) {
    return new ObjectMapper().readTree(body).get(name).asText();
  }

  private MappingOutputDTO createMapping(
      DataSet dataSet, DataStructureVersion source, DataStructureVersion target) {
    ResponseEntity<String> created =
        post(
            "/datasets/" + dataSet.getId() + "/mappings",
            Map.of(
                "title",
                "Source to Target",
                "source",
                source.getModelUrn(),
                "target",
                target.getModelUrn(),
                "fields",
                Map.of()));
    assertThat(created.getStatusCode()).as("%s", created.getBody()).isEqualTo(HttpStatus.CREATED);
    return new ObjectMapper().readValue(created.getBody(), MappingOutputDTO.class);
  }
}
