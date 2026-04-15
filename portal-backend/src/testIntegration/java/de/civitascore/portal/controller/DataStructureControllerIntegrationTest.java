package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.RestPage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.http.ResponseEntity;

@Slf4j
@DisplayName("DataStructure Controller Integration Tests")
class DataStructureControllerIntegrationTest
    extends BaseDataEntityControllerIntegrationTest<DataStructureInputDTO, DataStructureOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;

  @Autowired private DataStructureRepository dataStructureRepository;

  private static final String ENDPOINT = "/datastructures";

  @Override
  protected String getEndpointPath() {
    return ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  @Override
  protected DataStructureInputDTO createValidInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setName("test_datastructure_" + System.currentTimeMillis());
    input.setDescription("A test data structure for integration testing");
    input.setCreatedFromDataSource(false);
    return input;
  }

  @Override
  protected DataStructureInputDTO createInvalidInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setDescription("Invalid data structure without required fields");
    return input;
  }

  @Override
  protected DataStructureInputDTO createUpdateInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setName("Updated Data Structure");
    input.setDescription("Updated description");
    input.setCreatedFromDataSource(false);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataStructureOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataStructureOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataStructureOutputDTO output) {
    return output.getId();
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

      ResponseEntity<DataStructureOutputDTO> response = performCreate(input);

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

      ResponseEntity<DataStructureOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create data structure without authentication")
    void shouldFailToCreateDataStructureWithoutAuth() {
      ResponseEntity<String> response = performRequestWithoutAuth("", HttpMethod.POST);

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

      ResponseEntity<DataStructureOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      ResponseEntity<DataStructureOutputDTO> secondResponse = performCreate(input);

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
      // Create data structure with relationships
      DataStructure dataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Test Data Structure 1")
                      .description("First test data structure")
                      .dataStructureStatus(DataStructureStatus.DRAFT));

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .description("Version 1 Description")
                  .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
                  .modelAtlasUri("http://modelatlas.example.com/models/1")
                  .modelName("Test Model v1")
                  .styles(Map.of("color", "blue", "size", "large")));

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("2.0.0")
                  .description("Version 2 Description")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelAtlasUri("http://modelatlas.example.com/models/2")
                  .modelName("Test Model v2")
                  .styles(Map.of("color", "red", "size", "medium")));

      ResponseEntity<DataStructureOutputDTO> response = performGetById(dataStructure.getId());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructure.getId());
      assertThat(output.getName()).isEqualTo("Test Data Structure 1");
      assertThat(output.getDescription()).isEqualTo("First test data structure");
      assertThat(output.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);

      assertThat(output.getDataStructureVersions())
          .as("Should have dataStructureVersions field")
          .isNotNull();
      assertThat(output.getDataStructureVersions())
          .as("Should have 2 data structure versions")
          .hasSize(2);

      DataStructureVersionSummaryDTO version1 = output.getDataStructureVersions().getFirst();
      assertThat(version1.getId()).as("Version should have an ID").isNotNull();
      assertThat(version1.getVersion()).as("Version should have a version string").isNotNull();
      assertThat(version1.getDescription())
          .as("Version should have a description string")
          .isNotNull();
      assertThat(version1.getDataStructureVersionStatus())
          .as("Version should have a status")
          .isNotNull();
      assertThat(version1.getDataStructureVersionSource())
          .as("Version should have a source")
          .isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent data structure")
    void shouldReturn404ForNonExistentDataStructure() {
      ResponseEntity<DataStructureOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should retrieve all data structures")
    void shouldRetrieveAllDataStructures() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<DataStructureOutputDTO>> response = performGetAll();

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
      DataStructureInputDTO input = createValidInput();
      input.setName("Searchable Structure");
      performCreate(input);

      ResponseEntity<RestPage<DataStructureOutputDTO>> response =
          performGetAll(Map.of("name", "Searchable"));

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
      UUID dataStructureId = createTestEntity();

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Updated Data Structure");
      input.setDescription("Updated description");
      input.setCreatedFromDataSource(true);

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructureId, input);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructureId);
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

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(UUID.randomUUID(), input);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update data structure with blank name")
    void shouldFailToUpdateWithBlankName() {
      UUID dataStructureId = createTestEntity();

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("");
      input.setDescription("Empty name");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructureId, input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to update published data structure with regular PUT endpoint")
    void shouldFailToUpdatePublishedDataStructureWithRegularPut() {
      DataStructure dataStructure =
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.DRAFT));

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelAtlasUri("http://modelatlas.example.com/models/published")
                  .modelName("Published Model"));

      // Publish via API
      restTemplate.exchange(
          ENDPOINT + "/" + dataStructure.getId() + "/publish",
          HttpMethod.POST,
          new HttpEntity<>(createAuthHeaders()),
          getOutputTypeReference());

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Trying to update published with regular PUT");
      input.setDescription("This should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructure.getId(), input);

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
      UUID dataStructureId = createTestEntity();

      ResponseEntity<Void> response = performDelete(dataStructureId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<DataStructureOutputDTO> getResponse = performGetById(dataStructureId);
      assertThat(getResponse.getStatusCode())
          .as("Should return NOT_FOUND after deletion")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent data structure")
    void shouldReturn404WhenDeletingNonExistent() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

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
      performAdditionalCleanup();

      DataStructure dataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Publishable Data Structure")
                      .description("Data structure with published version")
                      .dataStructureStatus(DataStructureStatus.DRAFT));
      dataStructureWithPublishedVersionId = dataStructure.getId();

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelAtlasUri("http://modelatlas.example.com/models/published")
                  .modelName("Published Model"));
    }

    @Test
    @DisplayName("Should publish data structure with published version successfully")
    void shouldPublishDataStructureWithPublishedVersion() {
      DataStructure dataStructure =
          dataStructureRepository.findById(dataStructureWithPublishedVersionId).orElseThrow();
      assertThat(dataStructure.getDataStructureStatus()).isEqualTo(DataStructureStatus.DRAFT);

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructureWithPublishedVersionId);
      assertThat(output.getDataStructureStatus())
          .as("Status should be AVAILABLE after publishing")
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to publish data structure without published versions")
    void shouldFailToPublishDataStructureWithoutPublishedVersions() {
      DataStructure dataStructure =
          portalData.dataStructure(
              b ->
                  b.name("No Published Versions")
                      .description("Data structure without published versions")
                      .dataStructureStatus(DataStructureStatus.DRAFT));

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
                  .modelAtlasUri("http://modelatlas.example.com/models/draft")
                  .modelName("Draft Model"));

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + dataStructure.getId() + "/publish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to publish already published data structure")
    void shouldFailToPublishAlreadyPublishedDataStructure() {
      // First publish
      exchange(
          ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
          HttpMethod.POST,
          createAuthHeaders(),
          null,
          getOutputTypeReference());

      // Try to publish again
      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + dataStructureWithPublishedVersionId + "/publish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when publishing non-existent data structure")
    void shouldReturn404WhenPublishingNonExistentDataStructure() {
      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/publish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to publish without authentication")
    void shouldFailToPublishWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth(
              "/" + dataStructureWithPublishedVersionId + "/publish", HttpMethod.POST);

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
      performAdditionalCleanup();

      DataStructure dataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Published Data Structure")
                      .description("Data structure for unpublish testing")
                      .dataStructureStatus(DataStructureStatus.AVAILABLE));
      publishedDataStructureId = dataStructure.getId();

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelAtlasUri("http://modelatlas.example.com/models/published")
                  .modelName("Published Model"));
    }

    @Test
    @DisplayName("Should unpublish published data structure successfully")
    void shouldUnpublishPublishedDataStructureSuccessfully() {
      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/unpublish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(publishedDataStructureId);
      assertThat(output.getDataStructureStatus())
          .as("Status should be DRAFT after unpublishing")
          .isEqualTo(DataStructureStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to unpublish already unpublished data structure")
    void shouldFailToUnpublishDraftDataStructure() {
      DataStructure draftDataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Draft Data Structure")
                      .description("Already in draft status")
                      .dataStructureStatus(DataStructureStatus.DRAFT));

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + draftDataStructure.getId() + "/unpublish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 404 when unpublishing non-existent data structure")
    void shouldReturn404WhenUnpublishingNonExistentDataStructure() {
      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/unpublish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to unpublish without authentication")
    void shouldFailToUnpublishWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + publishedDataStructureId + "/unpublish", HttpMethod.POST);

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
      performAdditionalCleanup();

      DataStructure dataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Published Data Structure")
                      .description("Data structure for meta update testing")
                      .dataStructureStatus(DataStructureStatus.AVAILABLE));
      publishedDataStructureId = dataStructure.getId();

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("1.0.0")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelAtlasUri("http://modelatlas.example.com/models/published")
                  .modelName("Published Model"));
    }

    @Test
    @DisplayName("Should update metadata of published data structure successfully")
    void shouldUpdatePublishedMetaSuccessfully() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Updated Published Data Structure");
      input.setDescription("Updated description for published data structure");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              input,
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
      assertThat(output.getDataStructureStatus())
          .as("Status should remain AVAILABLE")
          .isEqualTo(DataStructureStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should fail to update published meta for DRAFT data structure")
    void shouldFailToUpdatePublishedMetaForDraftDataStructure() {
      DataStructure draftDataStructure =
          portalData.dataStructure(
              b ->
                  b.name("Draft Data Structure")
                      .description("Draft data structure")
                      .dataStructureStatus(DataStructureStatus.DRAFT));

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Trying to update draft");
      input.setDescription("This should fail");
      input.setCreatedFromDataSource(false);

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + draftDataStructure.getId() + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              input,
              getOutputTypeReference());

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

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              input,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update published meta without authentication")
    void shouldFailToUpdatePublishedMetaWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth(
              "/" + publishedDataStructureId + "/published/meta", HttpMethod.PUT);

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

      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + publishedDataStructureId + "/published/meta",
              HttpMethod.PUT,
              createAuthHeaders(),
              input,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Assignment Tests")
  class AssignmentTests {

    private UUID createTestGroup() {
      Group group = portalData.group(b -> b.description("Test group for assignments"));
      return group.getId();
    }

    private UUID createTestRole() {
      Role role =
          portalData.role(b -> b.description("Test role for assignments").roleType(RoleType.DATA));
      return role.getId();
    }

    @Test
    @DisplayName("Should return empty list when no assignments exist for data structure")
    void shouldReturnEmptyAssignmentsList() {
      UUID dataStructureId = createTestEntity();

      ResponseEntity<List<AssignmentOutputDTO>> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should return 404 when data structure does not exist")
    void shouldReturn404ForNonExistentDataStructure() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + UUID.randomUUID() + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticated() {
      UUID dataStructureId = createTestEntity();

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + dataStructureId + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(new HttpHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create data structure with assignments")
    void shouldCreateDataStructureWithAssignments() {
      AssignmentScopedInputDTO assignment = new AssignmentScopedInputDTO();
      assignment.setGroupId(createTestGroup());
      assignment.setRoleId(createTestRole());

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Data Structure With Assignments");
      input.setDescription("Test");
      input.setCreatedFromDataSource(false);
      input.setAssignments(Set.of(assignment));

      ResponseEntity<DataStructureOutputDTO> createResponse = performCreate(input);

      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID id = createResponse.getBody().getId();

      ResponseEntity<List<AssignmentOutputDTO>> assignmentsResponse =
          restTemplate.exchange(
              ENDPOINT + "/" + id + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(assignmentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(assignmentsResponse.getBody()).hasSize(1);
      assertThat(assignmentsResponse.getBody().getFirst().getScopeType())
          .isEqualTo(ScopeType.DATASTRUCTURE);
    }

    @Test
    @DisplayName("Should replace assignments when updating data structure")
    void shouldReplaceAssignmentsOnUpdate() {
      UUID groupId1 = createTestGroup();
      UUID groupId2 = createTestGroup();
      UUID roleId = createTestRole();

      AssignmentScopedInputDTO assignment1 = new AssignmentScopedInputDTO();
      assignment1.setGroupId(groupId1);
      assignment1.setRoleId(roleId);

      DataStructureInputDTO createInput = new DataStructureInputDTO();
      createInput.setName("Data Structure For Update " + System.currentTimeMillis());
      createInput.setDescription("Test");
      createInput.setCreatedFromDataSource(false);
      createInput.setAssignments(Set.of(assignment1));

      ResponseEntity<DataStructureOutputDTO> createResponse = performCreate(createInput);
      UUID id = createResponse.getBody().getId();

      AssignmentScopedInputDTO assignment2 = new AssignmentScopedInputDTO();
      assignment2.setGroupId(groupId2);
      assignment2.setRoleId(roleId);

      DataStructureInputDTO updateInput = new DataStructureInputDTO();
      updateInput.setName(createInput.getName());
      updateInput.setDescription("Updated");
      updateInput.setCreatedFromDataSource(false);
      updateInput.setAssignments(Set.of(assignment2));

      performUpdate(id, updateInput);

      ResponseEntity<List<AssignmentOutputDTO>> assignmentsResponse =
          restTemplate.exchange(
              ENDPOINT + "/" + id + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(assignmentsResponse.getBody()).hasSize(1);
      assertThat(assignmentsResponse.getBody().getFirst().getGroup().getId()).isEqualTo(groupId2);
    }
  }

  @Nested
  @DisplayName("InUse Guard Tests")
  class InUseGuardTests {

    private UUID inUseDataStructureId;

    @BeforeEach
    void setupInUseDataStructure() {
      performAdditionalCleanup();

      DataStructure ds =
          portalData.dataStructure(
              b ->
                  b.name("InUse Data Structure")
                      .description("Data structure with a version referenced by a DataSource")
                      .dataStructureStatus(DataStructureStatus.AVAILABLE));
      inUseDataStructureId = ds.getId();

      DataStructureVersion version =
          portalData.dataStructureVersion(
              ds,
              b ->
                  b.version("1.0.0")
                      .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                      .modelAtlasUri("http://modelatlas.example.com/models/inuse")
                      .modelName("InUse Model"));

      portalData.dataSource(
          b -> b.dataSourceStatus(DataSourceStatus.DRAFT).dataStructureVersion(version));
    }

    @Test
    @DisplayName("Should return 409 when unpublishing in-use data structure")
    void shouldReturn409WhenUnpublishingInUseDataStructure() {
      ResponseEntity<DataStructureOutputDTO> response =
          exchange(
              ENDPOINT + "/" + inUseDataStructureId + "/unpublish",
              HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return 409 when deleting in-use data structure")
    void shouldReturn409WhenDeletingInUseDataStructure() {
      ResponseEntity<Void> response = performDelete(inUseDataStructureId);

      assertThat(response.getStatusCode())
          .as("Should return CONFLICT status")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return inUse=true in output DTO when DataSource references a version")
    void shouldReturnInUseTrueInOutputDTO() {
      ResponseEntity<DataStructureOutputDTO> response = performGetById(inUseDataStructureId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be true when a DataSource references a version")
          .isTrue();
    }

    @Test
    @DisplayName("Should return inUse=false when no DataSource references any version")
    void shouldReturnInUseFalseWhenNoDataSourceReferences() {
      DataStructure ds =
          portalData.dataStructure(
              b ->
                  b.name("Not InUse Data Structure")
                      .description("No DataSources reference this")
                      .dataStructureStatus(DataStructureStatus.DRAFT));

      ResponseEntity<DataStructureOutputDTO> response = performGetById(ds.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be false when no DataSource references any version")
          .isFalse();
    }
  }
}
