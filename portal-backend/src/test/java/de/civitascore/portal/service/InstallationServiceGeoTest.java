package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.InstallationInputDTO;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.input.PackageManifestInputDTO;
import de.civitascore.portal.model.input.PackageMemberInputDTO;
import de.civitascore.portal.model.input.PackageMemberKind;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.InstallationRepository;
import de.civitascore.portal.repository.PublishedStructureRepository;
import de.civitascore.portal.util.InvalidInputException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the geo members of a package: named APIs, styles and map layers. The services are
 * mocked, so these tests prove what the install hands them (the bound references, the validated
 * inputs, the journal), not the rules of the services themselves; the API tests do that.
 */
@ExtendWith(MockitoExtension.class)
class InstallationServiceGeoTest {

  private static final String PACKAGE_ID = "urn:openurbanapps:usecase:kiez-baeume";
  private static final String DATASET_URN =
      "urn:core:standard:openurbanapps:dataset:environment:kiezbaeume:aaaaaaaaaa";
  private static final String API_URN =
      "urn:core:standard:openurbanapps:api:environment:karte:bbbbbbbbbb";
  private static final String SINK_URN =
      "urn:core:standard:openurbanapps:datasink:environment:kiezbaeumetabelle:cccccccccc";
  private static final String STYLE_URN =
      "urn:core:standard:openurbanapps:style:environment:baeumegruen:dddddddddd";
  private static final String LAYER_URN =
      "urn:core:standard:openurbanapps:layer:environment:baeume:eeeeeeeeee";

  private static final UUID DATAPOOL_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID DATA_SET_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final UUID NAMED_API_ID = UUID.fromString("30000000-0000-4000-8000-000000000003");
  private static final UUID DATA_SINK_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
  private static final UUID STYLE_ID = UUID.fromString("50000000-0000-4000-8000-000000000005");
  private static final UUID LAYER_ID = UUID.fromString("60000000-0000-4000-8000-000000000006");

  @Mock private InstallationRepository installationRepository;
  @Mock private DataStructureService dataStructureService;
  @Mock private DataStructureVersionService dataStructureVersionService;
  @Mock private DataSourceService dataSourceService;
  @Mock private DataSetService dataSetService;
  @Mock private MappingService mappingService;
  @Mock private DataSinkService dataSinkService;
  @Mock private PipelineService pipelineService;
  @Mock private StyleService styleService;
  @Mock private LayerService layerService;
  @Mock private PublishedStructureRepository publishedStructureRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private UninstallGuard uninstallGuard;

  /** The real constraints, so a reserved slug or a bad layer name is refused as in production. */
  @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @InjectMocks private InstallationService installationService;

