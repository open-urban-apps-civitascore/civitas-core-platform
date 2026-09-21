package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.configuration.CivitasProperties;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.model.input.validation.NamedApiAllowedSlugValidator;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.DataSetService;
import de.civitascore.portal.util.RestPage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSet Controller Integration Tests")
class DataSetControllerIntegrationTest
    extends BaseDataEntityControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private final String DATASETS_ENDPOINT = "/datasets";

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSetService dataSetService;
  @Autowired private CivitasProperties civitasProperties;
  @Autowired private org.springframework.transaction.support.TransactionTemplate txTemplate;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  /** Helper to construct a {@link NamedApiInputDTO} with the four explicit fields. */
  private NamedApiInputDTO namedApi(
      String name, String slug, ApiStandard standard, String version) {
    NamedApiInputDTO dto = new NamedApiInputDTO();
    dto.setName(name);
    dto.setSlug(slug);
    dto.setStandard(standard);
    dto.setVersion(version);
    return dto;
  }

  /** Helper method to create a sample styles map for Pipeline. */
  private Map<String, Object> createSampleStyles() {
    Map<String, Object> styles = new HashMap<>();
    styles.put("nodes", List.of());
    styles.put("edges", List.of());
    Map<String, Object> viewport = new HashMap<>();
    viewport.put("x", 0);
    viewport.put("y", 0);
    viewport.put("zoom", 1);
    styles.put("viewport", viewport);
    return styles;
  }

  /**
   * Helper method to create a sample CORE Pipeline model map. The stored model is validated against
   * pipeline.schema.json (top-level {@code nodes}/{@code edges} required; Model Forge stamps {@code
   * $schema}/{@code id}), so the fixture emits a clean, schema-valid document.
   */
  private Map<String, Object> createSampleModel() {
    Map<String, Object> model = new HashMap<>();
    model.put(
        "nodes",
        List.of(
            Map.of("id", "start-1", "kind", "start", "label", "Start"),
            Map.of("id", "end-1", "kind", "end", "label", "End")));
    model.put("edges", List.of(Map.of("id", "edge-1", "source", "start-1", "target", "end-1")));
    return model;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  /** Seeds the minimum stage requirement: one datasource linked to the first pipeline. */
  private void seedStageRequirements(Pipeline... pipelines) {
    DataSource dataSource = new DataSource();
    dataSource.setName("stage-datasource-" + System.nanoTime());
    dataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    dataSource = dataSourceRepository.save(dataSource);
    pipelines[0].getDataSources().add(dataSource);
    pipelineRepository.save(pipelines[0]);
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("test_dataset_" + System.currentTimeMillis());
    input.setDescription("A test dataset for integration testing");
    return input;
  }

  @Override
  protected DataSetInputDTO createInvalidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setDescription("Invalid dataset without required fields");
    return input;
  }

  @Override
  protected DataSetInputDTO createUpdateInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("Updated DataSet");
    input.setDescription("Updated description");
    input.setOpenDataAccess(false);
    return input;
  }

  /**
   * Creates a DataSet in DRAFT status with pre-existing relationships.
   *
   * @return UUID of the created DataSet
   */
  private DataSet createDataSetWithRelationships() {
    // Create a User to be the owner of the dataset
    User owner = new User();
    owner.setFirstName("Test");
    owner.setLastName("Owner");
    owner.setEmail("test.owner." + System.currentTimeMillis() + "@example.com");
    owner.setExternalId("ext-user-" + System.currentTimeMillis());
    owner.setActive(true);
    owner = userRepository.save(owner);

    DataSet dataSet = new DataSet();
    dataSet.setName("test_dataset_with_relationships_" + System.currentTimeMillis());
    dataSet.setDescription("Test dataset with pipelines and distributions");
    dataSet.setDataSetStatus(DataSetStatus.DRAFT);
    dataSet.setIdentifier("test-identifier-001");
    dataSet.setVersion("1.0.0");
    dataSet.setExternalId("ext-dataset-" + System.currentTimeMillis());
    dataSet.setFormat("JSON");
    dataSet.setOpenDataAccess(false);
    dataSet.setOwner(owner);
    dataSet = dataSetRepository.save(dataSet);

    // Create data sources for the pipelines
    DataSource dataSource1 = new DataSource();
    dataSource1.setName("test_data_source_1_" + System.currentTimeMillis());
    dataSource1.setDescription("Test data source 1");
    dataSource1 = dataSourceRepository.save(dataSource1);

    DataSource dataSource2 = new DataSource();
    dataSource2.setName("test_data_source_2_" + System.currentTimeMillis());
    dataSource2.setDescription("Test data source 2");
    dataSource2 = dataSourceRepository.save(dataSource2);

    DataSource dataSource3 = new DataSource();
    dataSource3.setName("test_data_source_3_" + System.currentTimeMillis());
    dataSource3.setDescription("Test data source 3");
    dataSource3 = dataSourceRepository.save(dataSource3);

    DataSource dataSource4 = new DataSource();
    dataSource4.setName("test_data_source_4_" + System.currentTimeMillis());
    dataSource4.setDescription("Test data source 4");
    dataSource4 = dataSourceRepository.save(dataSource4);

    // Create pipelines for the dataset
    Pipeline pipeline1 = new Pipeline();
    pipeline1.setName("test_pipeline_1_" + System.currentTimeMillis());
    pipeline1.setDescription("Test pipeline 1");
    pipeline1.setDataSet(dataSet);
    pipeline1.getDataSources().add(dataSource1);
    pipeline1.getDataSources().add(dataSource2);

    pipeline1 = pipelineRepository.save(pipeline1);
    pipeline1 =
        portalData.attachPipelineDefinition(pipeline1, createSampleModel(), createSampleStyles());

    Pipeline pipeline2 = new Pipeline();
    pipeline2.setName("test_pipeline_2_" + System.currentTimeMillis());
    pipeline2.setDescription("Test pipeline 2");
    pipeline2.setDataSet(dataSet);
    pipeline2.getDataSources().add(dataSource3);
    pipeline2.getDataSources().add(dataSource4);

    pipeline2 = pipelineRepository.save(pipeline2);
    pipeline2 =
        portalData.attachPipelineDefinition(pipeline2, createSampleModel(), createSampleStyles());

    // Create distributions for the dataset
    Distribution distribution1 = new Distribution();
    distribution1.setAccessUrl("http://localhost:8080/api/v1/traffic");
    distribution1.setApiType("SensorThings");
    distribution1.setFormat("application/json");
    distribution1.setAutoGenerated(true);
    distribution1.setDataSet(dataSet);
    distribution1 = distributionRepository.save(distribution1);

    Distribution distribution2 = new Distribution();
    distribution2.setAccessUrl("http://localhost:8080/api/v1/weather");
    distribution2.setApiType("SensorThings");
    distribution2.setFormat("application/json");
    distribution2.setAutoGenerated(true);
    distribution2.setDataSet(dataSet);
    distribution2 = distributionRepository.save(distribution2);

    dataSet.setPipelines(List.of(pipeline1, pipeline2));
    dataSet.setDistributions(Set.of(distribution1, distribution2));

    return dataSetRepository.save(dataSet);
  }

  @Override
  protected ParameterizedTypeReference<DataSetOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSetOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSetOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create DataSet Tests")
  class CreateDataSetTests {

    @Test
    @DisplayName("Should create dataset successfully with valid data")
    void shouldCreateDataSetSuccessfully() {
      DataSetInputDTO input = createValidInput();

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getDataSetStatus())
          .as("Status should match input")
          .isEqualTo(DataSetStatus.DRAFT);
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo(input.getDescription());
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create dataset with missing required fields")
    void shouldFailToCreateDataSetWithMissingFields() {
      DataSetInputDTO input = createInvalidInput();

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create dataset without authentication")
    void shouldFailToCreateDataSetWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create dataset with metadata")
    void shouldCreateDataSetWithMetadata() {
      DataSetInputDTO input = createValidInput();

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should round-trip namedApis with server-populated previewUrl")
    void shouldRoundTripNamedApisWithPreviewUrl() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic Sensor Readings", "traffic", ApiStandard.STA, "1.1"),
              namedApi("Weather Sensor Readings", "weather", ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> create = performCreate(input);
      assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      DataSetOutputDTO created = create.getBody();
      assertThat(created).isNotNull();
      assertThat(created.getId()).isNotNull();

      String baseUrl = civitasProperties.api().baseUrl();
      String trafficUrl = baseUrl + "/v1/datasets/" + created.getId() + "/traffic";
      String weatherUrl = baseUrl + "/v1/datasets/" + created.getId() + "/weather";

      // POST response: namedApis populated with previewUrl
      assertThat(created.getNamedApis())
          .extracting(
              NamedApiOutputDTO::getSlug,
              NamedApiOutputDTO::getStandard,
              NamedApiOutputDTO::getPreviewUrl)
          .containsExactlyInAnyOrder(
              tuple("traffic", ApiStandard.STA, trafficUrl),
              tuple("weather", ApiStandard.STA, weatherUrl));

      // GET round-trip returns the same shape
      ResponseEntity<DataSetOutputDTO> get = performGetById(created.getId());
      assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(get.getBody()).isNotNull();
      assertThat(get.getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getPreviewUrl)
          .containsExactlyInAnyOrder(tuple("traffic", trafficUrl), tuple("weather", weatherUrl));

      // GET-all (paginated) returns namedApis on each result (#1315 AC)
      ResponseEntity<de.civitascore.portal.util.RestPage<DataSetOutputDTO>> page = performGetAll();
      assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(page.getBody()).isNotNull();
      DataSetOutputDTO fromPage =
          page.getBody().getContent().stream()
              .filter(d -> created.getId().equals(d.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(fromPage.getNamedApis())
          .as("paginated GET must include namedApis with previewUrl")
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getPreviewUrl)
          .containsExactlyInAnyOrder(tuple("traffic", trafficUrl), tuple("weather", weatherUrl));
    }

    @Test
    @DisplayName("Should replace namedApis list on PATCH (array replace, not merge)")
    void shouldReplaceNamedApisOnPatch() {
      // Setup: dataset with two namedApis
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, null),
              namedApi("Weather", "weather", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      UUID id = created.getId();

      // PATCH with single-entry namedApis array — must REPLACE, not merge into existing entries
      Map<String, Object> patchBody =
          Map.of(
              "namedApis",
              List.of(Map.of("name", "Air Quality", "slug", "air", "standard", "STA")));

      ResponseEntity<DataSetOutputDTO> patch = performPatch(id, patchBody);
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getNamedApis())
          .as("PATCH with namedApis array replaces the list — no traffic/weather, only air")
          .extracting(NamedApiOutputDTO::getSlug)
          .containsExactly("air");

      // GET confirms persistence
      assertThat(performGetById(id).getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug)
          .containsExactly("air");
    }

    @Test
    @DisplayName("Should replace namedApis with same slugs (no unique-constraint violation)")
    void shouldReplaceNamedApisWithSameSlugs() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, null),
              namedApi("Weather", "weather", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();

      // PATCH with the same slugs but different display name + description.
      // Orphan removal of old rows + insert of new rows must not collide on
      // uk_named_api_dataset_slug.
      Map<String, Object> patchBody =
          Map.of(
              "namedApis",
              List.of(
                  Map.of(
                      "name", "Traffic Sensor Readings",
                      "slug", "traffic",
                      "standard", "STA",
                      "description", "Live traffic counter readings."),
                  Map.of(
                      "name", "Weather Sensor Readings",
                      "slug", "weather",
                      "standard", "STA")));

      ResponseEntity<DataSetOutputDTO> patch = performPatch(created.getId(), patchBody);
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getName)
          .containsExactlyInAnyOrder(
              tuple("traffic", "Traffic Sensor Readings"),
              tuple("weather", "Weather Sensor Readings"));
    }

    @Test
    @DisplayName("Should replace (not merge) entry fields on PATCH with same slug")
    void shouldReplaceEntryFieldsOnPatchWithSameSlug() {
      // PATCH replaces by slug: a non-empty namedApis list is the new source of truth, including
      // the optional fields of each entry. Omitting version/description on a slug that previously
      // carried them clears them. To preserve a field, the client must re-send it.
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO traffic = namedApi("Traffic", "traffic", ApiStandard.STA, "1.1");
      traffic.setDescription("Live traffic counter readings.");
      input.setNamedApis(List.of(traffic));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();

      Map<String, Object> patchBody =
          Map.of(
              "namedApis",
              List.of(
                  Map.of(
                      "name", "Traffic v2",
                      "slug", "traffic",
                      "standard", "STA")));

      ResponseEntity<DataSetOutputDTO> patch = performPatch(created.getId(), patchBody);
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getNamedApis())
          .extracting(
              NamedApiOutputDTO::getSlug,
              NamedApiOutputDTO::getName,
              NamedApiOutputDTO::getVersion,
              NamedApiOutputDTO::getDescription)
          .containsExactly(tuple("traffic", "Traffic v2", null, null));
    }

    @Test
    @DisplayName("Should clear namedApis on PATCH with empty array")
    void shouldClearNamedApisOnPatchWithEmptyArray() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", "traffic", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();

      ResponseEntity<DataSetOutputDTO> patch =
          performPatch(created.getId(), Map.of("namedApis", List.of()));
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getNamedApis()).isEmpty();
      assertThat(performGetById(created.getId()).getBody().getNamedApis()).isEmpty();
    }

    @Test
    @DisplayName("Should reject namedApis entry with blank name")
    void shouldRejectBlankNamedApiName() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO bad = new NamedApiInputDTO();
      bad.setName("   "); // whitespace, exercises @NotBlank (vs @NotNull)
      bad.setSlug("traffic");
      bad.setStandard(ApiStandard.STA);
      input.setNamedApis(List.of(bad));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode())
          .as("blank name on a namedApis entry should be rejected via @Valid cascade")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject namedApis entry with blank slug")
    void shouldRejectBlankNamedApiSlug() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO bad = new NamedApiInputDTO();
      bad.setName("Traffic");
      bad.setSlug("");
      bad.setStandard(ApiStandard.STA);
      input.setNamedApis(List.of(bad));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject namedApis entry with blank standard")
    void shouldRejectBlankNamedApiStandard() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO bad = new NamedApiInputDTO();
      bad.setName("Traffic");
      bad.setSlug("traffic");
      bad.setStandard(null);
      input.setNamedApis(List.of(bad));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "[{index}] slug={0}")
    @ValueSource(
        strings = {
          "Traffic-Counter", // uppercase
          "-traffic", // leading hyphen
          "traffic-", // trailing hyphen
          "traffic_counter", // underscore
          "traffic.counter", // dot
          "traffic counter", // space
        })
    @DisplayName("Should reject namedApis entry with malformed slug")
    void shouldRejectMalformedNamedApiSlug(String slug) {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", slug, ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    static Stream<String> reservedSlugs() {
      return NamedApiAllowedSlugValidator.RESERVED.stream();
    }

    @ParameterizedTest(name = "[{index}] slug={0}")
    @MethodSource("reservedSlugs")
    @DisplayName("Should reject namedApis entry with reserved slug (collides with platform path)")
    void shouldRejectReservedNamedApiSlug(String slug) {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", slug, ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject reserved slug on PUT update too (constraint wired on every @Valid)")
    void shouldRejectReservedNamedApiSlugOnUpdate() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", "traffic", ApiStandard.STA, null)));
      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();

      DataSetInputDTO updateBody = createValidInput();
      updateBody.setName(created.getName());
      updateBody.setNamedApis(List.of(namedApi("Apis", "apis", ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response = performUpdate(created.getId(), updateBody);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should accept single-character slug (regex boundary case)")
    void shouldAcceptSingleCharSlug() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", "a", ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should reject namedApis entry with slug over 32 characters")
    void shouldRejectOverlongNamedApiSlug() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", "a".repeat(33), ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject namedApis entry with unknown standard value")
    void shouldRejectUnknownNamedApiStandard() {
      // Raw-map body so we can post a value outside the ApiStandard enum and exercise the
      // Jackson-level rejection that returns 400 before bean validation.
      Map<String, Object> body =
          Map.of(
              "name",
              "DataSet with bad standard",
              "namedApis",
              List.of(
                  Map.of(
                      "name", "Traffic",
                      "slug", "traffic",
                      "standard", "OGCAPI")));

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath(),
              HttpMethod.POST,
              createAuthHeaders(),
              body,
              getOutputTypeReference());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject when an invalid entry follows a valid entry (cascade visits all)")
    void shouldRejectWhenSecondNamedApiEntryIsInvalid() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO good = new NamedApiInputDTO();
      good.setName("Traffic");
      good.setSlug("traffic");
      good.setStandard(ApiStandard.STA);
      NamedApiInputDTO bad = new NamedApiInputDTO();
      bad.setName("");
      bad.setSlug("weather");
      bad.setStandard(ApiStandard.STA);
      input.setNamedApis(List.of(good, bad));

      // Pins that @Valid cascade visits every element, not just the first. A future regression
      // (e.g. someone short-circuits validation on first valid entry) would not be caught by the
      // single-entry tests above.
      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should round-trip null version alongside a populated version")
    void shouldRoundTripNullVersion() {
      DataSetInputDTO input = createValidInput();
      // version on weather is intentionally null — must round-trip as null, not empty string
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, "1.1"),
              namedApi("Weather", "weather", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      assertThat(created.getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getVersion)
          .containsExactlyInAnyOrder(tuple("traffic", "1.1"), tuple("weather", null));

      DataSetOutputDTO refetched = performGetById(created.getId()).getBody();
      assertThat(refetched).isNotNull();
      assertThat(refetched.getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getVersion)
          .containsExactlyInAnyOrder(tuple("traffic", "1.1"), tuple("weather", null));
    }

    @Test
    @DisplayName("Should round-trip optional description alongside a null description")
    void shouldRoundTripDescription() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO traffic = namedApi("Traffic", "traffic", ApiStandard.STA, "1.1");
      traffic.setDescription("Live traffic counter readings from city sensors.");
      NamedApiInputDTO weather = namedApi("Weather", "weather", ApiStandard.STA, "1.1");
      // weather.description intentionally null — must round-trip as null

      input.setNamedApis(List.of(traffic, weather));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      assertThat(created.getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getDescription)
          .containsExactlyInAnyOrder(
              tuple("traffic", "Live traffic counter readings from city sensors."),
              tuple("weather", null));

      DataSetOutputDTO refetched = performGetById(created.getId()).getBody();
      assertThat(refetched).isNotNull();
      assertThat(refetched.getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getDescription)
          .containsExactlyInAnyOrder(
              tuple("traffic", "Live traffic counter readings from city sensors."),
              tuple("weather", null));
    }

    @Test
    @DisplayName("Should reject namedApis entry with description over 150 characters")
    void shouldRejectOverlongDescription() {
      DataSetInputDTO input = createValidInput();
      NamedApiInputDTO bad = namedApi("Traffic", "traffic", ApiStandard.STA, null);
      bad.setDescription("x".repeat(151));
      input.setNamedApis(List.of(bad));

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should preserve NamedApi rows (id + routeId) when PATCH body omits namedApis")
    void shouldLeaveNamedApisUntouchedWhenPatchOmitsField() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(List.of(namedApi("Traffic", "traffic", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      UUID dataSetId = created.getId();

      // Seed the saga-populated routeId directly on the entity (the saga normally does this
      // post-release; we shortcut that here so we can assert the PATCH preserves it).
      // namedApis is FetchType.LAZY, so the read + mutation happens inside a transactional
      // boundary. Capture the entry id for later equality assertion.
      UUID originalEntryId =
          txTemplate.execute(
              status -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                NamedApi traffic = ds.getNamedApis().iterator().next();
                traffic.setRouteId("route-saga-1");
                return traffic.getId();
              });
      assertThat(originalEntryId).isNotNull();

      // PATCH only the description — namedApis must stay intact (slug, id, AND routeId)
      ResponseEntity<DataSetOutputDTO> patch =
          performPatch(dataSetId, Map.of("description", "patched"));
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getDescription()).isEqualTo("patched");
      assertThat(patch.getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug)
          .containsExactly("traffic");

      // Re-fetch to verify the underlying entity row was not destroyed and recreated.
      // findById eagerly loads namedApis via @EntityGraph, so no transaction wrapper needed.
      DataSet afterPatch = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(afterPatch.getNamedApis())
          .as("PATCH that omits namedApis must preserve entity rows (same id, same routeId)")
          .singleElement()
          .satisfies(
              api -> {
                assertThat(api.getId()).isEqualTo(originalEntryId);
                assertThat(api.getRouteId()).isEqualTo("route-saga-1");
              });
    }

    @Test
    @DisplayName(
        "Should partially overlap namedApis on PATCH (preserve overlap, drop missing, add new)")
    void shouldPartiallyOverlapNamedApisOnPatch() {
      // Pins the partial-overlap merge path distinct from the all-overlap
      // (shouldReplaceNamedApisWithSameSlugs) and no-overlap (shouldReplaceNamedApisOnPatch) cases:
      // overlapping slugs must keep their id + saga-populated routeId.
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, null),
              namedApi("Weather", "weather", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      UUID dataSetId = created.getId();

      UUID trafficOriginalId =
          txTemplate.execute(
              status -> {
                DataSet ds = dataSetRepository.findById(dataSetId).orElseThrow();
                NamedApi traffic =
                    ds.getNamedApis().stream()
                        .filter(api -> "traffic".equals(api.getSlug()))
                        .findFirst()
                        .orElseThrow();
                traffic.setRouteId("route-saga-traffic");
                return traffic.getId();
              });

      Map<String, Object> patchBody =
          Map.of(
              "namedApis",
              List.of(
                  Map.of("name", "Traffic v2", "slug", "traffic", "standard", "STA"),
                  Map.of("name", "Air Quality", "slug", "air", "standard", "STA")));

      ResponseEntity<DataSetOutputDTO> patch = performPatch(dataSetId, patchBody);
      assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patch.getBody()).isNotNull();
      assertThat(patch.getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getName)
          .containsExactlyInAnyOrder(tuple("traffic", "Traffic v2"), tuple("air", "Air Quality"));

      DataSet afterPatch = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(afterPatch.getNamedApis())
          .anySatisfy(
              api -> {
                assertThat(api.getSlug()).isEqualTo("traffic");
                assertThat(api.getId()).isEqualTo(trafficOriginalId);
                assertThat(api.getRouteId()).isEqualTo("route-saga-traffic");
              })
          .anySatisfy(
              api -> {
                assertThat(api.getSlug()).isEqualTo("air");
                assertThat(api.getRouteId()).isNull();
              })
          .extracting(NamedApi::getSlug)
          .doesNotContain("weather");
    }

    @Test
    @DisplayName("Should replace namedApis on PUT with the supplied list")
    void shouldReplaceNamedApisOnPut() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, null),
              namedApi("Weather", "weather", ApiStandard.STA, null)));

      DataSetOutputDTO created = performCreate(input).getBody();
      assertThat(created).isNotNull();
      UUID dataSetId = created.getId();

      DataSetInputDTO putBody = createValidInput();
      putBody.setName(created.getName());
      putBody.setNamedApis(
          List.of(
              namedApi("Traffic v2", "traffic", ApiStandard.STA, "1.1"),
              namedApi("Air Quality", "air", ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> put = performUpdate(dataSetId, putBody);
      assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(put.getBody()).isNotNull();
      assertThat(put.getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getName)
          .containsExactlyInAnyOrder(tuple("traffic", "Traffic v2"), tuple("air", "Air Quality"));

      assertThat(performGetById(dataSetId).getBody().getNamedApis())
          .extracting(NamedApiOutputDTO::getSlug)
          .containsExactlyInAnyOrder("traffic", "air");
    }

    @Test
    @DisplayName("Should populate createdBy with user name when creator exists in database")
    void shouldPopulateCreatedByWhenCreatorExistsInDatabase() {
      String keycloakId = getUsersResource().search("testuser", true).get(0).getId();

      User creator = new User();
      creator.setFirstName("Test");
      creator.setLastName("User");
      creator.setEmail("testuser.creator." + System.currentTimeMillis() + "@example.com");
      creator.setExternalId(keycloakId);
      creator.setActive(true);
      userRepository.save(creator);

      ResponseEntity<DataSetOutputDTO> response = performCreate(createValidInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getCreatedBy()).as("createdBy should be populated").isNotNull();
      assertThat(output.getCreatedBy().getId())
          .as("createdBy.id should match the creator's database UUID")
          .isEqualTo(creator.getId());
      assertThat(output.getCreatedBy().getName())
          .as("createdBy.name should be first + last name")
          .isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should return null createdBy when creator is not found in database")
    void shouldReturnNullCreatedByWhenCreatorNotInDatabase() {
      ResponseEntity<DataSetOutputDTO> response = performCreate(createValidInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getCreatedBy())
          .as("createdBy should be null when no matching User exists")
          .isNull();
    }

    @Test
    @DisplayName("Should allow creating datasets with the same name")
    void shouldAllowDuplicateDataSetName() {
      // Name uniqueness was removed (#1453) to close a create-oracle: read access on
      // DataSets is scope-restricted but create access is global, so a uniqueness
      // violation leaked the existence of records the caller had no permission to read.
      DataSetInputDTO input = createValidInput();
      String name = input.getName();
      ResponseEntity<DataSetOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataSetInputDTO duplicate = createValidInput();
      duplicate.setName(name);
      ResponseEntity<DataSetOutputDTO> secondResponse = performCreate(duplicate);

      assertThat(secondResponse.getStatusCode())
          .as("Duplicate name must be accepted to avoid create-oracle leak")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(secondResponse.getBody().getId()).isNotEqualTo(firstResponse.getBody().getId());
      assertThat(secondResponse.getBody().getName()).isEqualTo(name);
    }
  }

  @Nested
  @DisplayName("Read DataSet Tests")
  class ReadDataSetTests {

    @Test
    @DisplayName("Should retrieve dataset by ID successfully")
    void shouldRetrieveDataSetById() {
      DataSet dataSet = createDataSetWithRelationships();
      UUID dataSetId = dataSet.getId();

      ResponseEntity<DataSetOutputDTO> response = performGetById(dataSetId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(dataSetId);
      assertThat(output.getName()).as("Name should be present").isNotNull();

      assertThat(output.getPipelines())
          .as("Pipelines should be included in the response")
          .isNotNull()
          .hasSize(2)
          .allMatch(pipeline -> pipeline.getId() != null)
          .allMatch(pipeline -> pipeline.getName() != null);

      assertThat(output.getDataSetStatus())
          .as("Status should be DRAFT")
          .isEqualTo(DataSetStatus.DRAFT);
    }

    @Test
    @DisplayName("Should return 404 for non-existent dataset")
    void shouldReturn404ForNonExistentDataSet() {
      ResponseEntity<DataSetOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve dataset without authentication")
    void shouldFailToRetrieveDataSetWithoutAuth() {
      UUID dataSetId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSetId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all datasets with pagination")
    void shouldRetrieveAllDataSetsWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<DataSetOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<DataSetOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain datasets").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve datasets with pagination parameters")
    void shouldRetrieveDataSetsWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<DataSetOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should exclude datasets with pending DELETE saga by default")
    void shouldExcludePendingDeleteDatasetsByDefault() {
      UUID pendingDeleteId = createTestEntity();
      DataSet pendingDelete = dataSetRepository.findById(pendingDeleteId).orElseThrow();
      pendingDelete.setPendingSagaType(PendingSagaType.DELETE);
      dataSetRepository.saveAndFlush(pendingDelete);

      ResponseEntity<RestPage<DataSetOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent())
          .extracting(DataSetOutputDTO::getId)
          .doesNotContain(pendingDeleteId);
    }

    @Test
    @DisplayName("Should include datasets with pending DELETE saga when opted in")
    void shouldIncludePendingDeleteDatasetsWhenOptedIn() {
      UUID pendingDeleteId = createTestEntity();
      DataSet pendingDelete = dataSetRepository.findById(pendingDeleteId).orElseThrow();
      pendingDelete.setPendingSagaType(PendingSagaType.DELETE);
      dataSetRepository.saveAndFlush(pendingDelete);

      ResponseEntity<RestPage<DataSetOutputDTO>> response =
          performGetAll(Map.of("includePendingDelete", "true"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent())
          .extracting(DataSetOutputDTO::getId)
          .contains(pendingDeleteId);
    }
  }

  @Nested
  @DisplayName("Update DataSet Tests")
  class UpdateDataSetTests {

    @Test
    @DisplayName("Should update DRAFT dataset successfully with PUT")
    void shouldUpdateDraftDataSetWithPut() {
      UUID dataSetId = createTestEntity();

      DataSetInputDTO updateInput = createUpdateInput();
      ResponseEntity<DataSetOutputDTO> response = performUpdate(dataSetId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(dataSetId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
      assertThat(output.getDataSetStatus())
          .as("Status should remain DRAFT")
          .isEqualTo(DataSetStatus.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"DRAFT"})
    @DisplayName("Should fail to update non-DRAFT dataset via regular PUT endpoint")
    void shouldFailToUpdateNonDraftDataSetViaRegularEndpoint(DataSetStatus status) {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(status);
      dataSet = dataSetRepository.save(dataSet);

      UUID dataSetId = dataSet.getId();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Name");
      updateInput.setDescription("Updated description");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSetId,
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status for %s dataset", status)
          .isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getType())
          .as("Published error contract; clients dispatch on this URN")
          .hasToString("urn:civitas:error:DATASET_NOT_EDITABLE");

      // Verify dataset was not modified
      DataSet unchangedDataSet = dataSetRepository.findById(dataSetId).orElse(null);
      assertThat(unchangedDataSet).isNotNull();
      assertThat(unchangedDataSet.getName())
          .as("Name should remain unchanged")
          .isEqualTo(dataSet.getName());
      assertThat(unchangedDataSet.getDataSetStatus())
          .as("Status should remain unchanged")
          .isEqualTo(status);
    }

    @Test
    @DisplayName("Should reject PUT on a DRAFT dataset whose unrelease teardown is still running")
    void shouldRejectUpdateWhileUnreleaseSagaInFlight() {
      DataSet dataSet = createDataSetWithRelationships();
      // The post-unrelease state, set directly because unrelease itself publishes to a broker this
      // context has none of. Unstage still runs for real below: it is the step that lets a pending
      // UNRELEASE through, and removing that exemption must break this test.
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet.setPendingSagaType(PendingSagaType.UNRELEASE);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();
      String originalName = dataSet.getName();

      ResponseEntity<DataSetOutputDTO> unstageResponse =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/unstage",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());
      assertThat(unstageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(dataSetRepository.findById(dataSetId).orElseThrow())
          .satisfies(
              persisted -> {
                assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
                assertThat(persisted.getPendingSagaType()).isEqualTo(PendingSagaType.UNRELEASE);
              });

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated While Teardown Runs");
      updateInput.setDescription("Updated description");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSetId,
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:SAGA_IN_FLIGHT");
      assertThat(response.getBody().getProperties())
          .containsEntry("pendingSagaType", PendingSagaType.UNRELEASE.name());
      assertThat(dataSetRepository.findById(dataSetId).orElseThrow().getName())
          .isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"DRAFT"})
    @DisplayName("Should update released dataset metadata via /released/meta endpoint")
    void shouldUpdateReleasedDataSetMetaViaReleasedEndpoint(DataSetStatus status) {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(status);
      dataSet = dataSetRepository.save(dataSet);

      UUID dataSetId = dataSet.getId();
      List<UUID> originalPipelineIds =
          dataSet.getPipelines().stream().map(Pipeline::getId).toList();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Released Dataset");
      updateInput.setDescription("Updated description for released dataset");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/released/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return OK status for %s dataset", status)
          .isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataSetId);
      assertThat(output.getName())
          .as("Name should be updated")
          .isEqualTo("Updated Released Dataset");
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo("Updated description for released dataset");
      assertThat(output.getPipelines())
          .as("Pipelines should remain unchanged")
          .hasSize(2)
          .extracting(PipelineSummaryDTO::getId)
          .containsExactlyInAnyOrderElementsOf(originalPipelineIds);
      assertThat(output.getDataSetStatus()).as("Status should remain unchanged").isEqualTo(status);
    }

    private UUID createTestGroup() {
      return portalData.group(b -> b.description("Test group for assignments")).getId();
    }

    private UUID createTestRole() {
      return portalData
          .role(b -> b.description("Test role for assignments").roleType(RoleType.DATA))
          .getId();
    }

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"DRAFT"})
    @DisplayName("Should replace assignments on released dataset via /released/meta endpoint")
    void shouldReplaceAssignmentsOnReleasedDatasetViaReleasedEndpoint(DataSetStatus status) {
      UUID groupId1 = createTestGroup();
      UUID groupId2 = createTestGroup();
      UUID roleId = createTestRole();

      DataSet dataSet = createDataSetWithRelationships();
      UUID dataSetId = dataSet.getId();

      AssignmentScopedInputDTO initialAssignment = new AssignmentScopedInputDTO();
      initialAssignment.setGroupId(groupId1);
      initialAssignment.setRoleId(roleId);

      DataSetInputDTO seedInput = createUpdateInput();
      seedInput.setAssignments(Set.of(initialAssignment));
      ResponseEntity<DataSetOutputDTO> seedResponse = performUpdate(dataSetId, seedInput);
      assertThat(seedResponse.getStatusCode())
          .as("Seeding the initial assignment on the DRAFT dataset should succeed")
          .isEqualTo(HttpStatus.OK);

      dataSet = dataSetRepository.findById(dataSetId).orElseThrow();
      dataSet.setDataSetStatus(status);
      dataSetRepository.save(dataSet);

      AssignmentScopedInputDTO replacementAssignment = new AssignmentScopedInputDTO();
      replacementAssignment.setGroupId(groupId2);
      replacementAssignment.setRoleId(roleId);

      DataSetInputDTO releasedUpdateInput = new DataSetInputDTO();
      releasedUpdateInput.setName("Updated Released Dataset With New Assignment");
      releasedUpdateInput.setDescription("Updated description for released dataset");
      releasedUpdateInput.setOpenDataAccess(false);
      releasedUpdateInput.setAssignments(Set.of(replacementAssignment));

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/released/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              releasedUpdateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return OK status for %s dataset", status)
          .isEqualTo(HttpStatus.OK);

      ResponseEntity<List<AssignmentOutputDTO>> assignmentsResponse =
          restTemplate.exchange(
              getEndpointPath() + "/" + dataSetId + "/assignments",
              org.springframework.http.HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(assignmentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(assignmentsResponse.getBody())
          .as("Old assignment should be replaced, not appended, for %s dataset", status)
          .hasSize(1);
      assertThat(assignmentsResponse.getBody().getFirst().getGroup().getId())
          .as("Assignment should now point to the new group for %s dataset", status)
          .isEqualTo(groupId2);
    }

    @Test
    @DisplayName("Should fail to update DRAFT dataset via /released/meta endpoint")
    void shouldFailToUpdateDraftDataSetViaReleasedEndpoint() {
      UUID dataSetId = createTestEntity();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Name");
      updateInput.setDescription("Updated description");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/released/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status for DRAFT dataset")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      // Verify dataset was not modified
      DataSet unchangedDataSet = dataSetRepository.findById(dataSetId).orElse(null);
      assertThat(unchangedDataSet).isNotNull();
      assertThat(unchangedDataSet.getName())
          .as("Name should remain unchanged")
          .doesNotContain("Updated");
      assertThat(unchangedDataSet.getDataSetStatus())
          .as("Status should remain DRAFT")
          .isEqualTo(DataSetStatus.DRAFT);
    }

    @Test
    @DisplayName("Should update READY dataset metadata via /ready/meta endpoint")
    void shouldUpdateReadyDataSetMetaViaReadyEndpoint() {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet = dataSetRepository.save(dataSet);

      UUID dataSetId = dataSet.getId();
      List<UUID> originalPipelineIds =
          dataSet.getPipelines().stream().map(Pipeline::getId).toList();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Ready Dataset");
      updateInput.setDescription("Updated description for ready dataset");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/ready/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getName()).as("Name should be updated").isEqualTo("Updated Ready Dataset");
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo("Updated description for ready dataset");
      assertThat(output.getPipelines())
          .as("Pipelines should remain unchanged")
          .hasSize(2)
          .extracting(PipelineSummaryDTO::getId)
          .containsExactlyInAnyOrderElementsOf(originalPipelineIds);
      assertThat(output.getDataSetStatus())
          .as("Status should remain READY")
          .isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("Should reject /ready/meta while a saga is in flight")
    void shouldRejectReadyMetaWhileSagaInFlight() {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet.setPendingSagaType(PendingSagaType.CREATE);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();
      String originalName = dataSet.getName();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated While Saga Runs");

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSetId + "/ready/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getType()).hasToString("urn:civitas:error:SAGA_IN_FLIGHT");
      assertThat(response.getBody().getProperties())
          .containsEntry("pendingSagaType", PendingSagaType.CREATE.name());
      assertThat(dataSetRepository.findById(dataSetId).orElseThrow().getName())
          .isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"READY"})
    @DisplayName("Should reject a non-READY dataset via /ready/meta endpoint")
    void shouldRejectNonReadyDataSetViaReadyEndpoint(DataSetStatus status) {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(status);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();
      String originalName = dataSet.getName();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Name");
      updateInput.setDescription("Updated description");
      updateInput.setOpenDataAccess(false);

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/ready/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status for %s dataset", status)
          .isEqualTo(HttpStatus.BAD_REQUEST);

      DataSet unchangedDataSet = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(unchangedDataSet.getName())
          .as("Name should remain unchanged")
          .isEqualTo(originalName);
      assertThat(unchangedDataSet.getDataSetStatus())
          .as("Status should remain unchanged")
          .isEqualTo(status);
    }

    @Test
    @DisplayName("Should reject namedApis on /ready/meta endpoint")
    void shouldRejectNamedApisViaReadyEndpoint() {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName("Updated Ready Dataset");
      updateInput.setDescription("Updated description");
      updateInput.setNamedApis(List.of(namedApi("Traffic", "traffic", ApiStandard.STA, null)));

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/ready/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              updateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      DataSet unchangedDataSet = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(unchangedDataSet.getName())
          .as("The rejection must happen before anything is persisted")
          .doesNotContain("Updated");
    }

    @Test
    @DisplayName("Should replace assignments on a READY dataset via /ready/meta endpoint")
    void shouldReplaceAssignmentsOnReadyDatasetViaReadyEndpoint() {
      UUID groupId1 = createTestGroup();
      UUID groupId2 = createTestGroup();
      UUID roleId = createTestRole();

      DataSet dataSet = createDataSetWithRelationships();
      UUID dataSetId = dataSet.getId();

      AssignmentScopedInputDTO initialAssignment = new AssignmentScopedInputDTO();
      initialAssignment.setGroupId(groupId1);
      initialAssignment.setRoleId(roleId);

      DataSetInputDTO seedInput = createUpdateInput();
      seedInput.setAssignments(Set.of(initialAssignment));
      assertThat(performUpdate(dataSetId, seedInput).getStatusCode()).isEqualTo(HttpStatus.OK);

      dataSet = dataSetRepository.findById(dataSetId).orElseThrow();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSetRepository.save(dataSet);

      AssignmentScopedInputDTO replacementAssignment = new AssignmentScopedInputDTO();
      replacementAssignment.setGroupId(groupId2);
      replacementAssignment.setRoleId(roleId);

      DataSetInputDTO readyUpdateInput = new DataSetInputDTO();
      readyUpdateInput.setName("Updated Ready Dataset With New Assignment");
      readyUpdateInput.setDescription("Updated description for ready dataset");
      readyUpdateInput.setAssignments(Set.of(replacementAssignment));

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/ready/meta",
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              readyUpdateInput,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      ResponseEntity<List<AssignmentOutputDTO>> assignmentsResponse =
          restTemplate.exchange(
              getEndpointPath() + "/" + dataSetId + "/assignments",
              org.springframework.http.HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(assignmentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(assignmentsResponse.getBody())
          .as("Old assignment should be replaced, not appended")
          .hasSize(1);
      assertThat(assignmentsResponse.getBody().getFirst().getGroup().getId()).isEqualTo(groupId2);
    }

    @Test
    @DisplayName("Should partially update dataset with PATCH - single field")
    void shouldPartiallyUpdateDataSetWithPatch() {
      UUID dataSetId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<DataSetOutputDTO> response = performPatch(dataSetId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      UUID dataSetId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedDataSet");
      patchMap.put("description", "Patched description");

      ResponseEntity<DataSetOutputDTO> response = performPatch(dataSetId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedDataSet");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      UUID dataSetId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<DataSetOutputDTO> response = performPatch(dataSetId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID dataSetId = createTestEntity();

      ResponseEntity<DataSetOutputDTO> initialResponse = performGetById(dataSetId);
      DataSetOutputDTO initialDataSet = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<DataSetOutputDTO> response = performPatch(dataSetId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialDataSet.getName());
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID dataSetId = createTestEntity();

      ResponseEntity<DataSetOutputDTO> initialResponse = performGetById(dataSetId);
      DataSetOutputDTO initialDataSet = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<DataSetOutputDTO> response = performPatch(dataSetId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialDataSet.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialDataSet.getDescription());
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      UUID dataSetId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<DataSetOutputDTO> firstResponse = performPatch(dataSetId, patchMap);
      ResponseEntity<DataSetOutputDTO> secondResponse = performPatch(dataSetId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    // Tests for preventing updates of non-DRAFT datasets via the regular endpoint are in
    // shouldFailToUpdateNonDraftDataSetViaRegularEndpoint and
    // shouldUpdateReleasedDataSetMetaViaReleasedEndpoint above

    @Test
    @DisplayName("Should fail to update non-existent dataset")
    void shouldFailToUpdateNonExistentDataSet() {
      DataSetInputDTO updateInput = createUpdateInput();

      ResponseEntity<DataSetOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update dataset without authentication")
    void shouldFailToUpdateDataSetWithoutAuth() {
      UUID dataSetId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSetId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should update dataset URL")
    void shouldUpdateDataSetUrl() {
      UUID dataSetId = createTestEntity();

      DataSetInputDTO updateInput = createUpdateInput();

      ResponseEntity<DataSetOutputDTO> response = performUpdate(dataSetId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Delete DataSet Tests")
  class DeleteDataSetTests {

    @Test
    @DisplayName("Should delete DRAFT dataset successfully and cascade to relationships")
    void shouldDeleteDataSetSuccessfully() {
      DataSet dataSet = createDataSetWithRelationships();
      UUID dataSetId = dataSet.getId();

      assertThat(pipelineRepository.findAll())
          .as("Pipelines should exist before deletion")
          .hasSize(2);
      assertThat(distributionRepository.findAll())
          .as("Distributions should exist before deletion")
          .hasSize(2);

      ResponseEntity<Void> response = performDelete(dataSetId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<DataSetOutputDTO> getResponse = performGetById(dataSetId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted dataset should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);

      long pipelineCount =
          pipelineRepository.findAll().stream()
              .filter(p -> p.getDataSet() != null && p.getDataSet().getId().equals(dataSetId))
              .count();
      assertThat(pipelineCount)
          .as("All pipelines associated with the dataset should be deleted")
          .isEqualTo(0);

      long distributionCount =
          distributionRepository.findAll().stream()
              .filter(d -> d.getDataSet() != null && d.getDataSet().getId().equals(dataSetId))
              .count();
      assertThat(distributionCount)
          .as("All distributions associated with the dataset should be deleted")
          .isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject deleting an AVAILABLE dataset (unrelease first)")
    void shouldRejectDeletingAvailableDataSet() {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      ResponseEntity<Void> response = performDelete(dataSetId);

      assertThat(response.getStatusCode())
          .as("An AVAILABLE dataset must be unreleased before it can be deleted")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      assertThat(performGetById(dataSetId).getStatusCode())
          .as("Dataset should still exist after the rejected deletion")
          .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should delete a never-provisioned READY dataset immediately")
    void shouldDeleteReadyDataSetWhenNotProvisioned() {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      ResponseEntity<Void> response = performDelete(dataSetId);

      assertThat(response.getStatusCode())
          .as("A READY dataset with no provisioned sink has no infrastructure to tear down")
          .isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(performGetById(dataSetId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent dataset")
    void shouldFailToDeleteNonExistentDataSet() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete dataset without authentication")
    void shouldFailToDeleteDataSetWithoutAuth() {
      UUID dataSetId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSetId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      DataSetInputDTO input = createValidInput();
      input.setName("DataSet with special chars: äöü ß @#$%");

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      DataSetInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty name as invalid")
    void shouldHandleEmptyName() {
      DataSetInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Stage DataSet Tests")
  class StageDataSetTests {

    @Test
    @DisplayName("Should stage dataset with pipelines successfully")
    void shouldStageDataSetWithPipelinesSuccessfully() {
      DataSet dataSet = new DataSet();
      dataSet.setName("test_dataset_stage_" + System.currentTimeMillis());
      dataSet.setDescription("Test dataset with pipelines");
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);
      dataSet.setIdentifier("test-identifier-stage");
      dataSet.setVersion("1.0.0");
      dataSet.setExternalId("ext-dataset-stage-" + System.currentTimeMillis());
      dataSet.setFormat("JSON");
      dataSet.setOpenDataAccess(false);
      dataSet = dataSetRepository.save(dataSet);

      Pipeline pipeline1 = new Pipeline();
      pipeline1.setName("test_pipeline_api1_" + System.currentTimeMillis());
      pipeline1.setDescription("Pipeline 1");
      pipeline1.setDataSet(dataSet);

      pipeline1 = pipelineRepository.save(pipeline1);
      pipeline1 =
          portalData.attachPipelineDefinition(pipeline1, createSampleModel(), createSampleStyles());

      Pipeline pipeline2 = new Pipeline();
      pipeline2.setName("test_pipeline_api2_" + System.currentTimeMillis());
      pipeline2.setDescription("Pipeline 2");
      pipeline2.setDataSet(dataSet);

      pipeline2 = pipelineRepository.save(pipeline2);
      pipeline2 =
          portalData.attachPipelineDefinition(pipeline2, createSampleModel(), createSampleStyles());

      seedStageRequirements(pipeline1, pipeline2);

      UUID dataSetId = dataSet.getId();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/stage",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSetStatus())
          .as("DataSet status should be READY after staging")
          .isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("Should fail to stage dataset without pipelines")
    void shouldFailToStageDataSetWithoutPipelines() {
      DataSet dataSet = new DataSet();
      dataSet.setName("test_dataset_no_pipelines_" + System.currentTimeMillis());
      dataSet.setDescription("Test dataset without pipelines");
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);
      dataSet.setFormat("JSON");
      dataSet.setOpenDataAccess(false);
      dataSet = dataSetRepository.save(dataSet);

      UUID dataSetId = dataSet.getId();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/stage",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status for dataset without pipelines")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      DataSet unchangedDataSet = dataSetRepository.findById(dataSetId).orElse(null);
      assertThat(unchangedDataSet).isNotNull();
      assertThat(unchangedDataSet.getDataSetStatus())
          .as("DataSet status should remain DRAFT after failed stage")
          .isEqualTo(DataSetStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to stage non-existent dataset")
    void shouldFailToStageNonExistentDataSet() {
      UUID nonExistentId = UUID.randomUUID();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + nonExistentId + "/stage",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * A dataset whose pipeline reaches a governed data structure two hops out, the shape a real
     * flow has: the source node names a DataSource, whose element names the structure. The walk,
     * the pin resolution and the scope check all run against the real registry.
     */
    private DataSet dataSetReachingStructure(DataStructureVersionStatus versionStatus) {
      DataStructure structure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion version =
          portalData.attachModel(
              portalData.dataStructureVersion(
                  structure, b -> b.dataStructureVersionStatus(versionStatus)),
              portalData.dataStructureVersionModel("Sensor"));

      DataSource dataSource =
          portalData.attachSourceConfiguration(
              portalData.dataSource(
                  b ->
                      b.connectorType(ConnectorType.MQTT)
                          .dataSourceStatus(DataSourceStatus.AVAILABLE)),
              Map.of(
                  "urls",
                  List.of("mqtt://mqtt-broker:1883"),
                  "topics",
                  List.of("sensors/closure"),
                  "element",
                  version.getModelUrn()));

      DataSet dataSet =
          portalData.dataSet(
              b ->
                  b.description("reaches a governed structure").dataSetStatus(DataSetStatus.DRAFT));
      Pipeline pipeline = portalData.pipeline(dataSet);
      pipeline.getDataSources().add(dataSource);
      pipeline = pipelineRepository.save(pipeline);
      portalData.attachPipelineDefinition(
          pipeline,
          Map.of(
              "nodes",
              List.of(
                  Map.of(
                      "id",
                      "src-1",
                      "kind",
                      "source",
                      "label",
                      "Source",
                      "sourceRef",
                      dataSource.getConfigurationUrn())),
              "edges",
              List.of()),
          createSampleStyles());
      return dataSet;
    }

    @Test
    @DisplayName("Should stage when the reached data structure is released")
    void shouldStageWhenReachedStructureIsReleased() {
      DataSet dataSet = dataSetReachingStructure(DataStructureVersionStatus.AVAILABLE);

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSet.getId() + "/stage",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("a flow reaching only released artifacts carries a release")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("Should refuse staging when the reached data structure is still a draft")
    void shouldRefuseStagingWhenReachedStructureIsDraft() {
      DataSet dataSet = dataSetReachingStructure(DataStructureVersionStatus.DRAFT);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + dataSet.getId() + "/stage",
              HttpMethod.POST,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getProperties())
          .as("the reply names the pipeline the caller has to repair")
          .containsKey("offendingPipelineIds");
      assertThat(response.getBody().getProperties().toString())
          .as("an artifact the caller may not read must not be disclosed by the reply")
          .doesNotContain("urn:core:");
      assertThat(dataSetRepository.findById(dataSet.getId()).orElseThrow().getDataSetStatus())
          .as("a refused stage leaves the dataset where it was")
          .isEqualTo(DataSetStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("Saga Completion Tests")
  class SagaCompletionTests {

    private DataSet createReleasedDataSet() {
      DataSet dataSet = new DataSet();
      dataSet.setName("test_dataset_saga_" + System.currentTimeMillis());
      dataSet.setDescription("Test dataset for saga completion");
      dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSet.setPendingSagaType(PendingSagaType.CREATE);
      dataSet.setOpenDataAccess(false);
      return dataSetRepository.save(dataSet);
    }

    @Test
    @DisplayName("CREATE saga should persist infrastructure fields and reconcile NamedApi.routeId")
    void createSagaShouldPersistInfrastructure() {
      DataSet dataSet = createReleasedDataSet();
      UUID dataSetId = dataSet.getId();

      NamedApi namedApi = new NamedApi();
      namedApi.setName("Things");
      namedApi.setSlug("things");
      namedApi.setStandard(ApiStandard.STA);
      namedApi.setDataSet(dataSet);
      dataSet.getNamedApis().add(namedApi);
      dataSetRepository.save(dataSet);

      SagaResultPayload result =
          new SagaResultPayload(
              dataSetId.toString(),
              "proj-test",
              "https://frost.example.com",
              Map.of("things", "route-test"),
              "svc-test",
              "https://public.example.com/datasets/" + dataSetId,
              List.of("pipe-test"),
              null,
              null,
              null);
      dataSetService.handleSagaCompleted(dataSetId, result);

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(persisted.getPendingSagaType()).isNull();
      assertThat(persisted.getProjectId()).isEqualTo("proj-test");
      assertThat(persisted.getPublicUrl())
          .isEqualTo("https://public.example.com/datasets/" + dataSetId);
      assertThat(persisted.getNamedApis())
          .as("Saga must reconcile routeId onto each NamedApi by slug")
          .singleElement()
          .satisfies(
              api -> {
                assertThat(api.getSlug()).isEqualTo("things");
                assertThat(api.getRouteId()).isEqualTo("route-test");
              });
    }

    @Test
    @DisplayName("UNRELEASE saga keeps the sink, clears route/pipeline, stays READY")
    void unreleaseSagaKeepsSink() {
      DataSet dataSet = createReleasedDataSet();
      dataSet.setProjectId("proj-test");
      dataSet.setFrostBaseUrl("https://frost.example.com/Projects(7)");
      dataSet.setServiceId("svc-test");
      dataSet.setPublicUrl("https://public.example.com/datasets/" + dataSet.getId());
      dataSet.setPipelineIds(List.of("pipe-test"));
      dataSet.setProvisioned(true);
      // unrelease set READY optimistically before the saga ran.
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet.setPendingSagaType(PendingSagaType.UNRELEASE);
      NamedApi namedApi = new NamedApi();
      namedApi.setName("Things");
      namedApi.setSlug("things");
      namedApi.setStandard(ApiStandard.STA);
      namedApi.setRouteId("route-test");
      namedApi.setDataSet(dataSet);
      dataSet.getNamedApis().add(namedApi);
      dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      SagaResultPayload result =
          new SagaResultPayload(
              dataSetId.toString(), null, null, null, null, null, null, null, null, null);
      dataSetService.handleSagaCompleted(dataSetId, result);

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(persisted.getPendingSagaType()).isNull();
      // Route/consumer-access layer is gone (routes/upstream deleted by DELETE_ROUTE).
      assertThat(persisted.getPipelineIds()).isNull();
      assertThat(persisted.getNamedApis()).allMatch(api -> api.getRouteId() == null);
      assertThat(persisted.getServiceId()).isNull();
      assertThat(persisted.getPublicUrl()).isNull();
      // The data-holding sink survives so a re-release reuses it.
      assertThat(persisted.getProjectId()).isEqualTo("proj-test");
      assertThat(persisted.getFrostBaseUrl()).isEqualTo("https://frost.example.com/Projects(7)");
      assertThat(persisted.isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("DELETE saga removes the dataset entity after teardown")
    void deleteSagaRemovesEntity() {
      DataSet dataSet = createReleasedDataSet();
      dataSet.setProjectId("proj-test");
      dataSet.setProvisioned(true);
      dataSet.setPendingSagaType(PendingSagaType.DELETE);
      dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      SagaResultPayload result =
          new SagaResultPayload(
              dataSetId.toString(), null, null, null, null, null, null, null, null, null);
      dataSetService.handleSagaCompleted(dataSetId, result);

      assertThat(dataSetRepository.findById(dataSetId)).isEmpty();
    }

    @Test
    @DisplayName("CREATE saga marks the dataset provisioned")
    void createSagaMarksProvisioned() {
      DataSet dataSet = createReleasedDataSet();
      UUID dataSetId = dataSet.getId();

      SagaResultPayload result =
          new SagaResultPayload(
              dataSetId.toString(), "proj-test", null, null, null, null, null, null, null, null);
      dataSetService.handleSagaCompleted(dataSetId, result);

      DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
      assertThat(persisted.isProvisioned()).isTrue();
    }
  }

  @Nested
  @DisplayName("GET /datasets/{id}/apis — Named API discovery (#1596)")
  class GetNamedApisEndpoint {

    private UUID createDatasetWithApis() {
      DataSetInputDTO input = createValidInput();
      input.setNamedApis(
          List.of(
              namedApi("Traffic", "traffic", ApiStandard.STA, "1.1"),
              namedApi("Weather", "weather", ApiStandard.STA, null)));
      ResponseEntity<DataSetOutputDTO> created = performCreate(input);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      return created.getBody().getId();
    }

    private void makeAvailable(UUID id) {
      txTemplate.executeWithoutResult(
          s -> {
            DataSet ds = dataSetRepository.findById(id).orElseThrow();
            ds.setDataSetStatus(DataSetStatus.AVAILABLE);
            dataSetRepository.save(ds);
          });
    }

    @Test
    @DisplayName(
        "returns the named APIs with previewUrl for a published (AVAILABLE) in-scope dataset")
    void shouldReturnNamedApisForAvailableDataset() {
      UUID id = createDatasetWithApis();
      makeAvailable(id);

      ResponseEntity<List<NamedApiOutputDTO>> response =
          exchange(
              DATASETS_ENDPOINT + "/" + id + "/apis",
              HttpMethod.GET,
              createAuthHeadersWithScope(id.toString()),
              null,
              new ParameterizedTypeReference<List<NamedApiOutputDTO>>() {});

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody())
          .extracting(NamedApiOutputDTO::getSlug)
          .containsExactlyInAnyOrder("traffic", "weather");
      String base = civitasProperties.api().baseUrl() + "/v1/datasets/" + id;
      assertThat(response.getBody())
          .allSatisfy(api -> assertThat(api.getPreviewUrl()).isEqualTo(base + "/" + api.getSlug()));
    }

    @Test
    @DisplayName(
        "returns an empty list for a DRAFT dataset — only published APIs are discoverable (#1379)")
    void shouldReturnEmptyForDraftDataset() {
      UUID id = createDatasetWithApis(); // DRAFT, carries two named APIs

      ResponseEntity<List<NamedApiOutputDTO>> response =
          exchange(
              DATASETS_ENDPOINT + "/" + id + "/apis",
              HttpMethod.GET,
              createAuthHeadersWithScope(id.toString()),
              null,
              new ParameterizedTypeReference<List<NamedApiOutputDTO>>() {});

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody())
          .as(
              "a DRAFT dataset has no active published APIs — draft edits must not change the"
                  + " public API surface (#1379)")
          .isEmpty();
    }

    @Test
    @DisplayName("returns an empty list when the dataset has no named APIs")
    void shouldReturnEmptyListWhenNoNamedApis() {
      UUID id = createTestEntity();

      ResponseEntity<List<NamedApiOutputDTO>> response =
          exchange(
              DATASETS_ENDPOINT + "/" + id + "/apis",
              HttpMethod.GET,
              createAuthHeadersWithScope(id.toString()),
              null,
              new ParameterizedTypeReference<List<NamedApiOutputDTO>>() {});

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("returns 404 when the dataset is outside the caller's scope (no leakage)")
    void shouldReturn404WhenDatasetOutOfScope() {
      UUID id = createDatasetWithApis();
      HttpHeaders headers = createAuthHeadersWithScope(UUID.randomUUID().toString());

      ResponseEntity<String> response =
          restTemplate.exchange(
              DATASETS_ENDPOINT + "/" + id + "/apis",
              HttpMethod.GET,
              new HttpEntity<>(headers),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("returns 404 for an unknown dataset id")
    void shouldReturn404ForUnknownDataset() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              DATASETS_ENDPOINT + "/" + UUID.randomUUID() + "/apis",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("returns 403 when the X-Allowed-Scope-Ids header is missing")
    void shouldReturn403WhenScopeHeaderMissing() {
      UUID id = createDatasetWithApis();

      ResponseEntity<String> response =
          restTemplate.exchange(
              DATASETS_ENDPOINT + "/" + id + "/apis",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeadersWithoutScope()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }
}
