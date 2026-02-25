package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.RestPage;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
@DisplayName("DataStructure Controller Integration Tests")
class DataStructureControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DataStructureRepository dataStructureRepository;

  @Autowired private AssignmentRepository assignmentRepository;

  @Autowired private DataStructureVersionRepository dataStructureVersionRepository;

  @Autowired private GroupRepository groupRepository;

  @Autowired private RoleRepository roleRepository;

  private static final String ENDPOINT = "/datastructures";

  private UUID dataStructureId1;

  @BeforeEach
  void initTestData() {
    // Create test data directly in repository
    DataStructure dataStructure1 = new DataStructure();
    dataStructure1.setName("Test Data Structure 1");
    dataStructure1.setDescription("First test data structure");
    dataStructure1.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure1.setCreatedFromDataSource(false);
    dataStructure1 = dataStructureRepository.save(dataStructure1);
    dataStructureId1 = dataStructure1.getId();

    DataStructure dataStructure2 = new DataStructure();
    dataStructure2.setName("Test Data Structure 2");
    dataStructure2.setDescription("Second test data structure");
    dataStructure2.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure2.setCreatedFromDataSource(true);
    dataStructureRepository.save(dataStructure2);
  }

  void initTestDataWithRelationships() {
    // Clean up first
    cleanup();

    // Create test data structure with relationships
    DataStructure dataStructure = new DataStructure();
    dataStructure.setName("Test Data Structure 1");
    dataStructure.setDescription("First test data structure");
    dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure.setCreatedFromDataSource(false);
    dataStructure = dataStructureRepository.save(dataStructure);
    dataStructureId1 = dataStructure.getId();

    // Create DataStructureVersion
    DataStructureVersion version1 = new DataStructureVersion();
    version1.setDataStructure(dataStructure);
    version1.setVersion("1.0.0");
    version1.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    version1.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    version1.setModelAtlasUri("http://modelatlas.example.com/models/1");
    version1.setModelName("Test Model v1");
    Map<String, Object> styles1 = new HashMap<>();
    styles1.put("color", "blue");
    styles1.put("size", "large");
    version1.setStyles(styles1);
    dataStructureVersionRepository.save(version1);

    DataStructureVersion version2 = new DataStructureVersion();
    version2.setDataStructure(dataStructure);
    version2.setVersion("2.0.0");
    version2.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    version2.setDataStructureVersionSource(DataStructureVersionSource.OWN);
    version2.setModelAtlasUri("http://modelatlas.example.com/models/2");
    version2.setModelName("Test Model v2");
    Map<String, Object> styles2 = new HashMap<>();
    styles2.put("color", "red");
    styles2.put("size", "medium");
    version2.setStyles(styles2);
    dataStructureVersionRepository.save(version2);

    // Create Group
    Group group = new Group();
    group.setName("Test Group");
    group.setDescription("Test group for assignments");
    group = groupRepository.save(group);

    // Create Role
    Role role = new Role();
    role.setName("Test Role");
    role.setDescription("Test role for assignments");
    role.setRoleType(RoleType.DATA);
    role = roleRepository.save(role);

    // Create Assignment
    Assignment assignment1 = new Assignment();
    assignment1.setGroup(group);
    assignment1.setRole(role);
    assignment1.setScopeType(ScopeType.DATASTRUCTURE);
    assignment1.setDataStructure(dataStructure);
    assignmentRepository.save(assignment1);

    // Create a second data structure for other tests
    DataStructure dataStructure2 = new DataStructure();
    dataStructure2.setName("Test Data Structure 2");
    dataStructure2.setDescription("Second test data structure");
    dataStructure2.setDataStructureStatus(DataStructureStatus.DRAFT);
    dataStructure2.setCreatedFromDataSource(true);
    dataStructureRepository.save(dataStructure2);
  }

  @AfterEach
  void cleanup() {
    assignmentRepository.deleteAll();
    dataStructureVersionRepository.deleteAll();
    dataStructureRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
  }

  //  @Override
  protected ParameterizedTypeReference<DataStructureOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  protected ParameterizedTypeReference<RestPage<DataStructureOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    return headers;
  }

  @Nested
  @DisplayName("Create DataStructure Tests")
  class CreateDataStructureTests {

    @Test
    @DisplayName("Should create data structure successfully with valid data")
    void shouldCreateDataStructureSuccessfully() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("New Data Structure");
      input.setDescription("A new test data structure");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT,
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo("New Data Structure");
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo("A new test data structure");
      assertThat(output.getDataStructureStatus())
          .as("Status should be set to DRAFT by preCreate hook")
          .isEqualTo(DataStructureStatus.DRAFT);
      assertThat(output.getCreatedFromDataSource())
          .as("CreatedFromDataSource should match input")
          .isFalse();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();

      assertThat(response.getHeaders().getLocation())
          .as("Location header should be present")
          .isNotNull();
    }

    @Test
    @DisplayName("Should fail to create data structure with missing required field")
    void shouldFailToCreateDataStructureWithMissingName() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setDescription("Missing name");
      input.setDataStructureStatus(DataStructureStatus.DRAFT);
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT,
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create data structure without authentication")
    void shouldFailToCreateDataStructureWithoutAuth() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Unauthorized");
      input.setDataStructureStatus(DataStructureStatus.DRAFT);
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(input), String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to create duplicate data structure with same name")
    void shouldFailToCreateDuplicateDataStructure() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Unique Data Structure");
      input.setDescription("First one");
      input.setDataStructureStatus(DataStructureStatus.DRAFT);
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> firstResponse =
          restTemplate.exchange(
              ENDPOINT,
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      ResponseEntity<String> secondResponse =
          restTemplate.exchange(
              ENDPOINT,
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(secondResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate")
          .isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Read DataStructure Tests")
  class ReadDataStructureTests {

    @Test
    @DisplayName("Should retrieve data structure by ID successfully")
    void shouldRetrieveDataStructureById() {
      // Initialize test data with relationships
      initTestDataWithRelationships();

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId().toString()).isEqualTo(dataStructureId1.toString());
      assertThat(output.getName()).isEqualTo("Test Data Structure 1");
      assertThat(output.getDescription()).isEqualTo("First test data structure");
      assertThat(output.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);
      assertThat(output.getCreatedFromDataSource()).isFalse();

      // Check relationships
      // Check DataStructureVersions
      assertThat(output.getDataStructureVersions())
          .as("Should have dataStructureVersions field")
          .isNotNull();
      assertThat(output.getDataStructureVersions())
          .as("Should have 2 data structure versions")
          .hasSize(2);

      // Verify version details
      DataStructureVersionSummaryDTO version1 = output.getDataStructureVersions().getFirst();
      assertThat(version1.getId()).as("Version should have an ID").isNotNull();
      assertThat(version1.getVersion()).as("Version should have a version string").isNotNull();
      assertThat(version1.getDataStructureVersionStatus())
          .as("Version should have a status")
          .isNotNull();
      assertThat(version1.getDataStructureVersionSource())
          .as("Version should have a source")
          .isNotNull();

      // Check Assignments
      assertThat(output.getAssignments()).as("Should have assignments field").isNotNull();
      assertThat(output.getAssignments()).as("Should have 1 assignment").hasSize(1);

      // Verify assignment details
      AssignmentOutputDTO assignment = output.getAssignments().getFirst();
      assertThat(assignment.getId()).as("Assignment should have an ID").isNotNull();
      assertThat(assignment.getScopeType())
          .as("Assignment scope type should be DATASTRUCTURE")
          .isEqualTo(ScopeType.DATASTRUCTURE);
      // FIXME: Nested dependencies of assignment are not yet being mapped in the outputDTO
    }

    @Test
    @DisplayName("Should return 404 for non-existent data structure")
    void shouldReturn404ForNonExistentDataStructure() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID(),
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should retrieve all data structures")
    void shouldRetrieveAllDataStructures() {
      ResponseEntity<RestPage<DataStructureOutputDTO>> response =
          restTemplate.exchange(
              ENDPOINT,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getPageTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<DataStructureOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should have content").isNotNull();
      assertThat(page.getContent())
          .as("Should return at least 2 items")
          .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should filter data structures by name")
    void shouldFilterDataStructuresByName() {
      ResponseEntity<RestPage<DataStructureOutputDTO>> response =
          restTemplate.exchange(
              ENDPOINT + "?name=Structure 1",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              getPageTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<DataStructureOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should return filtered results").hasSizeGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("Update DataStructure Tests")
  class UpdateDataStructureTests {

    @Test
    @DisplayName("Should update data structure successfully")
    void shouldUpdateDataStructureSuccessfully() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Updated Data Structure");
      input.setDescription("Updated description");
      input.setCreatedFromDataSource(true);

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructureId1);
      assertThat(output.getName()).isEqualTo("Updated Data Structure");
      assertThat(output.getDescription()).isEqualTo("Updated description");
      assertThat(output.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);
      assertThat(output.getCreatedFromDataSource()).isTrue();
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent data structure")
    void shouldReturn404WhenUpdatingNonExistent() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Non-existent");
      input.setDataStructureStatus(DataStructureStatus.DRAFT);
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID(),
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update data structure with blank name")
    void shouldFailToUpdateWithBlankName() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("");
      input.setDescription("Empty name");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId1,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to update published data structure with regular PUT endpoint")
    void shouldFailToUpdatePublishedDataStructureWithRegularPut() {
      DataStructure dataStructure = new DataStructure();
      dataStructure.setName("Test Data Structure for Regular Update");
      dataStructure.setDescription("Testing regular update restrictions");
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setCreatedFromDataSource(false);
      dataStructure = dataStructureRepository.save(dataStructure);
      UUID dataStructureId = dataStructure.getId();

      DataStructureVersion publishedVersion = new DataStructureVersion();
      publishedVersion.setDataStructure(dataStructure);
      publishedVersion.setVersion("1.0.0");
      publishedVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      publishedVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      publishedVersion.setModelAtlasUri("http://modelatlas.example.com/models/published");
      publishedVersion.setModelName("Published Model");
      dataStructureVersionRepository.save(publishedVersion);

      restTemplate.exchange(
          ENDPOINT + "/" + dataStructureId + "/publish",
          HttpMethod.POST,
          new HttpEntity<>(createAuthHeaders()),
          getOutputTypeReference());

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Trying to update published with regular PUT");
      input.setDescription("This should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId,
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Delete DataStructure Tests")
  class DeleteDataStructureTests {

    @Test
    @DisplayName("Should delete data structure successfully")
    void shouldDeleteDataStructureSuccessfully() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId1,
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      // Verify deletion
      ResponseEntity<String> getResponse =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId1,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(getResponse.getStatusCode())
          .as("Should return NOT_FOUND after deletion")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent data structure")
    void shouldReturn404WhenDeletingNonExistent() {
      ResponseEntity<Void> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              new HttpEntity<>(createAuthHeaders()),
              Void.class);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Publish DataStructure Tests")
  class PublishDataStructureTests {

    private UUID dataStructureWithPublishedVersionId;

    @BeforeEach
    void setupPublishableDataStructure() {
      // Clean up default test data
      cleanup();

      // Create a data structure with at least one published version
      DataStructure dataStructure = new DataStructure();
      dataStructure.setName("Publishable Data Structure");
      dataStructure.setDescription("Data structure with published version");
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setCreatedFromDataSource(false);
      dataStructure = dataStructureRepository.save(dataStructure);
      dataStructureWithPublishedVersionId = dataStructure.getId();

      // Create a published version
      DataStructureVersion publishedVersion = new DataStructureVersion();
      publishedVersion.setDataStructure(dataStructure);
      publishedVersion.setVersion("1.0.0");
      publishedVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      publishedVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      publishedVersion.setModelAtlasUri("http://modelatlas.example.com/models/published");
      publishedVersion.setModelName("Published Model");
      dataStructureVersionRepository.save(publishedVersion);
    }

    @Test
    @DisplayName("Should publish data structure with published version successfully")
    void shouldPublishDataStructureWithPublishedVersion() {
      DataStructure dataStructure =
          dataStructureRepository.findById(dataStructureWithPublishedVersionId).orElseThrow();
      assertThat(dataStructure.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructureWithPublishedVersionId);
      assertThat(output.getDataStructureStatus())
          .as("Status should be AVAILABLE after publishing")
          .isEqualTo(DataStructureStatus.AVAILABLE);

      DataStructure reloadedDataStructure =
          dataStructureRepository.findById(dataStructureWithPublishedVersionId).orElseThrow();
      assertThat(reloadedDataStructure.getDataStructureStatus())
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish data structure without published versions")
    void shouldFailToPublishDataStructureWithoutPublishedVersions() {
      // Create a data structure with only draft versions
      DataStructure dataStructure = new DataStructure();
      dataStructure.setName("No Published Versions");
      dataStructure.setDescription("Data structure without published versions");
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setCreatedFromDataSource(false);
      dataStructure = dataStructureRepository.save(dataStructure);
      UUID dataStructureWithoutPublishedId = dataStructure.getId();

      // Create a draft version
      DataStructureVersion draftVersion = new DataStructureVersion();
      draftVersion.setDataStructure(dataStructure);
      draftVersion.setVersion("1.0.0");
      draftVersion.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
      draftVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      draftVersion.setModelAtlasUri("http://modelatlas.example.com/models/draft");
      draftVersion.setModelName("Draft Model");
      dataStructureVersionRepository.save(draftVersion);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureWithoutPublishedId + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);

      DataStructure reloadedDataStructure =
          dataStructureRepository.findById(dataStructureWithoutPublishedId).orElseThrow();
      assertThat(reloadedDataStructure.getDataStructureStatus())
          .as("Status should remain DRAFT")
          .isEqualTo(DataStructureStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to publish already published data structure")
    void shouldFailToPublishAlreadyPublishedDataStructure() {
      // First publish
      restTemplate.exchange(
          ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
          HttpMethod.POST,
          new HttpEntity<>(createAuthHeaders()),
          getOutputTypeReference());

      // Try to publish again
      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when publishing non-existent data structure")
    void shouldReturn404WhenPublishingNonExistentDataStructure() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/publish",
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
              ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
              HttpMethod.POST,
              new HttpEntity<>(null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Unpublish DataStructure Tests")
  class UnpublishDataStructureTests {

    private UUID publishedDataStructureId;

    @BeforeEach
    void setupPublishedDataStructure() {
      // Clean up default test data
      cleanup();

      // Create a published data structure
      DataStructure dataStructure = new DataStructure();
      dataStructure.setName("Published Data Structure");
      dataStructure.setDescription("Data structure for unpublish testing");
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setCreatedFromDataSource(false);
      dataStructure = dataStructureRepository.save(dataStructure);
      publishedDataStructureId = dataStructure.getId();

      // Create a published version
      DataStructureVersion publishedVersion = new DataStructureVersion();
      publishedVersion.setDataStructure(dataStructure);
      publishedVersion.setVersion("1.0.0");
      publishedVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      publishedVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      publishedVersion.setModelAtlasUri("http://modelatlas.example.com/models/published");
      publishedVersion.setModelName("Published Model");
      dataStructureVersionRepository.save(publishedVersion);

      // Publish the data structure
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructure = dataStructureRepository.save(dataStructure);
      publishedDataStructureId = dataStructure.getId();

      DataStructure published =
          dataStructureRepository.findById(publishedDataStructureId).orElseThrow();
      assertThat(published.getDataStructureStatus()).isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should unpublish published data structure successfully")
    void shouldUnpublishPublishedDataStructureSuccessfully() {
      DataStructure dataStructure =
          dataStructureRepository.findById(publishedDataStructureId).orElseThrow();
      assertThat(dataStructure.getDataStructureStatus()).isEqualTo(DataStructureStatus.AVAILABLE);

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(publishedDataStructureId);
      assertThat(output.getDataStructureStatus())
          .as("Status should be DRAFT after unpublishing")
          .isEqualTo(DataStructureStatus.DRAFT);

      DataStructure reloadedDataStructure =
          dataStructureRepository.findById(publishedDataStructureId).orElseThrow();
      assertThat(reloadedDataStructure.getDataStructureStatus())
          .isEqualTo(DataStructureStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to unpublish already unpublished data structure")
    void shouldFailToUnpublishDraftDataStructure() {
      // Create a draft data structure
      DataStructure draftDataStructure = new DataStructure();
      draftDataStructure.setName("Draft Data Structure");
      draftDataStructure.setDescription("Already in draft status");
      draftDataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      draftDataStructure.setCreatedFromDataSource(false);
      draftDataStructure = dataStructureRepository.save(draftDataStructure);
      UUID draftDataStructureId = draftDataStructure.getId();

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + draftDataStructureId + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when unpublishing non-existent data structure")
    void shouldReturn404WhenUnpublishingNonExistentDataStructure() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/unpublish",
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
              ENDPOINT + "/" + publishedDataStructureId + "/unpublish",
              HttpMethod.POST,
              new HttpEntity<>(null),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Update Published Meta Tests")
  class UpdatePublishedMetaTests {

    private UUID publishedDataStructureId;

    @BeforeEach
    void setupPublishedDataStructure() {
      cleanup();

      DataStructure dataStructure = new DataStructure();
      dataStructure.setName("Published Data Structure");
      dataStructure.setDescription("Data structure for meta update testing");
      dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      dataStructure.setCreatedFromDataSource(false);
      dataStructure = dataStructureRepository.save(dataStructure);
      publishedDataStructureId = dataStructure.getId();

      DataStructureVersion publishedVersion = new DataStructureVersion();
      publishedVersion.setDataStructure(dataStructure);
      publishedVersion.setVersion("1.0.0");
      publishedVersion.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
      publishedVersion.setDataStructureVersionSource(DataStructureVersionSource.OWN);
      publishedVersion.setModelAtlasUri("http://modelatlas.example.com/models/published");
      publishedVersion.setModelName("Published Model");
      dataStructureVersionRepository.save(publishedVersion);

      // Publish the data structure via repository
      dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
      dataStructure = dataStructureRepository.save(dataStructure);
      publishedDataStructureId = dataStructure.getId();

      // Verify DataStructure is published
      DataStructure published =
          dataStructureRepository.findById(publishedDataStructureId).orElseThrow();
      assertThat(published.getDataStructureStatus()).isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should update metadata of published data structure successfully")
    void shouldUpdatePublishedMetaSuccessfully() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Updated Published Data Structure");
      input.setDescription("Updated description for published data structure");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response =
          restTemplate.exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(publishedDataStructureId);
      assertThat(output.getName())
          .as("Name should be updated")
          .isEqualTo("Updated Published Data Structure");
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo("Updated description for published data structure");
      assertThat(output.getCreatedFromDataSource())
          .as("CreatedFromDataSource should remain unchanged")
          .isFalse();
      assertThat(output.getDataStructureStatus())
          .as("Status should remain AVAILABLE")
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to update published meta for DRAFT data structure")
    void shouldFailToUpdatePublishedMetaForDraftDataStructure() {
      DataStructure draftDataStructure = new DataStructure();
      draftDataStructure.setName("Draft Data Structure");
      draftDataStructure.setDescription("Draft data structure");
      draftDataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
      draftDataStructure.setCreatedFromDataSource(false);
      draftDataStructure = dataStructureRepository.save(draftDataStructure);
      UUID draftDataStructureId = draftDataStructure.getId();

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Trying to update draft");
      input.setDescription("This should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + draftDataStructureId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when updating published meta for non-existent data structure")
    void shouldReturn404WhenUpdatingPublishedMetaForNonExistentDataStructure() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Non-existent");
      input.setDescription("Does not exist");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/published/meta",
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
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Updated without auth");
      input.setDescription("This should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to update published meta with blank name")
    void shouldFailToUpdatePublishedMetaWithBlankName() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("");
      input.setDescription("Blank name should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/published/meta",
              HttpMethod.PUT,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