  @BeforeEach
  void stubPlatform() {
    // Package URNs carry no version, so the logical form is the URN itself.
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(installationRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(dataSetService.create(any()))
        .thenAnswer(
            invocation -> {
              DataSetInputDTO input = invocation.getArgument(0);
              DataSet dataSet = DataSet.builder().build();
              dataSet.setId(DATA_SET_ID);
              dataSet.setName(input.getName());
              List<NamedApi> apis = new ArrayList<>();
              if (input.getNamedApis() != null) {
                for (NamedApiInputDTO api : input.getNamedApis()) {
                  NamedApi created = new NamedApi();
                  created.setId(NAMED_API_ID);
                  created.setSlug(api.getSlug());
                  apis.add(created);
                }
              }
              dataSet.setNamedApis(apis);
              return dataSet;
            });
    lenient()
        .when(dataSinkService.create(any()))
        .thenAnswer(
            invocation -> {
              DataSink sink = new DataSink();
              sink.setId(DATA_SINK_ID);
              return sink;
            });
    lenient()
        .when(styleService.create(any()))
        .thenAnswer(
            invocation -> {
              StyleInputDTO input = invocation.getArgument(0);
              Style style = new Style();
              style.setId(STYLE_ID);
              style.setName(input.getName());
              return style;
            });
    lenient()
        .when(layerService.create(any()))
        .thenAnswer(
            invocation -> {
              LayerInputDTO input = invocation.getArgument(0);
              Layer layer = new Layer();
              layer.setId(LAYER_ID);
              layer.setLayerName(input.getLayerName());
              return layer;
            });
  }

  private static PackageMemberInputDTO member(
      PackageMemberKind kind, String urn, String name, Map<String, Object> content) {
    PackageMemberInputDTO member = new PackageMemberInputDTO();
    member.setKind(kind);
    member.setUrn(urn);
    member.setName(name);
    member.setDescription("Installed by the test");
    member.setContent(content);
    return member;
  }

  private static PackageMemberInputDTO dataset() {
    return member(
        PackageMemberKind.DATASET, DATASET_URN, "Kiez-Bäume", Map.of("openDataAccess", false));
  }

  private static PackageMemberInputDTO api(String slug) {
    return member(
        PackageMemberKind.API,
        API_URN,
        "Karte",
        Map.of("name", "Karte der Kiez-Bäume", "slug", slug, "standard", "ows"));
  }

  private static PackageMemberInputDTO sink() {
    return member(
        PackageMemberKind.DATASINK,
        SINK_URN,
        "Kiez-Bäume-Tabelle",
        Map.of("connectionType", "postgis", "tableName", "kiez_baeume"));
  }

  private static PackageMemberInputDTO style() {
    return member(
        PackageMemberKind.STYLE,
        STYLE_URN,
        "baeume-gruen",
        Map.of("name", "baeume-gruen", "sldContent", "<StyledLayerDescriptor/>"));
  }

  private static PackageMemberInputDTO layer(String layerName, String sinkRef) {
    return member(
        PackageMemberKind.LAYER,
        LAYER_URN,
        layerName,
        Map.of(
            "layerName",
            layerName,
            "title",
            "Straßenbäume",
            "sinkRef",
            sinkRef,
            "defaultStyleRef",
            STYLE_URN,
            "alternativeStyleRefs",
            List.of(STYLE_URN),
            "attribute",
            List.of("art", "pflanzjahr")));
  }

  private static InstallationInputDTO installing(PackageMemberInputDTO... members) {
    PackageManifestInputDTO manifest = new PackageManifestInputDTO();
    manifest.setId(PACKAGE_ID);
    manifest.setVersion("1.0.0");
    manifest.setMembers(List.of(members));
    InstallationInputDTO input = new InstallationInputDTO();
    input.setPackageManifest(manifest);
    input.setDatapoolId(DATAPOOL_ID);
    return input;
  }

  @Test
  void install_handsTheApiToTheDatasetAtCreation() {
    installationService.install(installing(dataset(), api("karte"), sink()));

    ArgumentCaptor<DataSetInputDTO> created = ArgumentCaptor.forClass(DataSetInputDTO.class);
    verify(dataSetService).create(created.capture());
    assertThat(created.getValue().getNamedApis())
        .singleElement()
        .satisfies(
            api -> {
              assertThat(api.getSlug()).isEqualTo("karte");
              assertThat(api.getStandard()).isEqualTo(ApiStandard.OWS);
              assertThat(api.getName()).isEqualTo("Karte der Kiez-Bäume");
            });
  }

  @Test
  void install_bindsTheLayerToTheCopiesOfItsSinkAndStyles() {
    installationService.install(
        installing(dataset(), api("karte"), sink(), style(), layer("baeume", SINK_URN)));

    ArgumentCaptor<StyleInputDTO> style = ArgumentCaptor.forClass(StyleInputDTO.class);
    verify(styleService).create(style.capture());
    assertThat(style.getValue().getDataSetId()).isEqualTo(DATA_SET_ID);
    assertThat(style.getValue().getSldContent()).isEqualTo("<StyledLayerDescriptor/>");

    ArgumentCaptor<LayerInputDTO> layer = ArgumentCaptor.forClass(LayerInputDTO.class);
    verify(layerService).create(layer.capture());
    assertThat(layer.getValue().getDataSetId()).isEqualTo(DATA_SET_ID);
    assertThat(layer.getValue().getDataSinkId()).isEqualTo(DATA_SINK_ID);
    assertThat(layer.getValue().getDefaultStyleId()).isEqualTo(STYLE_ID);
    assertThat(layer.getValue().getAlternativeStyleIds()).containsExactly(STYLE_ID);
    assertThat(layer.getValue().getAttribute()).containsExactly("art", "pflanzjahr");
    assertThat(layer.getValue().getTitle()).isEqualTo("Straßenbäume");
  }

  @Test
  void install_recordsTheGeoMembersInTheJournal() {
    Installation installation =
        installationService.install(
            installing(dataset(), api("karte"), sink(), style(), layer("baeume", SINK_URN)));

    assertThat(installation.getArtifacts())
        .extracting(InstalledArtifact::getArtifactType)
        .containsExactly(
            InstalledArtifactType.DATA_SET,
            InstalledArtifactType.NAMED_API,
            InstalledArtifactType.DATA_SINK,
            InstalledArtifactType.STYLE,
            InstalledArtifactType.LAYER);
    assertThat(installation.getArtifacts())
        .filteredOn(line -> line.getArtifactType() == InstalledArtifactType.NAMED_API)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getShellId()).isEqualTo(NAMED_API_ID);
              assertThat(line.getOrigin()).isEqualTo(API_URN);
            });
  }

  @Test
  void install_refusesAReservedSlugBeforeTheDatasetExists() {
    assertThatThrownBy(() -> installationService.install(installing(dataset(), api("admin"))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("slug")
        .hasMessageContaining("reserved");

    verify(dataSetService, never()).create(any());
  }

  @Test
  void install_refusesAnApiWhenThePackageShipsNoDataset() {
    assertThatThrownBy(() -> installationService.install(installing(api("karte"))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("ships none");

    verify(installationRepository, never()).saveAndFlush(any());
  }

  @Test
  void install_refusesALayerWhoseSinkIsNotInThePackage() {
    String foreignSink =
        "urn:core:standard:openurbanapps:datasink:environment:fremdetabelle:ffffffffff";

    assertThatThrownBy(
            () ->
                installationService.install(
                    installing(dataset(), sink(), style(), layer("baeume", foreignSink))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("sinkRef");

    verify(layerService, never()).create(any());
  }

  @Test
  void install_refusesALayerNameGeoServerCannotPublish() {
    assertThatThrownBy(
            () ->
                installationService.install(
                    installing(dataset(), sink(), style(), layer("1-baeume", SINK_URN))))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("layerName");

    verify(layerService, never()).create(any());
  }
}
