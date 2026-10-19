package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  @Autowired private ModelRegistryGateway modelRegistryGateway;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private DataStructureVersion releasedVersion(String title) {
    return releasedVersionOf(
        portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE)),
        portalData.dataStructureVersionModel(title));
  }

  private DataStructureVersion releasedVersionOf(
      DataStructure structure, Map<String, Object> model) {
    DataStructureVersion version =
        portalData.dataStructureVersion(
            structure, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
    return portalData.attachModel(version, model);
  }

  private static Map<String, Object> pipelineDefinition(String mappingUrn) {
    return Map.of(
        "title",
        "Ingest",
        "nodes",
        List.of(Map.of("id", "map", "kind", "mapping", "mappingRef", mappingUrn)),
        "edges",
        List.of());
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

  private DataSet dataSetWithManifestUrn(String manifestLogicalUrn) {
    DataSet dataSet = portalData.dataSet();
    dataSet.setManifestLogicalUrn(manifestLogicalUrn);
    return dataSetRepository.save(dataSet);
  }

  private DataSet unreleasing(DataSet dataSet) {
    DataSet reloaded = dataSetRepository.findById(dataSet.getId()).orElseThrow();
    reloaded.setDataSetStatus(DataSetStatus.READY);
    reloaded.setPendingSagaType(PendingSagaType.UNRELEASE);
    return dataSetRepository.save(reloaded);
  }

  @Nested
  @DisplayName("Release-state queries")
  class Queries {

    @Test
    @DisplayName("each kind reports its row's release state by logical URN")
    void eachKindReportsItsReleaseState() {
      DataSet released = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      released.setManifestLogicalUrn(
          "urn:core:platform:civitas:dataset:common:85b23d00-fd3b-8af5-9dcc-69445cda1b26");
      released = dataSetRepository.save(released);
      Pipeline pipeline =
          portalData.pipeline(
              released,
              b ->
                  b.modelLogicalUrn(
                      "urn:core:platform:civitas:pipeline:common:00000000-0000-4000-8000-000000000021"));
      DataSink sink =
          portalData.dataSink(
              released,
              pipeline,
              s ->
                  s.setConfigurationLogicalUrn(
                      "urn:core:platform:civitas:datasink:common:00000000-0000-4000-8000-000000000022"));
      DataSource source =
          portalData.dataSource(
              b ->
                  b.dataSourceStatus(DataSourceStatus.AVAILABLE)
                      .configurationLogicalUrn(
                          "urn:core:platform:civitas:datasource:common:00000000-0000-4000-8000-000000000023"));
      DataStructure structure =
          portalData.dataStructure(
              b ->
                  b.dataStructureStatus(DataStructureStatus.DRAFT)
                      .modelLogicalUrn(
                          "urn:core:platform:civitas:datastructure:common:00000000-0000-4000-8000-000000000024"));

      assertThat(
              dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(
                  Set.of(released.getManifestLogicalUrn())))
          .containsExactly(new ReferrerReleaseState(released.getManifestLogicalUrn(), true));
      assertThat(
              pipelineRepository.findReleaseStatesByModelLogicalUrnIn(
                  Set.of(pipeline.getModelLogicalUrn())))
          .containsExactly(new ReferrerReleaseState(pipeline.getModelLogicalUrn(), true));
      assertThat(
              dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(
                  Set.of(sink.getConfigurationLogicalUrn())))
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

      assertThat(pipelineRepository.findIdsByDataSourceIdWithReleasedDataSet(source.getId()))
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
      portalData.attachPipelineDefinition(pipeline, pipelineDefinition(mappingUrn), null);

      assertThat(usageOf(source).inUse()).isTrue();
      assertThat(usageOf(source).inUseByReleased()).isFalse();

      withStatus(dataSet, DataSetStatus.AVAILABLE);

      assertThat(usageOf(source).releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.MAPPING);
    }

    @Test
    @DisplayName(
        "a structure referencing a version from a nested element releases it only when AVAILABLE")
    void structureReferencingFromNestedElement_followsItsStatus() {
      DataStructureVersion version = releasedVersion("Reading");
      Map<String, Object> model = portalData.dataStructureVersionModel("NestedHost");
      model.put("properties", Map.of("id", Map.of("type", "string")));
      model.put(
          "$defs",
          Map.of(
              "Measurement",
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("reading", Map.of("$ref", version.getModelUrn())))));
      DataStructure referrer = structureWithModel(DataStructureStatus.DRAFT, model);

      assertThat(usageOf(version).inUse()).isTrue();
      assertThat(usageOf(version).inUseByReleased()).isFalse();

      withStatus(referrer, DataStructureStatus.AVAILABLE);

      assertThat(usageOf(version).releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.DATA_STRUCTURE);
    }

    @Test
    @DisplayName(
        "a structure referencing a version from its root element releases it only when AVAILABLE")
    void structureReferencingFromRootElement_followsItsStatus() {
      DataStructureVersion version = releasedVersion("Reading");
      Map<String, Object> model = portalData.dataStructureVersionModel("RootHost");
      model.put("properties", Map.of("reading", Map.of("$ref", version.getModelUrn())));
      DataStructure referrer = structureWithModel(DataStructureStatus.DRAFT, model);

      assertThat(usageOf(version).inUse()).isTrue();
      assertThat(usageOf(version).inUseByReleased()).isFalse();

      withStatus(referrer, DataStructureStatus.AVAILABLE);

      assertThat(usageOf(version).releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.DATA_STRUCTURE);
    }
  }

  private DataStructure structureWithModel(DataStructureStatus status, Map<String, Object> model) {
    DataStructure structure = portalData.dataStructure(b -> b.dataStructureStatus(status));
    DataStructureVersion version = portalData.dataStructureVersion(structure);
    portalData.attachModel(version, model);
    return dataStructureRepository.findById(structure.getId()).orElseThrow();
  }

  private DataStructure withStatus(DataStructure structure, DataStructureStatus status) {
    DataStructure reloaded = dataStructureRepository.findById(structure.getId()).orElseThrow();
    reloaded.setDataStructureStatus(status);
    return dataStructureRepository.save(reloaded);
  }

  @Nested
  @DisplayName("Versions of one structure")
  class VersionsOfOneStructure {

    @Test
    @DisplayName("a class another version adds does not use the version it references")
    void classOfAnotherVersion_doesNotUseTheVersion() {
      DataStructure structure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion first = releasedVersionOf(structure, modeledAs("NeueKlasse"));
      DataStructureVersion second =
          releasedVersionOf(structure, modeledAs("NeueKlasse", "NeueKlasse2"));

      assertThat(usageOf(first).inUse()).isFalse();
      assertThat(modelRegistryGateway.referencesTo(first.getModelUrn())).isEmpty();
      assertThat(usageOf(second).inUse()).isFalse();
    }

    @Test
    @DisplayName("a class an earlier version had does not use the later version")
    void classOfAnEarlierVersion_doesNotUseTheLaterVersion() {
      DataStructure structure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion first =
          releasedVersionOf(structure, modeledAs("NeueKlasse", "NeueKlasse2"));
      DataStructureVersion second = releasedVersionOf(structure, modeledAs("NeueKlasse"));

      assertThat(usageOf(second).inUse()).isFalse();
      assertThat(modelRegistryGateway.referencesTo(second.getModelUrn())).isEmpty();
      assertThat(usageOf(first).inUse()).isFalse();
    }

    @Test
    @DisplayName("a class of another structure still uses the version it references")
    void classOfAnotherStructure_usesTheVersion() {
      DataStructure structure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion version = releasedVersionOf(structure, modeledAs("NeueKlasse"));
      Map<String, Object> other = modeledAs("Fremd");
      defOf(other, "Fremd")
          .put(
              "properties", Map.of("neueKlasse", Map.of("$ref", memberUrn(version, "NeueKlasse"))));
      structureWithModel(DataStructureStatus.DRAFT, other);

      assertThat(usageOf(version).inUse()).isTrue();
    }

    @Test
    @DisplayName("a structure that two datasets list is not used by the listing")
    void structureListedByTwoDataSets_isNotUsedByTheListing() {
      DataStructure sourceStructure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion source = releasedVersionOf(sourceStructure, modeledAs("Quelle"));
      DataStructureVersion unused = releasedVersionOf(sourceStructure, modeledAs("Andere"));
      DataStructureVersion target =
          releasedVersionOf(
              portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE)),
              modeledAs("Ziel"));
      List<String> pipelines =
          List.of(
              pipelineMapping(dataSetWithManifest(), source, target),
              pipelineMapping(dataSetWithManifest(), source, target));

      assertThat(usageOf(source).inUse()).as("the mappings use it").isTrue();
      assertThat(usageOf(unused).inUse()).as("nothing uses this version").isFalse();

      pipelines.forEach(pipeline -> modelRegistryGateway.deleteArtifact(pipeline, true));

      assertThat(usageOf(source).inUse()).isFalse();
      assertThat(modelRegistryGateway.referencesTo(source.getModelUrn())).isEmpty();
    }

    /**
     * A pipeline whose mapping joins the two versions, stored into the dataset as the editor does.
     */
    private String pipelineMapping(
        DataSet dataSet, DataStructureVersion source, DataStructureVersion target) {
      String mappingUrn = createMapping(dataSet, source, target);
      return modelRegistryGateway
          .storePayload(
              PayloadKind.PIPELINE,
              Optional.empty(),
              UUID.randomUUID(),
              "pipeline",
              pipelineDefinition(mappingUrn),
              null,
              dataSet.getManifestLogicalUrn())
          .logicalUrn();
    }

    /**
     * The document the frontend builds for one diagram: each class a member, each class after the
     * first composing the first through a local reference.
     */
    private Map<String, Object> modeledAs(String... classes) {
      Map<String, Object> defs = new LinkedHashMap<>();
      for (int i = 0; i < classes.length; i++) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("attribut", Map.of("type", "string"));
        if (i > 0) {
          properties.put("neueKlasse", Map.of("$ref", "#/$defs/" + classes[0]));
        }
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("type", "object");
        def.put("title", classes[i]);
        def.put("properties", properties);
        defs.put(classes[i], def);
      }
      Map<String, Object> model = new LinkedHashMap<>();
      model.put("$id", "http://civitas.org/model/modell");
      model.put("$schema", "https://json-schema.org/draft/2020-12/schema");
      model.put("title", "Modell");
      model.put("$defs", defs);
      model.put("$ref", "#/$defs/" + classes[classes.length - 1]);
      return model;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> defOf(Map<String, Object> model, String name) {
      return (Map<String, Object>) ((Map<String, Object>) model.get("$defs")).get(name);
    }

    /** The Element the registry stored the version's class as, by the URN it carries. */
    private String memberUrn(DataStructureVersion version, String name) {
      Map<String, Object> stored =
          modelRegistryGateway.fetchStoredModel(version.getModelUrn()).orElseThrow().content();
      return modelRegistryGateway.logicalUrn((String) defOf(stored, name).get("$ref"));
    }
  }

  @Nested
  @DisplayName("Dataset in the middle of an unrelease")
  class InFlightUnrelease {

    // Checked on the query because Model Forge reports no Dataset as a referrer to drive the
    // lookup.
    @Test
    @DisplayName("a dataset counts as released while AVAILABLE or being unreleased, not when READY")
    void dataSetReleaseState_includesUnreleaseInFlight() {
      DataSet available =
          dataSetWithManifestUrn(
              "urn:core:platform:civitas:dataset:common:00000000-0000-4000-8000-000000000025");
      DataSet ready =
          dataSetWithManifestUrn(
              "urn:core:platform:civitas:dataset:common:00000000-0000-4000-8000-000000000026");
      DataSet unreleasing =
          dataSetWithManifestUrn(
              "urn:core:platform:civitas:dataset:common:00000000-0000-4000-8000-000000000027");
      withStatus(available, DataSetStatus.AVAILABLE);
      withStatus(ready, DataSetStatus.READY);
      unreleasing(unreleasing);

      assertThat(
              dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(
                  Set.of(
                      available.getManifestLogicalUrn(),
                      ready.getManifestLogicalUrn(),
                      unreleasing.getManifestLogicalUrn())))
          .containsExactlyInAnyOrder(
              new ReferrerReleaseState(available.getManifestLogicalUrn(), true),
              new ReferrerReleaseState(ready.getManifestLogicalUrn(), false),
              new ReferrerReleaseState(unreleasing.getManifestLogicalUrn(), true));
    }

    @Test
    @DisplayName("a sink of a dataset being unreleased keeps its version released-used")
    void sinkOfUnreleasingDataSet_isReleased() {
      DataStructureVersion version = releasedVersion("Reading");
      DataSet dataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      DataSink sink = portalData.dataSink(dataSet, portalData.pipeline(dataSet));
      portalData.attachSinkConfiguration(sink, Map.of("element", version.getModelUrn()));
      unreleasing(dataSet);

      assertThat(usageOf(version).releasedReferrers())
          .extracting(ArtifactUsageLookup.ReleasedReferrer::kind)
          .containsExactly(ReferrerKind.DATA_SINK);
    }

    @Test
    @DisplayName("a pipeline of a dataset being unreleased keeps its data source released-used")
    void pipelineOfUnreleasingDataSet_keepsDataSourceReleased() {
      DataSource source =
          portalData.dataSource(b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE));
      DataSet dataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      Pipeline pipeline = portalData.pipeline(dataSet, b -> b.dataSources(Set.of(source)));
      unreleasing(dataSet);

      ArtifactUsage usage =
          transactionTemplate.execute(
              status -> lookup.of(dataSourceRepository.findById(source.getId()).orElseThrow()));

      assertThat(usage.releasedReferrers())
          .containsExactly(
              new ArtifactUsageLookup.ReleasedReferrer(
                  ReferrerKind.PIPELINE, pipeline.getId().toString()));
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
