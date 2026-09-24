package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ReferrerReleaseState;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.ReferrerKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ArtifactUsageLookup against the database and the registry")
class ArtifactUsageLookupIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private ArtifactUsageLookup lookup;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataStructureRepository dataStructureRepository;
  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;
  @Autowired private PipelineRepository pipelineRepository;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private DataStructureVersion releasedVersion(String title) {
    DataStructure structure =
        portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
    DataStructureVersion version =
        portalData.dataStructureVersion(
            structure, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
    return portalData.attachModel(version, portalData.dataStructureVersionModel(title));
  }

  private ArtifactUsage usageOf(DataStructureVersion version) {
    return transactionTemplate.execute(
        status ->
            lookup.of(dataStructureVersionRepository.findById(version.getId()).orElseThrow()));
  }

  private DataSet withStatus(DataSet dataSet, DataSetStatus status) {
    DataSet reloaded = dataSetRepository.findById(dataSet.getId()).orElseThrow();
    reloaded.setDataSetStatus(status);
    return dataSetRepository.save(reloaded);
  }

  @Nested
  @DisplayName("Release-state queries")
  class Queries {

    @Test
    @DisplayName("each kind reports its row's release state by logical URN")
    void eachKindReportsItsReleaseState() {
      DataSet released = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      released.setManifestLogicalUrn("urn:core:platform:civitas:dataset:common:A:1aaaaaaaaa");
      released = dataSetRepository.save(released);
      Pipeline pipeline =
          portalData.pipeline(
              released, b -> b.modelLogicalUrn("urn:core:platform:civitas:pipeline:common:P:1a"));
      DataSink sink =
          portalData.dataSink(
              released,
              pipeline,
              s -> s.setConfigurationLogicalUrn("urn:core:platform:civitas:datasink:common:S:1a"));
      DataSource source =
          portalData.dataSource(
              b ->
                  b.dataSourceStatus(DataSourceStatus.AVAILABLE)
                      .configurationLogicalUrn("urn:core:platform:civitas:datasource:common:D:1a"));
      DataStructure structure =
          portalData.dataStructure(
              b ->
                  b.dataStructureStatus(DataStructureStatus.DRAFT)
                      .modelLogicalUrn("urn:core:platform:civitas:datastructure:common:T:1a"));

      assertThat(
              dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(
                  Set.of(released.getManifestLogicalUrn()), DataSetStatus.AVAILABLE))
          .containsExactly(new ReferrerReleaseState(released.getManifestLogicalUrn(), true));
      assertThat(
              pipelineRepository.findReleaseStatesByModelLogicalUrnIn(
                  Set.of(pipeline.getModelLogicalUrn()), DataSetStatus.AVAILABLE))
          .containsExactly(new ReferrerReleaseState(pipeline.getModelLogicalUrn(), true));
      assertThat(
              dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(
                  Set.of(sink.getConfigurationLogicalUrn()), DataSetStatus.AVAILABLE))
          .containsExactly(new ReferrerReleaseState(sink.getConfigurationLogicalUrn(), true));
      assertThat(
              dataSourceRepository.findReleaseStatesByConfigurationLogicalUrnIn(
                  Set.of(source.getConfigurationLogicalUrn()), DataSourceStatus.AVAILABLE))
          .containsExactly(new ReferrerReleaseState(source.getConfigurationLogicalUrn(), true));
      assertThat(
              dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
                  Set.of(structure.getModelLogicalUrn()), DataStructureStatus.AVAILABLE))
          .containsExactly(new ReferrerReleaseState(structure.getModelLogicalUrn(), false));
    }

    @Test
    @DisplayName("the pipelines of released datasets are found through the data source link")
    void releasedPipelinesAreFoundThroughTheDataSourceLink() {
      DataSource source =
          portalData.dataSource(b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE));
      DataSet draft = portalData.dataSet();
      DataSet released = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      portalData.pipeline(draft, b -> b.dataSources(Set.of(source)));
      Pipeline releasedPipeline = portalData.pipeline(released, b -> b.dataSources(Set.of(source)));

      assertThat(
              pipelineRepository.findIdsByDataSourceIdAndDataSetStatus(
                  source.getId(), DataSetStatus.AVAILABLE))
          .containsExactly(releasedPipeline.getId());
    }

    @Test
    @DisplayName("the data sources pinned to versions are filtered by status")
    void pinnedDataSourcesAreFilteredByStatus() {
      DataStructureVersion version = releasedVersion("Pinned");
      portalData.dataSource(b -> b.dataStructureVersion(version));
      DataSource available =
          portalData.dataSource(
              b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE).dataStructureVersion(version));

      assertThat(
              dataSourceRepository.findIdsByDataStructureVersionIdInAndStatus(
                  Set.of(version.getId()), DataSourceStatus.AVAILABLE))
          .containsExactly(available.getId());
    }
  }

  @Nested
  @DisplayName("Registry referrers")
  class RegistryReferrers {

    @Test
    @DisplayName("a sink of a DRAFT dataset uses a version without releasing it")
    void sinkOfDraftDataSet_isNotReleased() {
      DataStructureVersion version = releasedVersion("Reading");
      DataSet dataSet = portalData.dataSet();
      DataSink sink = portalData.dataSink(dataSet, portalData.pipeline(dataSet));
      portalData.attachSinkConfiguration(sink, Map.of("element", version.getModelUrn()));

      ArtifactUsage usage = usageOf(version);

      assertThat(usage.inUse()).isTrue();
      assertThat(usage.inUseByReleased()).isFalse();
    }

    @Test
    @DisplayName("a sink of an AVAILABLE dataset uses a version as released work")
    void sinkOfAvailableDataSet_isReleased() {
      DataStructureVersion version = releasedVersion("Reading");
      DataSet dataSet = portalData.dataSet();
      DataSink sink = portalData.dataSink(dataSet, portalData.pipeline(dataSet));
      portalData.attachSinkConfiguration(sink, Map.of("element", version.getModelUrn()));
      withStatus(dataSet, DataSetStatus.AVAILABLE);

      ArtifactUsage usage = usageOf(version);

      assertThat(usage.releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.DATA_SINK);
    }

    @Test
    @DisplayName("a mapping counts as released only once its pipeline's dataset is AVAILABLE")
    void mappingFollowsItsPipelinesDataSet() {
      DataStructureVersion source = releasedVersion("Source");
      DataStructureVersion target = releasedVersion("Target");
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

      assertThat(usageOf(source).inUse()).isTrue();
      assertThat(usageOf(source).inUseByReleased()).isFalse();

      withStatus(dataSet, DataSetStatus.AVAILABLE);

      assertThat(usageOf(source).releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.MAPPING);
    }
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<String> post(String path, Object body) {
    return restTemplate.exchange(
        path, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), String.class);
  }

  /** Created through its route, so the registry holds the manifest a mapping is stored under. */
  private DataSet dataSetWithManifest() {
    ResponseEntity<String> created =
        post("/datasets", Map.of("name", "usage_" + UUID.randomUUID(), "description", "d"));
    assertThat(created.getStatusCode()).as("%s", created.getBody()).isEqualTo(HttpStatus.CREATED);
    return dataSetRepository
        .findById(UUID.fromString(field(created.getBody(), "id")))
        .orElseThrow();
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

  private String field(String body, String name) {
    return new ObjectMapper().readTree(body).get(name).asText();
  }
}
