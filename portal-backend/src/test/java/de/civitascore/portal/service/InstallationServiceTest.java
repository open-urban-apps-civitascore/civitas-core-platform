package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
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

  @Mock private InstallationRepository installationRepository;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Spy private ConnectorValidator connectorValidator = new ConnectorValidator(List.of());
  @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @InjectMocks private InstallationService installationService;

  @Captor private ArgumentCaptor<DataStructureInputDTO> structureCaptor;
  @Captor private ArgumentCaptor<DataStructureVersionInputDTO> versionCaptor;
  @Captor private ArgumentCaptor<DataSourceInputDTO> sourceCaptor;
  @Captor private ArgumentCaptor<DataSetInputDTO> dataSetCaptor;

  /** The identities in these tests have no version, so the registry gives them back unchanged. */
  @BeforeEach
  void keepIdentities() {
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void install_whenPackageIsInstalled_throwsUniqueConstraintViolation() {
    when(installationRepository.existsByPackageId(PACKAGE_ID)).thenReturn(true);

    assertThatThrownBy(() -> installationService.install(request(tree())))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("is already installed");

    verifyNoChange();
  }

  @Test
  void install_whenUniqueIndexRefusesTheRecord_createsNothing() {
    // The check passes, because the other install has not committed yet. The index refuses the row.
    when(installationRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_installations_package"));

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
        dataStructureService, dataStructureVersionService, dataSourceService, dataSetService);
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
