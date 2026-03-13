package de.civitascore.portal.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
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
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

  @Autowired private DataSourceRepository dataSourceRepository;

  private static final String TEST_NS_URI = "http://test/test/myuml/1.0.0";
  private static final String TEST_MODEL_FILE_PATH = "mocks/models/Simple_model.xmi";
  private static final String TEST_SCOPE = "default";
  private static final String TEST_STAGE = "draft";
  private static final String MOCK_MODEL_RESPONSE = "<xml>mock model content</xml>";
  private static final String MOCK_UPLOAD_METADATA =
      "{\"eClass\":\"http://eclipse.org/fennec/model/atlas/management/1.0.0#//ObjectMetadata\","
          + "\"objectId\":\"dGVzdE9iamVjdElk\",\"objectName\":\"TestModel\","
          + "\"stage\":\"draft\",\"scope\":\"default\",\"registry\":\"schema\"}";

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
    dataSourceRepository.deleteAll();
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
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(MOCK_UPLOAD_METADATA)));

    return expectedPath;
  }

  private String stubModelDelete(String nsUri) {
    String deletePath =
        String.format("/atlas/rest/%s/schema/stages/%s?nsUri=%s", TEST_SCOPE, TEST_STAGE, nsUri);
    stubFor(delete(urlEqualTo(deletePath)).willReturn(aResponse().withStatus(200)));
    return deletePath;
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
    @DisplayName("Should return 502 and roll back create when Model Atlas upload fails")
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

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

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

    @Test
    @DisplayName(
        "Should return 409 when creating a version whose version string already exists for the same DataStructure")
    void shouldReturn409WhenCreatingDuplicateVersion() {
      // "1.0.0" is already saved in initTestData for dataStructureId
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.0.0");
      input.setDescription("Duplicate of existing version 1.0.0");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT for duplicate version string within the same DataStructure")
          .isEqualTo(HttpStatus.CONFLICT);
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
    @DisplayName("Should retrieve all versions for a data structure")
    void shouldRetrieveAllVersions() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  @DisplayName("Update DataStructureVersion Tests")
  class UpdateDataStructureVersionTests {

    @Test
    @DisplayName("Should update data structure version successfully")
    void shouldUpdateDataStructureVersionSuccessfully() {
      stubModelDelete("https://modelatlas.example.com/model1");
      stubModelDownload("https://modelatlas.example.com/model1-updated");
      String expectedUploadPath = stubModelUpload("https://modelatlas.example.com/model1-updated");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setDescription("Updated description for version 1.1.0");
      input.setModelAtlasUri("https://modelatlas.example.com/model1-updated");
      input.setModelName("TestModel1-Updated");
      input.setModel(modelContent);

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

      verify(
          postRequestedFor(urlEqualTo(expectedUploadPath)).withRequestBody(equalTo(modelContent)));
    }

    @Test
    @DisplayName("Should upload model to Model Atlas when model is provided")
    void shouldUploadModelWhenProvided() {
      stubModelDelete("https://modelatlas.example.com/model1");

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
                      .withBody(MOCK_UPLOAD_METADATA)));
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
    @DisplayName("Should fail upload when model is provided but modelAtlasUri is missing")
    void shouldFailUploadWhenModelProvidedButNoModelAtlasUri() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelName("TestModel");
      input.setModel(modelContent);
      // No modelAtlasUri

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName(
        "Should allow update with modelAtlasUri but no model (keeps existing model at URI)")
    void shouldAllowUpdateWhenModelAtlasUriProvidedButNoModel() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("1.1.0");
      input.setModelName("TestModel");
      input.setModelAtlasUri(TEST_NS_URI);
      // No model — should be allowed, no upload happens

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModelAtlasUri())
          .as("ModelAtlasUri should be updated")
          .isEqualTo(TEST_NS_URI);
      // No upload should have been attempted since model content was not provided
      verify(0, postRequestedFor(urlMatching("/atlas/rest/.*/schema/stages/.*")));
    }

    @Test
    @DisplayName("Should return 502 and roll back update when Model Atlas upload fails")
    void shouldRollBackUpdateWhenModelAtlasUploadFails() {
      stubModelDelete("https://modelatlas.example.com/model1");

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

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

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

    @Test
    @DisplayName(
        "Should return 409 when updating a version to a version string that already exists for the same DataStructure")
    void shouldReturn400WhenUpdatingToDuplicateVersion() {
      // versionId1 has "1.0.0"; "2.0.0" is already used by another version in the same
      // DataStructure
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("2.0.0");
      input.setDescription("Trying to use a version string already taken by another version");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as(
              "Should return CONFLICT when updating to a version string already used by another version in the same DataStructure")
          .isEqualTo(HttpStatus.CONFLICT);

      DataStructureVersion unchanged =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(unchanged.getVersion())
          .as("Version string of versionId1 must not have changed")
          .isEqualTo("1.0.0");
    }
  }

  @Nested
  @DisplayName("Patch DataStructureVersion Tests")
  class PatchDataStructureVersionTests {

    @Test
    @DisplayName("Should patch description without requiring model")
    void shouldPatchDescriptionWithoutRequiringModel() {
      // Stub model download so the patch() override can load the existing model for the response
      stubModelDownload("https://modelatlas.example.com/model1");

      Map<String, Object> patchMap =
          Collections.singletonMap("description", "Patched version description");

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("PATCH with only description should return OK")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getDescription()).isEqualTo("Patched version description");
      assertThat(output.getVersion()).as("Version should remain unchanged").isEqualTo("1.0.0");
      assertThat(output.getModelAtlasUri())
          .as("ModelAtlasUri should remain unchanged")
          .isEqualTo("https://modelatlas.example.com/model1");

      // Verify model was downloaded from Model Atlas (loaded by patch() override for response)
      verify(
          getRequestedFor(
              urlEqualTo(
                  String.format(
                      "/atlas/rest/%s/schema/stages/%s/content?nsUri=%s",
                      TEST_SCOPE, TEST_STAGE, "https://modelatlas.example.com/model1"))));
    }

    @Test
    @DisplayName("Should patch with model and modelAtlasUri")
    void shouldPatchWithModelAndModelAtlasUri() {
      stubModelDownload("https://modelatlas.example.com/model1-patched");
      String expectedUploadPath = stubModelUpload("https://modelatlas.example.com/model1-patched");

      Map<String, Object> patchMap =
          Map.of(
              "modelAtlasUri",
              "https://modelatlas.example.com/model1-patched",
              "model",
              modelContent);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("PATCH with model should return OK")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModelAtlasUri())
          .isEqualTo("https://modelatlas.example.com/model1-patched");

      verify(postRequestedFor(urlEqualTo(expectedUploadPath)));
    }

    @Test
    @DisplayName("Should return 404 when patching non-existent version")
    void shouldReturn404WhenPatchingNonExistent() {
      Map<String, Object> patchMap = Collections.singletonMap("description", "Patched description");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID(),
              HttpMethod.PATCH,
              new HttpEntity<>(patchMap, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Delete DataStructureVersion Tests")
  class DeleteDataStructureVersionTests {

    @Test
    @DisplayName("Should delete data structure version and model from Atlas successfully")
    void shouldDeleteDataStructureVersionSuccessfully() {
      String expectedDeletePath = stubModelDelete("https://modelatlas.example.com/model1");

      ResponseEntity<Void> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      verify(deleteRequestedFor(urlEqualTo(expectedDeletePath)));

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
    @DisplayName("Should fail to delete last published version of a published data structure")
    void shouldFailToDeleteLastPublishedVersionOfPublishedDataStructure() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Try to delete the only published version - should fail
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      // Verify dataStructure and version still exist
      assertThat(dataStructureRepository.existsById(dataStructureId)).isTrue();
      assertThat(dataStructureVersionRepository.existsById(versionId1)).isTrue();
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

  @Nested
  @DisplayName("Publish DataStructureVersion Tests")
  class PublishDataStructureVersionTests {

    @Test
    @DisplayName("Should publish version with modelAtlasUri successfully")
    void shouldPublishVersionWithModelAtlasUri() {
      DataStructureVersion version =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
      assertThat(version.getModelAtlasUri()).isNotBlank();

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be AVAILABLE after publishing")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
      assertThat(output.getVersion()).isEqualTo("1.0.0");
      assertThat(output.getModelAtlasUri()).isEqualTo("https://modelatlas.example.com/model1");

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish version without modelAtlasUri")
    void shouldFailToPublishVersionWithoutModelAtlasUri() {
      DataStructureVersion versionWithoutUri = new DataStructureVersion();
      versionWithoutUri.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      versionWithoutUri.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      versionWithoutUri.setVersion("3.0.0");
      versionWithoutUri.setModelName("TestModel3");
      versionWithoutUri.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      versionWithoutUri = dataStructureVersionRepository.save(versionWithoutUri);
      UUID versionWithoutUriId = versionWithoutUri.getId();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionWithoutUriId + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionWithoutUriId).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Status should remain DRAFT")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to publish already published version")
    void shouldFailToPublishAlreadyPublishedVersion() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      // Try to publish again via API
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when publishing non-existent version")
    void shouldReturn404WhenPublishingNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to publish without authentication")
    void shouldFailToPublishWithoutAuth() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Unpublish DataStructureVersion Tests")
  class UnpublishDataStructureVersionTests {

    @Test
    @DisplayName("Should unpublish published version successfully")
    void shouldUnpublishPublishedVersionSuccessfully() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructureVersion publishedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(publishedVersion.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(versionId1);
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should be DRAFT after unpublishing")
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to unpublish already unpublished version")
    void shouldFailToUnpublishDraftVersion() {
      DataStructureVersion version =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(version.getDataStructureVersionStatus())
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when unpublishing non-existent version")
    void shouldReturn404WhenUnpublishingNonExistentVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to unpublish without authentication")
    void shouldFailToUnpublishWithoutAuth() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to unpublish the only published version of a published DataStructure")
    void shouldFailToUnpublishOnlyPublishedVersionOfPublishedDataStructure() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Try to unpublish the only published version - should fail
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      // Verify version is still published
      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Version should remain published")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName(
        "Should allow unpublishing when DataStructure has multiple published versions and is published")
    void shouldAllowUnpublishingWhenMultiplePublishedVersionsExist() {
      DataStructureVersion version3 = new DataStructureVersion();
      version3.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      version3.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      version3.setVersion("3.0.0");
      version3.setModelAtlasUri("https://modelatlas.example.com/model3");
      version3.setModelName("TestModel3");
      version3.setDataStructure(dataStructureRepository.findById(dataStructureId).orElseThrow());
      version3 = dataStructureVersionRepository.save(version3);
      UUID version3Id = version3.getId();

      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      version3.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version3);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructureRepository.save(dataStructure);

      // Now unpublish one version - should succeed because there's another published version
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      // Verify version1 is unpublished
      DataStructureVersion reloadedVersion1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion1.getDataStructureVersionStatus())
          .as("Version 1 should be unpublished")
          .isEqualTo(DataStructureVersionStatus.DRAFT);

      // Verify version3 is still published
      DataStructureVersion reloadedVersion3 =
          dataStructureVersionRepository.findById(version3Id).orElseThrow();
      assertThat(reloadedVersion3.getDataStructureVersionStatus())
          .as("Version 3 should still be published")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);

      // Verify DataStructure is still published
      DataStructure reloadedDataStructure =
          dataStructureRepository.findById(dataStructureId).orElseThrow();
      assertThat(reloadedDataStructure.getDataStructureStatus())
          .as("DataStructure should remain published")
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should allow unpublishing when DataStructure is in DRAFT status")
    void shouldAllowUnpublishingWhenDataStructureIsDraft() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructure dataStructure = dataStructureRepository.findById(dataStructureId).orElseThrow();
      assertThat(dataStructure.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);

      // Unpublish the version - should succeed because DataStructure is DRAFT
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1 + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      // Verify version is unpublished
      DataStructureVersion reloadedVersion =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      assertThat(reloadedVersion.getDataStructureVersionStatus())
          .as("Version should be unpublished")
          .isEqualTo(DataStructureVersionStatus.DRAFT);
    }
  }

  @Nested
  @DisplayName("Update Published Meta Tests")
  class UpdatePublishedMetaTests {

    private UUID publishedVersionId;

    @BeforeEach
    void publishVersion() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      publishedVersionId = versionId1;
    }

    @Test
    @DisplayName(
        "Should allow full update (version, styles, modelAtlasUri) via published/meta when not in use")
    void shouldAllowFullUpdateWhenNotInUse() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.1.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelName("UpdatedPublishedModel");

      Map<String, Object> newStyles = new HashMap<>();
      newStyles.put("color", "red");
      newStyles.put("size", 30);
      input.setStyles(newStyles);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + publishedVersionId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(publishedVersionId);
      assertThat(output.getModelName())
          .as("Model name should be updated")
          .isEqualTo("UpdatedPublishedModel");
      assertThat(output.getVersion()).as("Version should be updated").isEqualTo("1.1.0");
      assertThat(output.getStyles().get("color")).as("Styles should be updated").isEqualTo("red");
      assertThat(output.getDataStructureVersionStatus())
          .as("Status should remain AVAILABLE")
          .isEqualTo(DataStructureVersionStatus.AVAILABLE);
    }

    @Test
    @DisplayName(
        "Should delete old model and upload new one when modelAtlasUri changes via published/meta")
    void shouldAllowModelAtlasUriChangeWhenNotInUse() {
      String expectedDeletePath = stubModelDelete("https://modelatlas.example.com/model1");
      stubModelUpload("https://modelatlas.example.com/updated-model");
      stubModelDownload("https://modelatlas.example.com/updated-model");

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/updated-model");
      input.setModel(modelContent);
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + publishedVersionId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getModelAtlasUri())
          .as("ModelAtlasUri should be updated when not in use")
          .isEqualTo("https://modelatlas.example.com/updated-model");

      verify(deleteRequestedFor(urlEqualTo(expectedDeletePath)));
    }

    @Test
    @DisplayName("Should fail to update published meta for DRAFT version")
    void shouldFailToUpdatePublishedMetaForDraftVersion() {
      DataStructureVersion draftVersion = new DataStructureVersion();
      draftVersion.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draftVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      draftVersion.setVersion("4.0.0");
      draftVersion.setModelAtlasUri("https://modelatlas.example.com/model4");
      draftVersion.setModelName("TestModel4");
      draftVersion.setDataStructure(
          dataStructureRepository.findById(dataStructureId).orElseThrow());
      draftVersion = dataStructureVersionRepository.save(draftVersion);
      UUID draftVersionId = draftVersion.getId();

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("4.1.0");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + draftVersionId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when updating published meta for non-existent version")
    void shouldReturn404WhenUpdatingPublishedMetaForNonExistentVersion() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("99.0.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + UUID.randomUUID() + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update published meta without authentication")
    void shouldFailToUpdatePublishedMetaWithoutAuth() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.2.0");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + publishedVersionId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("PATCH Tests")
  class PatchTests {

    @Test
    @DisplayName("Should patch description without triggering model validation error")
    void shouldPatchDescriptionWithoutModelValidationError() {
      String patchBody = "{\"description\": \"Patched version description\"}";

      HttpHeaders headers = createAuthHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PATCH,
              new HttpEntity<>(patchBody, headers),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Patched version description");
      assertThat(response.getBody().getModelAtlasUri())
          .as("ModelAtlasUri should remain unchanged")
          .isEqualTo("https://modelatlas.example.com/model1");
    }
  }

  @Nested
  @DisplayName("Regular Update Restrictions Tests")
  class RegularUpdateRestrictionsTests {

    @Test
    @DisplayName("Should fail to update published version with regular PUT endpoint")
    void shouldFailToUpdatePublishedVersionWithRegularPut() {
      DataStructureVersion version1 =
          dataStructureVersionRepository.findById(versionId1).orElseThrow();
      version1.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      dataStructureVersionRepository.save(version1);

      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("2.0.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("InUse Guard Tests")
  class InUseGuardTests {

    private UUID inUseDataStructureId;
    private UUID inUseVersionId;

    @BeforeEach
    void setupInUseVersion() {
      cleanup();

      DataStructure ds = new DataStructure();
      ds.setName("InUse Data Structure");
      ds.setDescription("Data structure with in-use version");
      ds.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      ds.setCreatedFromDataSource(false);
      ds = dataStructureRepository.save(ds);
      inUseDataStructureId = ds.getId();

      DataStructureVersion version = new DataStructureVersion();
      version.setDataStructure(ds);
      version.setVersion("1.0.0");
      version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      version.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      version.setModelAtlasUri("http://modelatlas.example.com/models/inuse");
      version.setModelName("InUse Model");
      version = dataStructureVersionRepository.save(version);
      inUseVersionId = version.getId();

      DataSource dataSource = new DataSource();
      dataSource.setName("ds_referencing_" + UUID.randomUUID().toString().substring(0, 8));
      dataSource.setDataSourceStatus(DataSourceStatus.DRAFT);
      dataSource.setDataStructureVersion(version);
      dataSourceRepository.save(dataSource);
    }

    @Test
    @DisplayName("Should return 409 when unpublishing in-use version")
    void shouldReturn409WhenUnpublishingInUseVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions"
                  + "/"
                  + inUseVersionId
                  + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return 409 when deleting in-use version")
    void shouldReturn409WhenDeletingInUseVersion() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              "/datastructures/" + inUseDataStructureId + "/versions" + "/" + inUseVersionId,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return inUse=true in version output DTO")
    void shouldReturnInUseTrueInVersionOutputDTO() {
      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/" + inUseDataStructureId + "/versions" + "/" + inUseVersionId,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be true when a DataSource references this version")
          .isTrue();
    }

    @Test
    @DisplayName(
        "Should protect modelAtlasUri and version via published/meta when version is in use")
    void shouldProtectStructuralFieldsWhenInUse() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("2.0.0");
      input.setModelAtlasUri("https://modelatlas.example.com/SHOULD_NOT_CHANGE");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelName("UpdatedModelName");

      input.setStyles(Collections.singletonMap("color", "green"));

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions/"
                  + inUseVersionId
                  + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      DataStructureVersionOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getModelAtlasUri())
          .as("ModelAtlasUri should be protected when in use")
          .isEqualTo("http://modelatlas.example.com/models/inuse");
      assertThat(output.getVersion())
          .as("Version should be protected when in use")
          .isEqualTo("1.0.0");
      assertThat(output.getStyles().get("color"))
          .as("Styles should be protected when in use")
          .isNull();
      assertThat(output.getModelName())
          .as("ModelName should be updatable even when in use")
          .isEqualTo("UpdatedModelName");
    }

    @Test
    @DisplayName("Should return inUse=false when no DataSource references version")
    void shouldReturnInUseFalseWhenNoDataSourceReferences() {
      DataStructureVersion notInUseVersion = new DataStructureVersion();
      notInUseVersion.setDataStructure(
          dataStructureRepository.findById(inUseDataStructureId).orElseThrow());
      notInUseVersion.setVersion("2.0.0");
      notInUseVersion.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      notInUseVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      notInUseVersion = dataStructureVersionRepository.save(notInUseVersion);

      ResponseEntity<DataStructureVersionOutputDTO> response =
          restTemplate.exchange(
              "/datastructures/"
                  + inUseDataStructureId
                  + "/versions"
                  + "/"
                  + notInUseVersion.getId(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be false when no DataSource references this version")
          .isFalse();
    }
  }
}
