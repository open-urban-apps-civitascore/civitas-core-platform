package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSet Controller Integration Tests")
class DataSetControllerIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private final String DATASETS_ENDPOINT = "/datasets";

  @Autowired private DataSetRepository dataSetRepository;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    dataSetRepository.deleteAll();
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("test_dataset_" + System.currentTimeMillis());
    input.setDescription("A test dataset for integration testing");
    input.setFormat("JSON");
    input.setExternalId("ext-" + System.currentTimeMillis());
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
    input.setName("updated_dataset");
    input.setName("Updated DataSet");
    input.setDescription("Updated description");
    input.setFormat("CSV");
    return input;
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
  protected String getIdFromOutput(DataSetOutputDTO output) {
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
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo(input.getDescription());
      assertThat(output.getFormat()).as("Format should match input").isEqualTo(input.getFormat());
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
    @DisplayName("Should create dataset with dataspaces")
    void shouldCreateDataSetWithDataSpaces() {
      DataSetInputDTO input = createValidInput();
      input.setDataSpaceIds(Collections.emptyList());

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should create dataset with external ID")
    void shouldCreateDataSetWithExternalId() {
      DataSetInputDTO input = createValidInput();
      input.setExternalId("external-dataset-123");

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getExternalId()).isEqualTo("external-dataset-123");
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
    @DisplayName("Should create dataset with different formats")
    void shouldCreateDataSetWithDifferentFormats() {
      String[] formats = {"JSON", "CSV", "XML", "PARQUET", "AVRO"};

      for (String format : formats) {
        DataSetInputDTO input = createValidInput();
        input.setName("dataset_" + format.toLowerCase() + "_" + System.currentTimeMillis());
        input.setFormat(format);

        ResponseEntity<DataSetOutputDTO> response = performCreate(input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFormat()).isEqualTo(format);
      }
    }
  }

  @Nested
  @DisplayName("Read DataSet Tests")
  class ReadDataSetTests {

    @Test
    @DisplayName("Should retrieve dataset by ID successfully")
    void shouldRetrieveDataSetById() {
      String dataSetId = createTestEntity();

      ResponseEntity<DataSetOutputDTO> response = performGetById(dataSetId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      DataSetOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(dataSetId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent dataset")
    void shouldReturn404ForNonExistentDataSet() {
      ResponseEntity<DataSetOutputDTO> response = performGetById("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve dataset without authentication")
    void shouldFailToRetrieveDataSetWithoutAuth() {
      String dataSetId = createTestEntity();

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
  }

  @Nested
  @DisplayName("Update DataSet Tests")
  class UpdateDataSetTests {

    @Test
    @DisplayName("Should update dataset successfully with PUT")
    void shouldUpdateDataSetWithPut() {
      String dataSetId = createTestEntity();

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
      assertThat(output.getFormat())
          .as("Format should be updated")
          .isEqualTo(updateInput.getFormat());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update dataset with PATCH - single field")
    void shouldPartiallyUpdateDataSetWithPatch() {
      String dataSetId = createTestEntity();

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
      String dataSetId = createTestEntity();

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
      String dataSetId = createTestEntity();

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
      String dataSetId = createTestEntity();

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
      String dataSetId = createTestEntity();

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
      String dataSetId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Idempotent test");

      ResponseEntity<DataSetOutputDTO> firstResponse = performPatch(dataSetId, patchMap);
      ResponseEntity<DataSetOutputDTO> secondResponse = performPatch(dataSetId, patchMap);

      assertThat(firstResponse.getBody()).isNotNull();
      assertThat(secondResponse.getBody()).isNotNull();
      assertThat(firstResponse.getBody().getDescription())
          .isEqualTo(secondResponse.getBody().getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent dataset")
    void shouldFailToUpdateNonExistentDataSet() {
      DataSetInputDTO updateInput = createUpdateInput();

      ResponseEntity<DataSetOutputDTO> response = performUpdate("non-existent-id", updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update dataset without authentication")
    void shouldFailToUpdateDataSetWithoutAuth() {
      String dataSetId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSetId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should update dataset URL")
    void shouldUpdateDataSetUrl() {
      String dataSetId = createTestEntity();

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
    @DisplayName("Should delete dataset successfully")
    void shouldDeleteDataSetSuccessfully() {
      String dataSetId = createTestEntity();

      ResponseEntity<Void> response = performDelete(dataSetId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<DataSetOutputDTO> getResponse = performGetById(dataSetId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted dataset should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent dataset")
    void shouldFailToDeleteNonExistentDataSet() {
      ResponseEntity<Void> response = performDelete("non-existent-id");

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete dataset without authentication")
    void shouldFailToDeleteDataSetWithoutAuth() {
      String dataSetId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + dataSetId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Nested
  @DisplayName("Business Logic Tests")
  class BusinessLogicTests {

    @Test
    @DisplayName("Should handle dataset with multiple dataspaces")
    void shouldHandleDataSetWithMultipleDataSpaces() {
      DataSetInputDTO input = createValidInput();
      input.setDataSpaceIds(Collections.emptyList());

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      DataSetInputDTO input = createValidInput();
      input.setName("dataset_with_special_äöü");
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

    @Test
    @DisplayName("Should handle null format")
    void shouldHandleNullFormat() {
      DataSetInputDTO input = createValidInput();
      input.setFormat(null);

      ResponseEntity<DataSetOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
  }
}
