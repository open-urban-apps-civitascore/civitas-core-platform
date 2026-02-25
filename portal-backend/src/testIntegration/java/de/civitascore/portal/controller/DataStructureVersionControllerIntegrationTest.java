package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
@DisplayName("DataStructureVersion Controller Integration Tests")
class DataStructureVersionControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DataStructureRepository dataStructureRepository;

  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;

  @Autowired private ObjectMapper objectMapper;

  private UUID dataStructureId;
  private UUID versionId1;

  @BeforeEach
  void initTestData() {
    // Create parent data structure directly in repository
    DataStructure dataStructure = new DataStructure();
    dataStructure.setName("Test Data Structure");
    dataStructure.setDescription("Data structure for version testing");
    dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure.setCreatedFromDataSource(false);
    dataStructure = dataStructureRepository.save(dataStructure);
    dataStructureId = dataStructure.getId();

    // Create test versions directly in repository
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

  @Nested
  @DisplayName("Create DataStructureVersion Tests")
  class CreateDataStructureVersionTests {

    @Test
    @DisplayName("Should create data structure version successfully with valid data")
    void shouldCreateDataStructureVersionSuccessfully() throws Exception {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setVersion("3.0.0");
      input.setDescription("Third version with new features");
      input.setModelAtlasUri("https://modelatlas.example.com/model3");
      input.setModelName("TestModel3");

      Map<String, Object> styles = new HashMap<>();
      styles.put("color", "green");
      styles.put("size", 15);
      input.setStyles(styles);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("id").asText()).as("ID should be generated").isNotNull();
      assertThat(actual.get("version").asText())
          .as("Version should match input")
          .isEqualTo("3.0.0");
      assertThat(actual.get("description").asText())
          .as("Description should match input")
          .isEqualTo("Third version with new features");
      assertThat(actual.get("dataStructureVersionStatus").asText())
          .as("Status should be set to DRAFT by preProcessCreateInput hook")
          .isEqualTo("DRAFT");
      assertThat(actual.get("dataStructureVersionSource").asText())
          .as("Source should match input")
          .isEqualTo("OWN");
      assertThat(actual.get("modelAtlasUri").asText())
          .as("Model atlas URI should match input")
          .isEqualTo("https://modelatlas.example.com/model3");
      assertThat(actual.get("modelName").asText())
          .as("Model name should match input")
          .isEqualTo("TestModel3");
      assertThat(actual.get("styles").get("color").asText())
          .as("Styles color should match input")
          .isEqualTo("green");
      assertThat(actual.get("dataStructure").get("id").asText())
          .as("DataStructure ID should be set from path variable")
          .isEqualTo(dataStructureId.toString());
      assertThat(actual.get("createdAt").asText())
          .as("Created timestamp should be set")
          .isNotNull();

      assertThat(response.getHeaders().getLocation())
          .as("Location header should be present")
          .isNotNull();
    }

    @Test
    @DisplayName("Should fail to create version with missing required field")
    void shouldFailToCreateVersionWithMissingVersion() {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      // Missing version field
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
    @DisplayName("Should set dataStructureId from path variable, not from payload")
    void shouldSetDataStructureIdFromPathVariable() throws Exception {
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
      // Try to set a different dataStructureId - should be ignored due to @JsonIgnore
      input.setDataStructureId(otherDataStructureId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("dataStructure").get("id").asText())
          .as("DataStructure ID should be from path variable, not payload")
          .isEqualTo(dataStructureId.toString())
          .isNotEqualTo(otherDataStructureId.toString());
    }
  }

  @Nested
  @DisplayName("Read DataStructureVersion Tests")
  class ReadDataStructureVersionTests {

    @Test
    @DisplayName("Should retrieve data structure version by ID successfully")
    void shouldRetrieveDataStructureVersionById() throws Exception {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("id").asText()).isEqualTo(versionId1.toString());
      assertThat(actual.get("version").asText()).isEqualTo("1.0.0");
      assertThat(actual.get("description").asText())
          .isEqualTo("First version of the test data structure");
      assertThat(actual.get("dataStructureVersionStatus").asText()).isEqualTo("DRAFT");
      assertThat(actual.get("dataStructureVersionSource").asText()).isEqualTo("OWN");
      assertThat(actual.get("modelAtlasUri").asText())
          .isEqualTo("https://modelatlas.example.com/model1");
      assertThat(actual.get("modelName").asText()).isEqualTo("TestModel1");
      assertThat(actual.get("styles").get("color").asText()).isEqualTo("blue");
      assertThat(actual.get("dataStructure").get("id").asText())
          .isEqualTo(dataStructureId.toString());
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
    void shouldRetrieveAllVersions() throws Exception {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint(), HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("content").isArray()).isTrue();
      assertThat(actual.get("content").size())
          .as("Should return at least 2 versions")
          .isGreaterThanOrEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Update DataStructureVersion Tests")
  class UpdateDataStructureVersionTests {

    @Test
    @DisplayName("Should update data structure version successfully")
    void shouldUpdateDataStructureVersionSuccessfully() throws Exception {
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

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("id").asText()).isEqualTo(versionId1.toString());
      assertThat(actual.get("version").asText()).isEqualTo("1.1.0");
      assertThat(actual.get("description").asText())
          .isEqualTo("Updated description for version 1.1.0");
      assertThat(actual.get("dataStructureVersionStatus").asText())
          .as("Status should remain DRAFT (not changed by update)")
          .isEqualTo("DRAFT");
      assertThat(actual.get("modelAtlasUri").asText())
          .isEqualTo("https://modelatlas.example.com/model1-updated");
      assertThat(actual.get("modelName").asText()).isEqualTo("TestModel1-Updated");
      assertThat(actual.get("styles").get("color").asText()).isEqualTo("purple");
      assertThat(actual.get("styles").get("size").asInt()).isEqualTo(25);
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
    void shouldMaintainDataStructureIdOnUpdate() throws Exception {
      DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
      input.setVersion("1.2.0");
      input.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      input.setModelAtlasUri("https://modelatlas.example.com/model");

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpoint() + "/" + versionId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      var actual = objectMapper.readTree(response.getBody());
      assertThat(actual.get("dataStructure").get("id").asText())
          .as("DataStructure ID should remain unchanged")
          .isEqualTo(dataStructureId.toString());
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

      // Verify deletion
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
