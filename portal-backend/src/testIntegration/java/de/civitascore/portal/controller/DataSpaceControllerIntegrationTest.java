package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import de.civitascore.portal.util.RestPage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test-integration", "preview"})
@DisplayName("DataSpace Controller Integration Tests")
class DataSpaceControllerIntegrationTest
    extends BaseControllerIntegrationTest<DataSpaceInputDTO, DataSpaceOutputDTO> {

  private final String DATASPACES_ENDPOINT = "/dataspaces";

  @Autowired protected PortalTestDataFactory portalData;

  @Override
  protected String getEndpointPath() {
    return DATASPACES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  @Override
  protected DataSpaceInputDTO createValidInput() {
    DataSpaceInputDTO input = new DataSpaceInputDTO();
    input.setName("test_dataspace_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test dataspace for integration testing");
    input.setExternalId("ext-" + System.currentTimeMillis());
    return input;
  }

  @Override
  protected DataSpaceInputDTO createInvalidInput() {
    DataSpaceInputDTO input = new DataSpaceInputDTO();
    input.setDescription("Invalid dataspace without required fields");
    return input;
  }

  @Override
  protected DataSpaceInputDTO createUpdateInput() {
    DataSpaceInputDTO input = new DataSpaceInputDTO();
    input.setName("Updated DataSpace");
    input.setDescription("Updated description");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataSpaceOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSpaceOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSpaceOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create DataSpace Tests")
  class CreateDataSpaceTests {

    @Test
    @DisplayName("Should create dataspace successfully with valid data")
    void shouldCreateDataSpaceSuccessfully() {
      DataSpaceInputDTO input = createValidInput();

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSpaceOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo(input.getDescription());
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should fail to create dataspace with missing required fields")
    void shouldFailToCreateDataSpaceWithMissingFields() {
      DataSpaceInputDTO input = createInvalidInput();

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create dataspace without authentication")
    void shouldFailToCreateDataSpaceWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should create dataspace with parent dataspace")
    void shouldCreateDataSpaceWithParent() {
      // Create parent dataspace
      UUID parentId = createTestEntity();

      // Create child dataspace
      DataSpaceInputDTO childInput = createValidInput();
      childInput.setName("Child DataSpace");
      childInput.setParentDataSpaceId(parentId);

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(childInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should create dataspace with external ID")
    void shouldCreateDataSpaceWithExternalId() {
      DataSpaceInputDTO input = createValidInput();
      input.setExternalId("external-dataspace-123");

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getExternalId()).isEqualTo("external-dataspace-123");
    }

    @Test
    @DisplayName("Should create dataspace with metadata")
    void shouldCreateDataSpaceWithMetadata() {
      DataSpaceInputDTO input = createValidInput();
      input.setName("TestDataSpace");

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(input.getName());
    }
  }

  @Nested
  @DisplayName("Read DataSpace Tests")
  class ReadDataSpaceTests {

    @Test
    @DisplayName("Should retrieve dataspace by ID successfully")
    void shouldRetrieveDataSpaceById() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<DataSpaceOutputDTO> response = performGetById(dataSpaceId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSpaceOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(dataSpaceId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent dataspace")
    void shouldReturn404ForNonExistentDataSpace() {
      ResponseEntity<DataSpaceOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve dataspace without authentication")
    void shouldFailToRetrieveDataSpaceWithoutAuth() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSpaceId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all dataspaces with pagination")
    void shouldRetrieveAllDataSpacesWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<DataSpaceOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<DataSpaceOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain dataspaces").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve dataspaces with pagination parameters")
    void shouldRetrieveDataSpacesWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<DataSpaceOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update DataSpace Tests")
  class UpdateDataSpaceTests {

    @Test
    @DisplayName("Should update dataspace successfully with PUT")
    void shouldUpdateDataSpaceWithPut() {
      UUID dataSpaceId = createTestEntity();

      DataSpaceInputDTO updateInput = createUpdateInput();
      ResponseEntity<DataSpaceOutputDTO> response = performUpdate(dataSpaceId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSpaceOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(dataSpaceId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update dataspace with PATCH - single field")
    void shouldPartiallyUpdateDataSpaceWithPatch() {
      UUID dataSpaceId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(dataSpaceId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      UUID dataSpaceId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedDataSpace");
      patchMap.put("description", "Patched description");

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(dataSpaceId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedDataSpace");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      UUID dataSpaceId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(dataSpaceId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<DataSpaceOutputDTO> initialResponse = performGetById(dataSpaceId);
      DataSpaceOutputDTO initialDataSpace = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(dataSpaceId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialDataSpace.getName());
    }

    @Test
    @DisplayName("Should set parentDataSpace to null with PATCH")
    void shouldSetParentDataSpaceToNullWithPatch() {
      UUID parentDataSpaceId = createTestEntity();
      UUID childDataSpaceId = createTestEntity();

      // Set parent
      Map<String, Object> setParentMap = new HashMap<>();
      setParentMap.put("parentDataSpaceId", parentDataSpaceId);
      performPatch(childDataSpaceId, setParentMap);

      // Remove parent
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("parentDataSpaceId", null);

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(childDataSpaceId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getParentDataSpace())
          .as("ParentDataSpace should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<DataSpaceOutputDTO> initialResponse = performGetById(dataSpaceId);
      DataSpaceOutputDTO initialDataSpace = initialResponse.getBody();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<DataSpaceOutputDTO> response = performPatch(dataSpaceId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialDataSpace.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialDataSpace.getDescription());
    }

    @Test
    @DisplayName("Should be idempotent with PATCH")
    void shouldBeIdempotentWithPatch() {
      UUID dataSpaceId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<DataSpaceOutputDTO> firstResponse = performPatch(dataSpaceId, patchMap);
      ResponseEntity<DataSpaceOutputDTO> secondResponse = performPatch(dataSpaceId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent dataspace")
    void shouldFailToUpdateNonExistentDataSpace() {
      DataSpaceInputDTO updateInput = createUpdateInput();

      ResponseEntity<DataSpaceOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update dataspace without authentication")
    void shouldFailToUpdateDataSpaceWithoutAuth() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSpaceId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Delete DataSpace Tests")
  class DeleteDataSpaceTests {

    @Test
    @DisplayName("Should delete dataspace successfully")
    void shouldDeleteDataSpaceSuccessfully() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<Void> response = performDelete(dataSpaceId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<DataSpaceOutputDTO> getResponse = performGetById(dataSpaceId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted dataspace should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent dataspace")
    void shouldFailToDeleteNonExistentDataSpace() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete dataspace without authentication")
    void shouldFailToDeleteDataSpaceWithoutAuth() {
      UUID dataSpaceId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSpaceId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle dataspace hierarchy")
    void shouldHandleDataSpaceHierarchy() {
      // Create parent dataspace
      DataSpaceInputDTO parentInput = createValidInput();
      parentInput.setName("Parent DataSpace");
      ResponseEntity<DataSpaceOutputDTO> parentResponse = performCreate(parentInput);
      assertThat(parentResponse.getBody()).isNotNull();
      UUID parentId = parentResponse.getBody().getId();

      // Create child dataspace
      DataSpaceInputDTO childInput = createValidInput();
      childInput.setName("Child DataSpace");
      childInput.setParentDataSpaceId(parentId);
      ResponseEntity<DataSpaceOutputDTO> childResponse = performCreate(childInput);

      assertThat(childResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(childResponse.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      DataSpaceInputDTO input = createValidInput();
      input.setName("DataSpace with special chars: äöü ß @#$%");

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      DataSpaceInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should handle empty name as invalid")
    void shouldHandleEmptyName() {
      DataSpaceInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<DataSpaceOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
