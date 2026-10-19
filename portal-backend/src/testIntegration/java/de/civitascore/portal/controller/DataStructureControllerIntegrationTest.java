package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSourceStatus;
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
import de.civitascore.portal.model.output.summary.DataStructureVersionUsageSummaryDTO;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

      ResponseEntity<DataStructureOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create data structure with blank description")
    void shouldFailToCreateDataStructureWithBlankDescription() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Missing description");
      input.setDescription("  ");

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
    @DisplayName("Should allow creating data structures with the same name")
    void shouldAllowDuplicateDataStructureName() {
      // Name uniqueness was removed (#1453) to close a create-oracle: read access on
      // DataStructures is scope-restricted but create access is global, so a uniqueness
      // violation leaked the existence of records the caller had no permission to read.
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Shared Data Structure Name");
      input.setDescription("First one");

      ResponseEntity<DataStructureOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      ResponseEntity<DataStructureOutputDTO> secondResponse = performCreate(input);

      assertThat(secondResponse.getStatusCode())
          .as("Duplicate name must be accepted to avoid create-oracle leak")
          .isEqualTo(HttpStatus.CREATED);
      assertThat(secondResponse.getBody().getId()).isNotEqualTo(firstResponse.getBody().getId());
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
              b -> b.name("Test Data Structure 1").description("First test data structure"));

      DataStructureVersion modelledVersion =
          portalData.attachModel(
              portalData.dataStructureVersion(
                  dataStructure,
                  b ->
                      b.version("1.0.0")
                          .description("Version 1 Description")
                          .dataStructureVersionStatus(DataStructureVersionStatus.DRAFT)
                          .modelName("Test Model v1")),
              portalData.dataStructureVersionModel("Test Model v1"));

      portalData.dataStructureVersion(
          dataStructure,
          b ->
              b.version("2.0.0")
                  .description("Version 2 Description")
                  .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                  .modelName("Test Model v2"));

      ResponseEntity<DataStructureOutputDTO> response = performGetById(dataStructure.getId());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructure.getId());
      assertThat(output.getName()).isEqualTo("Test Data Structure 1");
      assertThat(output.getDescription()).isEqualTo("First test data structure");
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
      assertThat(output.getDataStructureVersions())
          .filteredOn(version -> version.getId().equals(modelledVersion.getId()))
          .singleElement()
          .extracting(DataStructureVersionSummaryDTO::getModelUrn)
          .isNotNull()
          .isEqualTo(modelledVersion.getModelUrn());
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

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructureId, input);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataStructureOutputDTO output = response.getBody();
      assertThat(output.getId()).isEqualTo(dataStructureId);
      assertThat(output.getName()).isEqualTo("Updated Data Structure");
      assertThat(output.getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent data structure")
    void shouldReturn404WhenUpdatingNonExistent() {
      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Non-existent");
      input.setDescription("a description");

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

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructureId, input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to update data structure with blank description")
    void shouldFailToUpdateWithBlankDescription() {
      UUID dataStructureId = createTestEntity();

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Still has a name");
      input.setDescription("  ");

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructureId, input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should update a data structure whose version is released via PUT")
    void shouldUpdateDataStructureWithReleasedVersionViaPut() {
      DataStructure dataStructure = dataStructureWithReleasedVersion();

      DataStructureInputDTO input = new DataStructureInputDTO();
      input.setName("Renamed with a released version");
      input.setDescription("Changed description");

      ResponseEntity<DataStructureOutputDTO> response = performUpdate(dataStructure.getId(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("Renamed with a released version");
      assertThat(response.getBody().getDescription()).isEqualTo("Changed description");
    }

    @Test
    @DisplayName("Should patch the name of a data structure whose version is released")
    void shouldPatchNameOfDataStructureWithReleasedVersion() {
      DataStructure dataStructure = dataStructureWithReleasedVersion();

      ResponseEntity<DataStructureOutputDTO> response =
          performPatch(dataStructure.getId(), Map.of("name", "Patched with a released version"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("Patched with a released version");
      assertThat(response.getBody().getDescription()).isEqualTo(dataStructure.getDescription());
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
      createInput.setAssignments(Set.of(assignment1));

      ResponseEntity<DataStructureOutputDTO> createResponse = performCreate(createInput);
      UUID id = createResponse.getBody().getId();

      AssignmentScopedInputDTO assignment2 = new AssignmentScopedInputDTO();
      assignment2.setGroupId(groupId2);
      assignment2.setRoleId(roleId);

      DataStructureInputDTO updateInput = new DataStructureInputDTO();
      updateInput.setName(createInput.getName());
      updateInput.setDescription("Updated");
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

    @Test
    @DisplayName(
        "Should replace assignments of a data structure whose version is released via PATCH")
    void shouldReplaceAssignmentsOfDataStructureWithReleasedVersionViaPatch() {
      UUID groupId1 = createTestGroup();
      UUID groupId2 = createTestGroup();
      UUID roleId = createTestRole();

      AssignmentScopedInputDTO assignment1 = new AssignmentScopedInputDTO();
      assignment1.setGroupId(groupId1);
      assignment1.setRoleId(roleId);

      DataStructureInputDTO createInput = createValidInput();
      createInput.setAssignments(Set.of(assignment1));
      UUID id = performCreate(createInput).getBody().getId();

      portalData.dataStructureVersion(
          dataStructureRepository.findById(id).orElseThrow(),
          b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      AssignmentScopedInputDTO assignment2 = new AssignmentScopedInputDTO();
      assignment2.setGroupId(groupId2);
      assignment2.setRoleId(roleId);

      ResponseEntity<DataStructureOutputDTO> response =
          performPatch(id, Map.of("assignments", Set.of(assignment2)));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

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
                      .description("Data structure with a version referenced by a DataSource"));
      inUseDataStructureId = ds.getId();

      DataStructureVersion version =
          portalData.dataStructureVersion(
              ds,
              b ->
                  b.version("1.0.0")
                      .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                      .modelName("InUse Model"));

      portalData.dataSource(
          b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE).dataStructureVersion(version));
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
      assertThat(response.getBody().isInUseByReleased())
          .as("inUseByReleased should be true when an AVAILABLE DataSource pins a version")
          .isTrue();
    }

    @Test
    @DisplayName("Should report inUse and inUseByReleased on each version row")
    void shouldReportUsageOnEachVersionRow() {
      DataStructure ds = portalData.dataStructure();
      DataStructureVersion releasedUse =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      DataStructureVersion draftUse =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      DataStructureVersion unused =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.DRAFT));
      portalData.dataSource(
          b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE).dataStructureVersion(releasedUse));
      portalData.dataSource(
          b -> b.dataSourceStatus(DataSourceStatus.DRAFT).dataStructureVersion(draftUse));

      ResponseEntity<DataStructureOutputDTO> response = performGetById(ds.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getDataStructureVersions())
          .extracting(
              DataStructureVersionUsageSummaryDTO::getId,
              DataStructureVersionUsageSummaryDTO::getDataStructureId,
              DataStructureVersionUsageSummaryDTO::isInUse,
              DataStructureVersionUsageSummaryDTO::isInUseByReleased)
          .containsExactlyInAnyOrder(
              tuple(releasedUse.getId(), ds.getId(), true, true),
              tuple(draftUse.getId(), ds.getId(), true, false),
              tuple(unused.getId(), ds.getId(), false, false));
    }

    @Test
    @DisplayName("Should return inUse=false when no DataSource references any version")
    void shouldReturnInUseFalseWhenNoDataSourceReferences() {
      DataStructure ds =
          portalData.dataStructure(
              b -> b.name("Not InUse Data Structure").description("No DataSources reference this"));

      ResponseEntity<DataStructureOutputDTO> response = performGetById(ds.getId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse())
          .as("inUse should be false when no DataSource references any version")
          .isFalse();
    }
  }

  @Nested
  @DisplayName("No Lifecycle Tests")
  class NoLifecycleTests {

    @ParameterizedTest
    @CsvSource({"POST, /release", "POST, /unrelease", "PATCH, /released/meta"})
    @DisplayName("Should answer 404 for the removed lifecycle endpoints")
    void shouldAnswer404ForRemovedLifecycleEndpoints(String method, String suffix) {
      UUID id = dataStructureWithReleasedVersion().getId();

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + id + suffix,
              HttpMethod.valueOf(method),
              new HttpEntity<>(Map.of(), createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should answer without a status of the data structure")
    void shouldAnswerWithoutStatus() {
      UUID id = dataStructureWithReleasedVersion().getId();

      ResponseEntity<String> response =
          restTemplate.exchange(
              ENDPOINT + "/" + id,
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody())
          .doesNotContain("dataStructureStatus")
          .contains("dataStructureVersionStatus");
    }

    @Test
    @DisplayName("Should ignore a status sent in the body of a PATCH")
    void shouldIgnoreStatusInPatchBody() {
      UUID id = createTestEntity();

      ResponseEntity<DataStructureOutputDTO> response =
          performPatch(id, Map.of("name", "Patched", "dataStructureStatus", "AVAILABLE"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getName()).isEqualTo("Patched");
    }

    @Test
    @DisplayName("Should not filter the list by the removed status parameter")
    void shouldNotFilterByRemovedStatusParameter() {
      UUID id = createTestEntity();

      ResponseEntity<RestPage<DataStructureOutputDTO>> response =
          performGetAll(Map.of("dataStructureStatus", "AVAILABLE"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent())
          .extracting(DataStructureOutputDTO::getId)
          .contains(id);
    }
  }

  private DataStructure dataStructureWithReleasedVersion() {
    DataStructure dataStructure = portalData.dataStructure();
    portalData.dataStructureVersion(
        dataStructure,
        b ->
            b.version("1.0.0")
                .dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)
                .modelName("Released Model"));
    return dataStructure;
  }
}
