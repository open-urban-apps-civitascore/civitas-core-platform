package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.service.connector.ConnectorValidator;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link InstallationService}. The services of the platform and the registry are
 * mocks, so these tests do not show the rules of the platform. {@code
 * InstallationControllerIntegrationTest} shows them.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

  private static final String PACKAGE_ID = "tree-cadastre";
  private static final String TITLE = "title";

  /** A Data structure URN of another instance, as a package from that instance carries it. */
  private static final String FOREIGN_URN =
      "urn:core:platform:civitas:datastructure:common:0b9e5b0e-6a3f-4c1e-9d2a-7f3c2e1a5b4d";

  /** Identities in the form of the catalogue, which are no CORE URNs of an instance. */
  private static final String SOURCE_URN =
      "urn:core:standard:test:datasource:environment:treedb:a1b2c3d4e5";

  private static final String DATASET_URN =
      "urn:core:standard:test:dataset:environment:trees:f6g7h8i9j0";
  private static final String API_URN = "urn:core:standard:test:api:environment:map:k1l2m3n4o5";

  private static final UUID DATAPOOL_ID = UUID.fromString("11111111-1111-4000-8000-000000000001");
  private static final UUID STRUCTURE_ID = UUID.fromString("44444444-4444-4000-8000-000000000004");
  private static final UUID VERSION_ID = UUID.fromString("55555555-5555-4000-8000-000000000005");
  private static final UUID SOURCE_ID = UUID.fromString("66666666-6666-4000-8000-000000000006");
  private static final UUID DATASET_ID = UUID.fromString("77777777-7777-4000-8000-000000000007");
  private static final UUID API_ID = UUID.fromString("88888888-8888-4000-8000-000000000008");
  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:datastructure:common:44444444-4444-4000-8000-000000000004";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";
  private static final String SOURCE_LOGICAL_URN =
      "urn:core:platform:civitas:datasource:common:66666666-6666-4000-8000-000000000006";

  private static final String MAPPING_URN =
      "urn:core:standard:test:mapping:environment:rowtotree:p1q2r3s4t5";
  private static final String SINK_URN =
      "urn:core:standard:test:datasink:environment:treetable:u6v7w8x9y0";
  private static final String PIPELINE_URN =
      "urn:core:standard:test:pipeline:environment:treeimport:a9b8c7d6e5";
  private static final String GREEN_STYLE_URN =
      "urn:core:standard:test:style:environment:treesgreen:f4g3h2i1j0";
  private static final String ORANGE_STYLE_URN =
      "urn:core:standard:test:style:environment:treesorange:k9l8m7n6o5";
  private static final String LAYER_URN =
      "urn:core:standard:test:layer:environment:trees:p4q3r2s1t0";

  private static final UUID SINK_ID = UUID.fromString("99999999-9999-4000-8000-00000000000a");
  private static final UUID PIPELINE_ID = UUID.fromString("99999999-9999-4000-8000-00000000000b");
  private static final UUID GREEN_ID = UUID.fromString("99999999-9999-4000-8000-00000000000c");
  private static final UUID ORANGE_ID = UUID.fromString("99999999-9999-4000-8000-00000000000d");
  private static final UUID LAYER_ID = UUID.fromString("99999999-9999-4000-8000-00000000000e");
  private static final String MAPPING_LOGICAL_URN =
      "urn:core:platform:civitas:mapping:common:99999999-9999-4000-8000-00000000000f";
  private static final String MAPPING_VERSIONED_URN = MAPPING_LOGICAL_URN + ":1.0.0";
  private static final String SINK_LOGICAL_URN =
      "urn:core:platform:civitas:datasink:common:99999999-9999-4000-8000-00000000000a";
  private static final String SINK_VERSIONED_URN = SINK_LOGICAL_URN + ":1.0.0";

  @Mock private InstallationRepository installationRepository;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private MappingService mappingService;
  @Mock private DataSinkService dataSinkService;
  @Mock private PipelineService pipelineService;
  @Mock private StyleService styleService;
  @Mock private LayerService layerService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Spy private ConnectorValidator connectorValidator = new ConnectorValidator(List.of());
  @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @InjectMocks private InstallationService installationService;

  @Captor private ArgumentCaptor<DataStructureInputDTO> structureCaptor;
  @Captor private ArgumentCaptor<DataStructureVersionInputDTO> versionCaptor;
  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetCaptor;
  @Captor private ArgumentCaptor<Map<String, Object>> documentCaptor;
  @Captor private ArgumentCaptor<DataSinkInputDTO> sinkCaptor;
  @Captor private ArgumentCaptor<PipelineInputDTO> pipelineCaptor;
  @Captor private ArgumentCaptor<LayerInputDTO> layerCaptor;

  /** The identities in these tests have no version, so the registry gives them back unchanged. */
  @BeforeEach
  void keepIdentities() {
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void install_whenPackageIsInstalled_throwsUniqueConstraintViolation() {
    when(installationRepository.existsActive(PACKAGE_ID, null)).thenReturn(true);

    assertThatThrownBy(() -> installationService.install(request(tree())))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("is already installed");

    verifyNoChange();
  }

  @Test
  void install_whenPackageIsInstalledInTheDatapool_throwsUniqueConstraintViolationWithThePool() {
    when(installationRepository.existsActive(PACKAGE_ID, DATAPOOL_ID)).thenReturn(true);

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, tree(), sqlSource(FOREIGN_URN))))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining(DATAPOOL_ID.toString());

    verifyNoChange();
  }

  @Test
  void install_whenPackageHasOnlyDataStructures_recordsNoDatapool() {
    stubCreate();

    // The request names a Data pool, but no member of the package goes into it.
    Installation installation = installationService.install(request(DATAPOOL_ID, tree()));

    assertThat(installation.getDatapoolId()).isNull();
    verify(installationRepository).existsActive(PACKAGE_ID, null);
  }

  @Test
  void install_whenUniqueIndexRefusesTheRecord_createsNothing() {
    // The check passes, because the other install has not committed yet. The index refuses the row.
    when(installationRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_installations_active_package"));

    assertThatThrownBy(() -> installationService.install(request(tree())))
        .isInstanceOf(DataIntegrityViolationException.class);

    verifyNoArtifactCreated();
  }

  @Test
  void install_whenSecondMemberHasNoDescription_changesNothing() {
    PackageMemberInputDTO park = member("park", "Park", null, Map.of(TITLE, "Park"));

    assertThatThrownBy(() -> installationService.install(request(tree(), park)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("needs a description");

    verifyNoChange();
  }

  @Test
  void install_whenTitleIsLongerThanTheNameLimit_changesNothing() {
    String title = "T".repeat(InstalledArtifact.NAME_LENGTH + 1);
    PackageMemberInputDTO member = member(FOREIGN_URN, null, "Trees", Map.of(TITLE, title));

    assertThatThrownBy(() -> installationService.install(request(member)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("must not exceed");

    verifyNoChange();
  }

  @Test
  void install_whenModelHasRootId_storesTheModelWithoutIt() {
    stubCreate();
    Map<String, Object> model = model();

    installationService.install(request(member(FOREIGN_URN, "Tree", "Trees", model)));

    verify(dataStructureVersionService).create(versionCaptor.capture());
    Map<String, Object> expected = new LinkedHashMap<>(model);
    expected.remove("$id");
    assertThat(versionCaptor.getValue().getModel()).isEqualTo(expected).doesNotContainKey("$id");
    assertThat(versionCaptor.getValue().getDataStructureId()).isEqualTo(STRUCTURE_ID);
    assertThat(model).containsEntry("$id", FOREIGN_URN);
  }

  @Test
  void install_whenMemberIsInstalled_recordsTheCopyAndItsOrigin() {
    stubCreate();

    Installation installation = installationService.install(request(tree()));

    assertThat(installation.getPackageId()).isEqualTo(PACKAGE_ID);
    assertThat(installation.getPackageVersion()).isEqualTo("1.0.0");
    assertThat(installation.getArtifacts())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getArtifactType()).isEqualTo(InstalledArtifactType.DATA_STRUCTURE);
              assertThat(line.getName()).isEqualTo("Tree");
              assertThat(line.getShellId()).isEqualTo(STRUCTURE_ID);
              assertThat(line.getUrn()).isEqualTo(LOGICAL_URN);
              assertThat(line.getVersionedUrn()).isEqualTo(VERSIONED_URN);
              assertThat(line.getOrigin()).isEqualTo(FOREIGN_URN);
            });
  }

  @Test
  void install_whenNameIsBlank_usesTheDocumentTitle() {
    stubCreate();

    installationService.install(request(member(FOREIGN_URN, " ", "Trees", model())));

    verify(dataStructureService).create(structureCaptor.capture());
    assertThat(structureCaptor.getValue().getName()).isEqualTo("Tree cadastre");
  }

  @Test
  void install_whenMembersComeInAnotherOrder_installsThemInTheOrderOfTheKinds() {
    stubCreate();
    stubDatapool();
    stubDataSource();
    stubDataSet();

    Installation installation =
        installationService.install(
            request(DATAPOOL_ID, dataSet(), sqlSource(FOREIGN_URN), tree()));

    InOrder order = inOrder(dataStructureService, dataSourceService, dataSetService);
    order.verify(dataStructureService).create(any());
    order.verify(dataSourceService).create(any());
    order.verify(dataSetService).create(any());
    assertThat(installation.getArtifacts())
        .extracting(InstalledArtifact::getArtifactType)
        .containsExactly(
            InstalledArtifactType.DATA_STRUCTURE,
            InstalledArtifactType.DATA_SOURCE,
            InstalledArtifactType.DATA_SET);
  }

  @Test
  void install_whenTwoMembersHaveTheSameIdentity_changesNothing() {
    PackageMemberInputDTO again = member(FOREIGN_URN, "Tree again", "Trees again", model());

    assertThatThrownBy(() -> installationService.install(request(tree(), again)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("Two members of the package have the identity");

    verifyNoChange();
  }

  @Test
  void install_whenPackageHasADataSourceAndNoDatapool_changesNothing() {
    assertThatThrownBy(() -> installationService.install(request(tree(), sqlSource(FOREIGN_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("datapoolId");

    verifyNoChange();
  }

  @Test
  void install_whenDatapoolDoesNotExist_changesNothing() {
    when(dataPoolRepository.existsById(DATAPOOL_ID)).thenReturn(false);

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, tree(), sqlSource(FOREIGN_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(DATAPOOL_ID.toString());

    verifyNoChange();
  }

  @Test
  void install_whenDataSourceReferencesNoMemberOfThePackage_changesNothing() {
    stubDatapool();
    String unknown = "urn:core:standard:test:datastructure:environment:other:z9y8x7w6v5";

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, tree(), sqlSource(unknown))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("the package has no member with this identity");

    verifyNoChange();
  }

  @Test
  void install_whenDataSourceReferencesAMemberOfAnotherKind_changesNothing() {
    stubDatapool();

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, tree(), sqlSource(SOURCE_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("is not of the kind datastructure");

    verifyNoChange();
  }

  @Test
  void install_whenConnectorHasAnUnknownKey_changesNothingAndNamesTheKey() {
    stubDatapool();
    PackageMemberInputDTO feed = mqttSource(1, Map.of("client_id", "air-quality"));

    assertThatThrownBy(() -> installationService.install(request(DATAPOOL_ID, tree(), feed)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(SOURCE_URN)
        .hasMessageContaining("Unknown key: connector.client_id");

    verifyNoChange();
  }

  @Test
  void install_whenConnectorBreaksARuleOfADraft_changesNothingAndNamesTheKey() {
    stubDatapool();
    PackageMemberInputDTO feed = mqttSource(5, Map.of());

    assertThatThrownBy(() -> installationService.install(request(DATAPOOL_ID, tree(), feed)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("connector.qos");

    verifyNoChange();
  }

  @Test
  void install_whenPackageHasADataSource_linksItToTheVersionOfTheStructureCopy() {
    stubCreate();
    stubDatapool();
    stubDataSource();

    Installation installation =
        installationService.install(request(DATAPOOL_ID, tree(), sqlSource(FOREIGN_URN)));

    verify(dataSourceService).create(sourceCaptor.capture());
    DataSourceInputDTO input = sourceCaptor.getValue();
    assertThat(input.getName()).isEqualTo("Tree database");
    assertThat(input.getDescription()).isEqualTo("Reads the trees from the database of the city");
    assertThat(input.getDataStructureVersionId()).isEqualTo(VERSION_ID);
    assertThat(input.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.SPECIFIC);
    assertThat(input.getDatapoolScope().getDatapoolIds()).containsExactly(DATAPOOL_ID);
    assertThat(input.getConnector())
        .isInstanceOfSatisfying(
            SqlConnectorConfiguration.class,
            connector -> {
              assertThat(connector.dsn()).isEqualTo("postgres://trees-db:5432/city");
              assertThat(connector.table()).isEqualTo("trees");
            });
    assertThat(installation.getDatapoolId()).isEqualTo(DATAPOOL_ID);
    assertThat(installation.getArtifacts())
        .filteredOn(line -> line.getArtifactType() == InstalledArtifactType.DATA_SOURCE)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getShellId()).isEqualTo(SOURCE_ID);
              assertThat(line.getUrn()).isEqualTo(SOURCE_LOGICAL_URN);
              assertThat(line.getOrigin()).isEqualTo(SOURCE_URN);
            });
  }

  @Test
  void install_whenPackageHasTwoDataSets_changesNothing() {
    PackageMemberInputDTO parks =
        member(
            PackageMemberKind.DATASET,
            DATASET_URN + "-parks",
            "Parks",
            "Parks of the city",
            Map.of("openDataAccess", false));

    assertThatThrownBy(() -> installationService.install(request(DATAPOOL_ID, dataSet(), parks)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("has 2 Datasets");

    verifyNoChange();
  }

  @Test
  void install_whenPackageHasAnApiAndNoDataSet_changesNothing() {
    assertThatThrownBy(() -> installationService.install(request(api("map"))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("it has no Dataset");

    verifyNoChange();
  }

  @Test
  void install_whenApiHasAnInvalidSlug_changesNothingAndNamesTheField() {
    stubDatapool();

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, dataSet(), api("Not a slug"))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(API_URN)
        .hasMessageContaining("field 'slug'");

    verifyNoChange();
  }

  @Test
  void install_whenDataSetHasApis_createsThemWithTheDataSetAndRecordsOneLineEach() {
    stubDatapool();
    stubDataSet();
    stubSave();
    PackageMemberInputDTO openDataSet =
        member(
            PackageMemberKind.DATASET,
            DATASET_URN,
            "Trees",
            "Trees of the city",
            Map.of("openDataAccess", true));

    Installation installation =
        installationService.install(request(DATAPOOL_ID, api("map"), openDataSet));

    verify(dataSetService).create(dataSetCaptor.capture());
    DataSetInputDTO input = dataSetCaptor.getValue();
    assertThat(input.getName()).isEqualTo("Trees");
    assertThat(input.getDatapoolId()).isEqualTo(DATAPOOL_ID);
    assertThat(input.getOpenDataAccess()).isFalse();
    assertThat(input.getNamedApis())
        .singleElement()
        .satisfies(
            api -> {
              assertThat(api.getName()).isEqualTo("Tree map");
              assertThat(api.getSlug()).isEqualTo("map");
              assertThat(api.getStandard()).isEqualTo(ApiStandard.OWS);
            });
    assertThat(installation.getArtifacts())
        .extracting(
            InstalledArtifact::getArtifactType,
            InstalledArtifact::getShellId,
            InstalledArtifact::getOrigin)
        .containsExactly(
            tuple(InstalledArtifactType.DATA_SET, DATASET_ID, DATASET_URN),
            tuple(InstalledArtifactType.NAMED_API, API_ID, API_URN));
  }

  @Test
  void install_whenPackageHasADataFlow_bindsEachReferenceToItsCopy() {
    stubCreate();
    stubDatapool();
    stubDataSource();
    stubDataSet();
    stubMapping();
    stubDataSink();
    stubPipeline();

    Installation installation =
        installationService.install(
            request(
                DATAPOOL_ID,
                pipeline(SINK_URN),
                postgisSink(),
                mapping(),
                dataSet(),
                sqlSource(FOREIGN_URN),
                tree()));

    verify(mappingService).store(eq(DATASET_ID), isNull(), documentCaptor.capture());
    assertThat(documentCaptor.getValue())
        .containsEntry("source", VERSIONED_URN)
        .containsEntry("target", VERSIONED_URN)
        .doesNotContainKeys("$schema", "id");
    verify(dataSinkService).create(sinkCaptor.capture());
    assertThat(sinkCaptor.getValue().getDataSetId()).isEqualTo(DATASET_ID);
    assertThat(sinkCaptor.getValue().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
    assertThat(sinkCaptor.getValue().getConfiguration())
        .containsOnly(entry("tableName", "trees"), entry("element", VERSIONED_URN));
    verify(pipelineService).create(pipelineCaptor.capture());
    PipelineInputDTO pipeline = pipelineCaptor.getValue();
    assertThat(pipeline.getDataSetId()).isEqualTo(DATASET_ID);
    assertThat(pipeline.getName()).isEqualTo("Tree import");
    assertThat(pipeline.getDataSourceIds()).containsExactly(SOURCE_ID);
    assertThat(pipeline.getDataSinkIds()).containsExactly(SINK_ID);
    assertThat(pipeline.getModel()).doesNotContainKey("x-ui-styles").containsKey("edges");
    List<Map<String, Object>> nodes = nodesOf(pipeline);
    assertThat(nodes.get(1))
        .containsEntry("sourceRef", SOURCE_LOGICAL_URN + ":latest")
        .containsKey("x-ui-position");
    assertThat(nodes.get(2)).containsEntry("mappingRef", MAPPING_VERSIONED_URN);
    assertThat(nodes.get(3)).containsEntry("sinkRef", SINK_VERSIONED_URN);
    assertThat(installation.getArtifacts())
        .extracting(InstalledArtifact::getArtifactType)
        .containsExactly(
            InstalledArtifactType.DATA_STRUCTURE,
            InstalledArtifactType.DATA_SOURCE,
            InstalledArtifactType.DATA_SET,
            InstalledArtifactType.MAPPING,
            InstalledArtifactType.DATA_SINK,
            InstalledArtifactType.PIPELINE);
  }

  @Test
  void install_whenPackageHasAMap_createsTheLayerWithTheIdsOfTheCopies() {
    stubDatapool();
    stubDataSet();
    stubDataSink();
    stubStyles();
    stubLayer();
    stubSave();

    Installation installation =
        installationService.install(
            request(
                DATAPOOL_ID,
                layer("trees", GREEN_STYLE_URN, List.of(ORANGE_STYLE_URN)),
                style(ORANGE_STYLE_URN, "trees-orange"),
                style(GREEN_STYLE_URN, "trees-green"),
                frostSink(),
                dataSet()));

    verify(layerService).create(layerCaptor.capture());
    LayerInputDTO layer = layerCaptor.getValue();
    assertThat(layer.getDataSetId()).isEqualTo(DATASET_ID);
    assertThat(layer.getDataSinkId()).isEqualTo(SINK_ID);
    assertThat(layer.getDefaultStyleId()).isEqualTo(GREEN_ID);
    assertThat(layer.getAlternativeStyleIds()).containsExactly(ORANGE_ID);
    assertThat(layer.getLayerName()).isEqualTo("trees");
    assertThat(layer.getTitle()).isEqualTo("Street trees");
    assertThat(layer.getAttribute()).containsExactly("treeId", "position");
    assertThat(layer.getNativeBoundingBox()).containsEntry("minX", 7.625);
    assertThat(installation.getArtifacts())
        .extracting(InstalledArtifact::getArtifactType, InstalledArtifact::getName)
        .containsExactly(
            tuple(InstalledArtifactType.DATA_SET, "Trees"),
            tuple(InstalledArtifactType.DATA_SINK, "Sensor store"),
            tuple(InstalledArtifactType.STYLE, "trees-orange"),
            tuple(InstalledArtifactType.STYLE, "trees-green"),
            tuple(InstalledArtifactType.LAYER, "trees"));
  }

  @Test
  void install_whenMappingHasNoDataSet_changesNothing() {
    assertThatThrownBy(() -> installationService.install(request(tree(), mapping())))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("it has no Dataset");

    verifyNoChange();
  }

  @Test
  void install_whenPipelineReferencesAMemberOfAnotherKind_changesNothing() {
    stubDatapool();

    assertThatThrownBy(
            () ->
                installationService.install(
                    request(
                        DATAPOOL_ID,
                        tree(),
                        sqlSource(FOREIGN_URN),
                        dataSet(),
                        mapping(),
                        pipeline(MAPPING_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("nodes[3].sinkRef")
        .hasMessageContaining("is not of the kind datasink");

    verifyNoChange();
  }

  @Test
  void install_whenLayerReferencesAStyleThatThePackageDoesNotHave_changesNothing() {
    stubDatapool();
    String unknown = "urn:core:standard:test:style:environment:other:z9y8x7w6v5";

    assertThatThrownBy(
            () ->
                installationService.install(
                    request(DATAPOOL_ID, dataSet(), frostSink(), layer("trees", unknown, null))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("defaultStyleRef")
        .hasMessageContaining("the package has no member with this identity");

    verifyNoChange();
  }

  @Test
  void install_whenSinkTypeIsUnknown_changesNothing() {
    stubDatapool();

    assertThatThrownBy(
            () -> installationService.install(request(DATAPOOL_ID, dataSet(), sink("kafka"))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("unknown sink type 'kafka'");

    verifyNoChange();
  }

  @Test
  void install_whenLayerNameIsInvalid_changesNothingAndNamesTheField() {
    stubDatapool();

    assertThatThrownBy(
            () ->
                installationService.install(
                    request(DATAPOOL_ID, dataSet(), frostSink(), layer("1trees", null, null))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining(LAYER_URN)
        .hasMessageContaining("field 'layerName'");

    verifyNoChange();
  }

  @Test
  void findByIdOrThrow_whenInstallationDoesNotExist_throwsResourceNotFound() {
    UUID unknownId = UUID.fromString("99999999-9999-4000-8000-000000000009");
    when(installationRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> installationService.findByIdOrThrow(unknownId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  /** The install failed before its first change: no record and no artifact. */
  private void verifyNoChange() {
    verify(installationRepository, never()).saveAndFlush(any());
    verifyNoArtifactCreated();
  }

  private void verifyNoArtifactCreated() {
    verifyNoInteractions(
        dataStructureService,
        dataStructureVersionService,
        dataSourceService,
        dataSetService,
        mappingService,
        dataSinkService,
        pipelineService,
        styleService,
        layerService);
  }

  private void stubCreate() {
    DataStructure structure = new DataStructure();
    structure.setId(STRUCTURE_ID);
    when(dataStructureService.create(any())).thenReturn(structure);
    DataStructureVersion version = new DataStructureVersion();
    version.setId(VERSION_ID);
    version.setModelUrn(VERSIONED_URN);
    when(dataStructureVersionService.create(any())).thenReturn(version);
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(LOGICAL_URN);
    stubSave();
  }

  private void stubSave() {
    when(installationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void stubDatapool() {
    when(dataPoolRepository.existsById(DATAPOOL_ID)).thenReturn(true);
  }

  private void stubDataSource() {
    DataSource source =
        DataSource.builder()
            .configurationLogicalUrn(SOURCE_LOGICAL_URN)
            .configurationUrn(SOURCE_LOGICAL_URN + ":1.0.0")
            .build();
    source.setId(SOURCE_ID);
    when(dataSourceService.create(any())).thenReturn(source);
  }

  private void stubDataSet() {
    NamedApi api = NamedApi.builder().slug("map").build();
    api.setId(API_ID);
    DataSet dataSet =
        DataSet.builder()
            .name("Trees")
            .manifestLogicalUrn("urn:core:platform:civitas:dataset:common:" + DATASET_ID)
            .build();
    dataSet.setId(DATASET_ID);
    dataSet.getNamedApis().add(api);
    when(dataSetService.create(any())).thenReturn(dataSet);
  }

  private void stubMapping() {
    when(mappingService.store(any(), any(), any()))
        .thenReturn(
            new ModelRegistryGateway.ModelPin(MAPPING_LOGICAL_URN, MAPPING_VERSIONED_URN, "1.0.0"));
  }

  private void stubDataSink() {
    DataSink sink = new DataSink();
    sink.setId(SINK_ID);
    sink.setConfigurationLogicalUrn(SINK_LOGICAL_URN);
    sink.setConfigurationUrn(SINK_VERSIONED_URN);
    when(dataSinkService.create(any())).thenReturn(sink);
  }

  private void stubPipeline() {
    Pipeline pipeline = Pipeline.builder().name("Tree import").build();
    pipeline.setId(PIPELINE_ID);
    when(pipelineService.create(any())).thenReturn(pipeline);
  }

  /** The style service gives each style the id that belongs to its name. */
  private void stubStyles() {
    when(styleService.create(any()))
        .thenAnswer(
            invocation -> {
              StyleInputDTO input = invocation.getArgument(0);
              Style style = new Style();
              style.setId("trees-green".equals(input.getName()) ? GREEN_ID : ORANGE_ID);
              style.setName(input.getName());
              return style;
            });
  }

  private void stubLayer() {
    Layer layer = new Layer();
    layer.setId(LAYER_ID);
    layer.setLayerName("trees");
    when(layerService.create(any())).thenReturn(layer);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> nodesOf(PipelineInputDTO pipeline) {
    return (List<Map<String, Object>>) pipeline.getModel().get("nodes");
  }

  /** A Mapping document as a package carries it, with its Data structures by their identity. */
  private static PackageMemberInputDTO mapping() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$schema", "https://civitasconnect.digital/core/mapping/v1");
    document.put("id", MAPPING_URN);
    document.put(TITLE, "Row to tree");
    document.put("source", FOREIGN_URN);
    document.put("target", FOREIGN_URN);
    document.put("fields", Map.of("$.treeId", "$.tree_id"));
    return member(PackageMemberKind.MAPPING, MAPPING_URN, null, null, document);
  }

  private static PackageMemberInputDTO postgisSink() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$schema", "https://civitasconnect.digital/core/datasink/v1");
    document.put("id", SINK_URN);
    document.put(TITLE, "Tree table");
    document.put("description", "Writes the trees into a table");
    document.put("connectionType", "postgis");
    document.put("tableName", "trees");
    document.put("element", FOREIGN_URN);
    return member(PackageMemberKind.DATASINK, SINK_URN, null, null, document);
  }

  private static PackageMemberInputDTO frostSink() {
    return member(
        PackageMemberKind.DATASINK,
        SINK_URN,
        null,
        null,
        Map.of(TITLE, "Sensor store", "connectionType", "frost", "port", "ThingTree"));
  }

  private static PackageMemberInputDTO sink(String connectionType) {
    return member(
        PackageMemberKind.DATASINK,
        SINK_URN,
        null,
        null,
        Map.of(TITLE, "Sensor store", "connectionType", connectionType));
  }

  /** A Pipeline document whose sink node references the member with the identity. */
  private static PackageMemberInputDTO pipeline(String sinkReference) {
    Map<String, Object> position = Map.of("x", 0, "y", 120);
    List<Map<String, Object>> nodes =
        List.of(
            Map.of("id", "n-start", "kind", "start", "label", "Start"),
            Map.of(
                "id",
                "n-source",
                "kind",
                "source",
                "label",
                "Tree database",
                "sourceRef",
                SOURCE_URN,
                "x-ui-position",
                position),
            Map.of(
                "id",
                "n-mapping",
                "kind",
                "mapping",
                "label",
                "Row to tree",
                "mappingRef",
                MAPPING_URN),
            Map.of("id", "n-sink", "kind", "sink", "label", "Tree table", "sinkRef", sinkReference),
            Map.of("id", "n-end", "kind", "end", "label", "End"));
    List<Map<String, Object>> edges =
        List.of(
            Map.of("id", "e-1", "source", "n-start", "target", "n-source"),
            Map.of("id", "e-2", "source", "n-source", "target", "n-mapping"),
            Map.of("id", "e-3", "source", "n-mapping", "target", "n-sink"),
            Map.of("id", "e-4", "source", "n-sink", "target", "n-end"));
    return member(
        PackageMemberKind.PIPELINE,
        PIPELINE_URN,
        "Tree import",
        null,
        Map.of("nodes", nodes, "edges", edges, "x-ui-styles", Map.of("nodes", List.of())));
  }

  private static PackageMemberInputDTO style(String urn, String name) {
    return member(
        PackageMemberKind.STYLE,
        urn,
        null,
        null,
        Map.of("name", name, "sldContent", "<StyledLayerDescriptor/>"));
  }

  /** A layer document with its references by the identities in the package. */
  private static PackageMemberInputDTO layer(
      String layerName, String defaultStyle, List<String> alternativeStyles) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("layerName", layerName);
    document.put(TITLE, "Street trees");
    document.put("keywords", List.of("tree"));
    document.put("sinkRef", SINK_URN);
    if (defaultStyle != null) {
      document.put("defaultStyleRef", defaultStyle);
    }
    if (alternativeStyles != null) {
      document.put("alternativeStyleRefs", alternativeStyles);
    }
    document.put("attribute", List.of("treeId", "position"));
    document.put("geometryColumnRef", "position");
    document.put("crs", "EPSG:4326");
    Map<String, Object> box = Map.of("minX", 7.625, "minY", 51.95, "maxX", 7.645, "maxY", 51.972);
    document.put("nativeBoundingBox", box);
    document.put("latLonBoundingBox", box);
    return member(PackageMemberKind.LAYER, LAYER_URN, null, null, document);
  }

  /** A Data structure document as a package from another instance carries it. */
  private static Map<String, Object> model() {
    return Map.of(
        "$schema",
        "https://json-schema.org/draft/2020-12/schema",
        "$id",
        FOREIGN_URN,
        TITLE,
        "Tree cadastre",
        "$defs",
        Map.of("Tree", Map.of("type", "object")));
  }

  private static PackageMemberInputDTO tree() {
    return member(FOREIGN_URN, "Tree", "Trees of the city", model());
  }

  /** A Data source document as a package carries it, with its Data structure by its identity. */
  private static PackageMemberInputDTO sqlSource(String element) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("$schema", "https://civitasconnect.digital/core/datasource/v1");
    document.put("id", SOURCE_URN);
    document.put(TITLE, "Tree database");
    document.put("description", "Reads the trees from the database of the city");
    document.put("connectionType", "sql");
    document.put("element", element);
    document.put("driver", "postgres");
    document.put("dsn", "postgres://trees-db:5432/city");
    document.put("table", "trees");
    document.put("columns", List.of("tree_id", "species"));
    document.put("user", "trees");
    document.put("password", "secret");
    return member(PackageMemberKind.DATASOURCE, SOURCE_URN, null, null, document);
  }

  private static PackageMemberInputDTO mqttSource(int qos, Map<String, Object> otherKeys) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("id", SOURCE_URN);
    document.put(TITLE, "Air quality feed");
    document.put("description", "Receives the measurements of the stations");
    document.put("connectionType", "mqtt");
    document.put("element", FOREIGN_URN);
    document.put("urls", List.of("tcp://broker:1883"));
    document.put("topics", List.of("air-quality/+"));
    document.put("qos", qos);
    document.putAll(otherKeys);
    return member(PackageMemberKind.DATASOURCE, SOURCE_URN, null, null, document);
  }

  private static PackageMemberInputDTO dataSet() {
    return member(
        PackageMemberKind.DATASET,
        DATASET_URN,
        "Trees",
        "Trees of the city",
        Map.of("openDataAccess", false));
  }

  private static PackageMemberInputDTO api(String slug) {
    return member(
        PackageMemberKind.API,
        API_URN,
        null,
        null,
        Map.of("name", "Tree map", "slug", slug, "standard", "OWS"));
  }

  private static PackageMemberInputDTO member(
      String urn, String name, String description, Map<String, Object> content) {
    return member(PackageMemberKind.DATASTRUCTURE, urn, name, description, content);
  }

  private static PackageMemberInputDTO member(
      PackageMemberKind kind,
      String urn,
      String name,
      String description,
      Map<String, Object> content) {
    PackageMemberInputDTO member = new PackageMemberInputDTO();
    member.setKind(kind);
    member.setUrn(urn);
    member.setName(name);
    member.setDescription(description);
    member.setContent(content);
    return member;
  }

  private static InstallationInputDTO request(PackageMemberInputDTO... members) {
    return request((UUID) null, members);
  }

  private static InstallationInputDTO request(UUID datapoolId, PackageMemberInputDTO... members) {
    PackageManifestInputDTO manifest = new PackageManifestInputDTO();
    manifest.setId(PACKAGE_ID);
    manifest.setVersion("1.0.0");
    manifest.setMembers(List.of(members));
    InstallationInputDTO input = new InstallationInputDTO();
    input.setDatapoolId(datapoolId);
    input.setPackageManifest(manifest);
    return input;
  }
}
