package de.civitascore.portal.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

@Slf4j
@DisplayName("DataStructureVersion Controller Integration Tests")
@EnableWireMock(
    @ConfigureWireMock(
        baseUrlProperties = {"model-atlas.base-url"},
        portProperties = "model-atlas.port"))
class DataStructureVersionControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DataStructureRepository dataStructureRepository;

  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;

  private static final String TEST_NS_URI = "http://test/test/myuml/1.0.0";
  private static final String TEST_MODEL_FILE_PATH = "mocks/models/Simple_model.xmi";
  private static final String TEST_SCOPE = "default";
  private static final String TEST_STAGE = "draft";
  private static final String MOCK_MODEL_RESPONSE = "<xml>mock model content</xml>";

  private String modelContent;
  private byte[] modelContentBinary;

  private UUID dataStructureId;
  private UUID versionId1;

  @BeforeEach
  void initTestData() throws IOException {
    ClassPathResource modelFile = new ClassPathResource(TEST_MODEL_FILE_PATH);
    modelContentBinary = modelFile.getContentAsByteArray();
    modelContent = new String(modelContentBinary, StandardCharsets.UTF_8);

    // Create parent data structure
    DataStructure dataStructure = new DataStructure();
    dataStructure.setName("Test Data Structure");
    dataStructure.setDescription("Data structure for version testing");
    dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure.setCreatedFromDataSource(false);
    dataStructure = dataStructureRepository.save(dataStructure);
    dataStructureId = dataStructure.getId();

    // Create test versions
    DataStructureVersion version1 = new DataStructureVersion();
    version1.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    version1.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    version1.setVersion("1.0.0");
    version1.setDescription("First version of the test data structure");
    version1.setModelAtlasUri("https://modelatlas.example.com/model1");
    version1.setModelName("TestModel1");
    version1.setDataStructure(dataStructure);

    Map<String, Object> styles1 = new HashMap<>();
    styles1.put("color", "blue");
    styles1.put("size", 10);
    version1.setStyles(styles1);

    version1 = dataStructureVersionRepository.save(version1);
    versionId1 = version1.getId();

    DataStructureVersion version2 = new DataStructureVersion();
    version2.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    version2.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    version2.setVersion("2.0.0");
    version2.setDescription("Second version with updated fields");
    version2.setModelAtlasUri("https://modelatlas.example.com/model2");
    version2.setModelName("TestModel2");
    version2.setDataStructure(dataStructure);

    Map<String, Object> styles2 = new HashMap<>();
    styles2.put("color", "red");
    styles2.put("size", 20);
    version2.setStyles(styles2);

    dataStructureVersionRepository.save(version2);
  }

  @AfterEach
  void cleanup() {
    dataStructureVersionRepository.deleteAll();
    dataStructureRepository.deleteAll();
  }

  private String stubModelDownload(String nsUri) {
    String downloadPath =
        String.format(
            "/atlas/rest/%s/schema/stages/%s/content?nsUri=%s", TEST_SCOPE, TEST_STAGE, nsUri);
    stubFor(
        get(urlEqualTo(downloadPath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                    .withBody(MOCK_MODEL_RESPONSE)));

    return downloadPath;
  }

  private String stubModelUpload(String nsUri) {
    String expectedPath =
        String.format(
            "/atlas/rest/%s/schema/stages/%s?nsUri=%s&overwrite=true",
            TEST_SCOPE, TEST_STAGE, nsUri);

    stubFor(
        post(urlEqualTo(expectedPath))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
            .withRequestBody(binaryEqualTo(modelContentBinary))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/uml")
                    .withBody(MOCK_MODEL_RESPONSE)));

    return expectedPath;
  }

  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    return headers;
  }

  private String getEndpoint() {
    return "/datastructures/" + dataStructureId + "/versions";
  }

  protected ParameterizedTypeReference<DataStructureVersionOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Nested
  @DisplayName("Create DataStructureVersion Tests")
  class CreateDataStructureVersionTests {

    @Test
    @DisplayName("Should create data structure version successfully with valid data")
    void shouldCreateDataStructureVersionSuccessfully() {
      stubModelDownload("https://modelatlas.example.com/model3");
      String expectedUploadPath = stubModelUpload("https://modelatlas.example.com/model3");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setDescription("Third version with new features");
      input.setModelAtlasUri("https://modelatlas.example.com/model3");
      input.setModel(modelContent);
      input.setModelName("TestModel3");

      Map<String, Object> styles = new HashMap<>();
      styles.put("color", "green");
      styles.put("size", 15);
      input.setStyles(styles);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getVersion()).as("Version should match input").isEqualTo("3.0.0");
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo("Third version with new features");
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be set to DRAFT by preProcessCreateInput hook")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getDataStructureVersionSource())
          .as("Source should match input")
          .isEqualTo(DataStructureVersionSource.OWN);
      assertThat(output.getModelAtlasUri())
          .as("Model atlas URI should match input")
          .isEqualTo("https://modelatlas.example.com/model3");
      assertThat(output.getModelName()).as("Model name should match input").isEqualTo("TestModel3");
      assertThat(output.getStyles().get("color"))
          .as("Styles color should match input")
          .isEqualTo("green");
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should be set from path variable")
          .isEqualTo(dataStructureId);
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();

      assertThat(response.getHeaders().getLocation())
          .as("Location header should be present")
          .isNotNull();

      verify(
          postRequestedFor(urlEqualTo(expectedUploadPath)).withRequestBody(equalTo(modelContent)));
    }

    @Test
    @DisplayName("Should fail to create version with missing required field")
    void shouldFailToCreateVersionWithMissingVersion() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelAtlasUri("https://modelatlas.example.com/model");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create version without authentication")
    void shouldFailToCreateVersionWithoutAuth() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("4.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model");
      input.setModelAtlasUri("https://modelatlas.example.com/model");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.POST, new HttpEntity<>(input), String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should upload model to Model Atlas when model is provided on create")
    void shouldUploadModelWhenProvidedOnCreate() {
      String expectedUploadPath = stubModelUpload(TEST_NS_URI);
      stubModelDownload(TEST_NS_URI);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setModelAtlasUri(TEST_NS_URI);
      input.setModelName("TestModel3");
      input.setModel(modelContent);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      verify(
          postRequestedFor(urlEqualTo(expectedUploadPath)).withRequestBody(equalTo(modelContent)));
    }

    @Test
    @DisplayName(
        "Should fail creation when model is provided but modelAtlasUri is missing on create")
    void shouldFailWhenModelProvidedButNoModelAtlasUriOnCreate() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setModelName("TestModel3");
      input.setModel(modelContent);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName(
        "Should fail creation when modelAtlasUri is provided but model is missing on create")
    void shouldFailWhenModelAtlasUriProvidedButNoModelOnCreate() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setModelName("TestModel3");
      input.setModelAtlasUri(TEST_NS_URI);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName("Should roll back create when Model Atlas upload fails")
    void shouldRollBackCreateWhenModelAtlasUploadFails() {
      String expectedPath =
          String.format(
              "/atlas/rest/%s/schema/stages/%s?nsUri=%s&overwrite=true",
              TEST_SCOPE, TEST_STAGE, TEST_NS_URI);

      stubFor(
          post(urlEqualTo(expectedPath))
              .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
              .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setModelAtlasUri(TEST_NS_URI);
      input.setModelName("TestModel3");
      input.setModel(modelContent);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

      List<DataStructureVersion> versions = dataStructureVersionRepository.findAll();
      assertThat(versions).noneMatch(v -> "3.0.0".equals(v.getVersion()));
    }

    @Test
    @DisplayName("Should set dataStructureId from path variable, not from payload")
    void shouldSetDataStructureIdFromPathVariable() {
      stubModelDownload("https://modelatlas.example.com/model");
      stubModelUpload("https://modelatlas.example.com/model");

      // Create another data structure
      DataStructure otherDataStructure = new DataStructure();
      otherDataStructure.setName("Other Data Structure");
      otherDataStructure.setDescription("Should not be used");
      otherDataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      otherDataStructure.setCreatedFromDataSource(false);
      otherDataStructure = dataStructureRepository.save(otherDataStructure);
      UUID otherDataStructureId = otherDataStructure.getId();

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("5.0.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelAtlasUri("https://modelatlas.example.com/model");
      input.setModel(modelContent);
      // Try to set a different dataStructureId - should be ignored due to @JsonIgnore
      input.setDataStructureId(otherDataStructureId);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataStructure()).isNotNull();
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should be from path variable, not payload")
          .isEqualTo(dataStructureId)
          .isNotEqualTo(otherDataStructureId);
    }
  }

  @Nested
  @DisplayName("Read DataStructureVersion Tests")
  class ReadDataStructureVersionTests {

    @Test
    @DisplayName("Should retrieve data structure version by ID successfully")
    void shouldRetrieveDataStructureVersionById() {
      stubModelDownload("https://modelatlas.example.com/model1");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getVersion()).isEqualTo("1.0.0");
      assertThat(output.getDescription()).isEqualTo("First version of the test data structure");
      assertThat(output.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getDataStructureVersionSource()).isEqualTo(DataStructureVersionSource.OWN);
      assertThat(output.getModelAtlasUri()).isEqualTo("https://modelatlas.example.com/model1");
      assertThat(output.getModelName()).isEqualTo("TestModel1");
      assertThat(output.getStyles().get("color")).isEqualTo("blue");
      assertThat(output.getModel())
          .as("Model should be auto-downloaded from Model Atlas")
          .isEqualTo(MOCK_MODEL_RESPONSE);

      DataStructureSummaryDTO dataStructureSummary = output.getDataStructure();
      assertThat(dataStructureSummary).isNotNull();
      assertThat(dataStructureSummary.getId()).isEqualTo(dataStructureId);
      assertThat(dataStructureSummary.getName()).isEqualTo("Test Data Structure");
    }

    @Test
    @DisplayName("Should auto-download model from Model Atlas when modelAtlasUri is set")
    void shouldAutoDownloadModelFromModelAtlasWhenModelAtlasUriIsSet() {
      stubModelDownload("https://modelatlas.example.com/model1");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getModel())
          .as("Model content should be fetched from Model Atlas via modelAtlasUri")
          .isEqualTo(MOCK_MODEL_RESPONSE);

      String expectedDownloadPath =
          String.format(
              "/atlas/rest/%s/schema/stages/%s/content?nsUri=%s",
              TEST_SCOPE, TEST_STAGE, "https://modelatlas.example.com/model1");
      verify(getRequestedFor(urlEqualTo(expectedDownloadPath)));
    }

    @Test
    @DisplayName("Should return model as null when modelAtlasUri is not set")
    void shouldReturnNullModelWhenModelAtlasUriIsNotSet() {
      // Create a version without modelAtlasUri
      DataStructureVersion versionWithoutUri = new DataStructureVersion();
      versionWithoutUri.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      versionWithoutUri.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      versionWithoutUri.setVersion("3.0.0");
      versionWithoutUri.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      versionWithoutUri = dataStructureVersionRepository.save(versionWithoutUri);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionWithoutUri.getId(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel())
          .as("Model should be null when no modelAtlasUri is set")
          .isNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent version")
    void shouldReturn404ForNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to fetch all versions for a data structure")
    void shouldFailToRetrieveAllVersions() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
  }

  @Nested
  @DisplayName("Update DataStructureVersion Tests")
  class UpdateDataStructureVersionTests {

    @Test
    @DisplayName("Should update data structure version successfully")
    void shouldUpdateDataStructureVersionSuccessfully() {
      stubModelDownload("https://modelatlas.example.com/model1-updated");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setDescription("Updated description for version 1.1.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1-updated");
      input.setModelName("TestModel1-Updated");

      Map<String, Object> styles = new HashMap<>();
      styles.put("color", "purple");
      styles.put("size", 25);
      input.setStyles(styles);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getVersion()).isEqualTo("1.1.0");
      assertThat(output.getDescription()).isEqualTo("Updated description for version 1.1.0");
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should remain DRAFT (not changed by update)")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(output.getModelAtlasUri())
          .isEqualTo("https://modelatlas.example.com/model1-updated");
      assertThat(output.getModelName()).isEqualTo("TestModel1-Updated");
      assertThat(output.getStyles().get("color")).isEqualTo("purple");
      assertThat(output.getStyles().get("size")).isEqualTo(25);
    }

    @Test
    @DisplayName("Should upload model to Model Atlas when model is provided")
    void shouldUploadModelWhenProvided() {
      String expectedPath =
          String.format(
              "/atlas/rest/%s/schema/stages/%s?nsUri=%s&overwrite=true",
              TEST_SCOPE, TEST_STAGE, "https://modelatlas.example.com/model1-updated");

      stubFor(
          post(urlEqualTo(expectedPath))
              .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                      .withBody("{\"result\": \"ok\"}")));
      stubModelDownload("https://modelatlas.example.com/model1-updated");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1-updated");
      input.setModelName("UpdatedModel");
      input.setModel(modelContent);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      verify(postRequestedFor(urlEqualTo(expectedPath)).withRequestBody(equalTo(modelContent)));
    }

    @Test
    @DisplayName("Should not upload model to Model Atlas when model is not provided")
    void shouldNotUploadModelWhenNotProvided() {
      stubModelDownload("https://modelatlas.example.com/model1-updated");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1-updated");
      input.setModelName("UpdatedModel");
      // No model content

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName("Should skip upload when model is provided but modelAtlasUri is missing")
    void shouldSkipUploadWhenModelProvidedButNoModelAtlasUri() {
      // The mapper's SET_TO_NULL policy clears an existing modelAtlasUri when not in input
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelName("TestModel");
      input.setModel(modelContent);
      // No modelAtlasUri — upload must be skipped, not fail

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName("Should roll back update when Model Atlas upload fails")
    void shouldRollBackUpdateWhenModelAtlasUploadFails() {
      String expectedPath =
          String.format(
              "/atlas/rest/%s/schema/stages/%s?nsUri=%s&overwrite=true",
              TEST_SCOPE, TEST_STAGE, TEST_NS_URI);

      stubFor(
          post(urlEqualTo(expectedPath))
              .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/uml"))
              .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelAtlasUri(TEST_NS_URI);
      input.setModelName("TestModel");
      input.setModel(modelContent);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

      // Stub download for the verification GET (version1 rolled back to original modelAtlasUri)
      stubModelDownload("https://modelatlas.example.com/model1");

      // Verify entity was not modified (transaction rolled back)
      DataStructureVersionOutputDTO unchanged =
          restTemplate
              .exchange(
                  getEndpoint() + "/" + versionId1,
                  HttpMethod.GET,
                  new HttpEntity<>(createAuthHeaders()),
                  getOutputTypeReference())
              .getBody();
      assertThat(unchanged).isNotNull();
      assertThat(unchanged.getVersion()).isEqualTo("1.0.0");
      assertThat(unchanged.getModelAtlasUri()).isEqualTo("https://modelatlas.example.com/model1");
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent version")
    void shouldReturn404WhenUpdatingNonExistent() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("99.0.0");
      input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelAtlasUri("https://modelatlas.example.com/model");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should maintain dataStructureId on update")
    void shouldMaintainDataStructureIdOnUpdate() {
      stubModelDownload("https://modelatlas.example.com/model");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.2.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelAtlasUri("https://modelatlas.example.com/model");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataStructure()).isNotNull();
      assertThat(output.getDataStructure().getId())
          .as("DataStructure ID should remain unchanged")
          .isEqualTo(dataStructureId);
    }
  }

  @Nested
  @DisplayName("Delete DataStructureVersion Tests")
  class DeleteDataStructureVersionTests {

    @Test
    @DisplayName("Should delete data structure version successfully")
    void shouldDeleteDataStructureVersionSuccessfully() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<String> getResponse =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(getResponse.getStatusCode())
          .as("Should return NOT_FOUND after deletion")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent version")
    void shouldReturn404WhenDeletingNonExistent() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }
}
