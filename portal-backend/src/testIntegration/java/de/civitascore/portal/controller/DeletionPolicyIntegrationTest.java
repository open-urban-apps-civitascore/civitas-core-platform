package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
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
    assertThat(registry.isReferenced(source.getModelUrn())).isTrue();
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

    String mappingUrn = createMapping(dataSet, source, target);
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

    ResponseEntity<String> refused =
        delete("/datasets/" + dataSet.getId() + "/mappings?urn=" + mappingUrn + "&force=true");

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(registry.fetchPayload(mappingUrn)).isPresent();
  }

  @Test
  @DisplayName("deleting a pipeline takes the mapping no other pipeline uses")
  void deletingAPipelineTakesItsMapping() {
    DataStructureVersion source = structureWithModel("Source");
    DataStructureVersion target = structureWithModel("Target");
    DataSet dataSet = dataSetWithManifest();
    Pipeline pipeline = portalData.pipeline(dataSet);

    String mappingUrn = createMapping(dataSet, source, target);
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
    assertThat(registry.isReferenced(source.getModelUrn())).isFalse();
    assertThat(registry.isReferenced(target.getModelUrn())).isFalse();
  }

  /** Reads one top-level field, so an added field cannot shift what a regex would have matched. */
  private String field(String body, String name) {
    return new ObjectMapper().readTree(body).get(name).asText();
  }

  private String createMapping(
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
    return field(created.getBody(), "logicalUrn");
  }
}
