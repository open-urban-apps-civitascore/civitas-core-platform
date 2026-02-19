package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSet Controller Integration Tests")
class DataSetControllerIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private final String DATASETS_ENDPOINT = "/datasets";

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DataSourceRepository dataSourceRepository;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
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

  /** Helper method to create a sample model map for Pipeline. */
  private Map<String, Object> createSampleModel() {
    Map<String, Object> model = new HashMap<>();
    model.put("input", Map.of("type", "kafka"));
    return model;
  }

  @Override
  protected void performAdditionalCleanup() {
    pipelineRepository.deleteAll();
    distributionRepository.deleteAll();
    dataSetRepository.deleteAll();
    dataSpaceRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("test_dataset_" + System.currentTimeMillis());
    input.setDescription("A test dataset for integration testing");
    input.setOpenDataAccess(false);
    return input;
  }

  @Override
  protected DataSetInputDTO createInvalidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setDescription("Invalid dataset without required fields");
    input.setOpenDataAccess(false);
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
    dataSet.setPersistenceId(12345L);
    dataSet.setIdentifier("test-identifier-001");
    dataSet.setVersion("1.0.0");
    dataSet.setExternalId("ext-dataset-" + System.currentTimeMillis());
    dataSet.setFormat("JSON");
    dataSet.setOpenDataAccess(false);
    dataSet.setOwner(owner);
    dataSet = dataSetRepository.save(dataSet);

    // Create a DataSpace for the dataset
    DataSpace dataSpace = new DataSpace();
    dataSpace.setName("test_dataspace_" + System.currentTimeMillis());
    dataSpace.setDescription("Test data space for dataset");
    dataSpace = dataSpaceRepository.save(dataSpace);

    // Associate dataset with dataspace
    dataSet.getDataSpaces().add(dataSpace);
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
    pipeline1.setStyles(createSampleStyles());
    pipeline1.getDataSources().add(dataSource1);
    pipeline1.getDataSources().add(dataSource2);
    pipeline1.setApis(Collections.singletonList("/api/v1/traffic"));
    pipeline1.setPersistences(Collections.singletonList(12345L));
    pipeline1.setModel(createSampleModel());
    pipeline1 = pipelineRepository.save(pipeline1);

    Pipeline pipeline2 = new Pipeline();
    pipeline2.setName("test_pipeline_2_" + System.currentTimeMillis());
    pipeline2.setDescription("Test pipeline 2");
    pipeline2.setDataSet(dataSet);
    pipeline2.setStyles(createSampleStyles());
    pipeline2.getDataSources().add(dataSource3);
    pipeline2.getDataSources().add(dataSource4);
    pipeline2.setApis(Collections.singletonList("/api/v1/weather"));
    pipeline2.setPersistences(Collections.singletonList(12345L));
    pipeline2.setModel(createSampleModel());
    pipeline2 = pipelineRepository.save(pipeline2);

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

      assertThat(output.getDistributions())
          .as("Distributions should be included in the response")
          .isNotNull()
          .hasSize(2)
          .allMatch(distribution -> distribution.getId() != null)
          .allMatch(distribution -> distribution.getAccessUrl() != null)
          .extracting("accessUrl")
          .containsExactlyInAnyOrder(
              "http://localhost:8080/api/v1/traffic", "http://localhost:8080/api/v1/weather");

      assertThat(output.getDataSpaces())
          .as("DataSpaces should be included in the response")
          .isNotNull()
          .hasSize(1)
          .allMatch(dataSpace -> dataSpace.getId() != null)
          .allMatch(dataSpace -> dataSpace.getName() != null)
          .allMatch(dataSpace -> dataSpace.getName().startsWith("test_dataspace_"));

      assertThat(output.getOwner()).as("Owner should be included in the response").isNotNull();
      assertThat(output.getOwner().getId()).as("Owner ID should be set").isNotNull();
      assertThat(output.getOwner().getName()).as("Owner name should be set").isNotNull();

      assertThat(output.getDataSetStatus())
          .as("Status should be DRAFT")
          .isEqualTo(DataSetStatus.DRAFT);
      assertThat(output.getPersistenceId()).as("Persistence ID should be set").isEqualTo(12345L);
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
  }

  @Nested
  @DisplayName("Update DataSet Tests")
  class UpdateDataSetTests {

    @Test
    @DisplayName("Should update dataset successfully with PUT")
    void shouldUpdateDataSetWithPut() {
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

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"DRAFT"})
    @DisplayName("Should prevent updating persistenceId and pipelines after publishing")
    void shouldPreventUpdatingLockedFieldsAfterPublishing(DataSetStatus status) {
      // Create dataset with pipelines and distributions in DRAFT status
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(status);
      dataSet = dataSetRepository.save(dataSet);

      UUID dataSetId = dataSet.getId();
      Long originalPersistenceId = dataSet.getPersistenceId();

      // Get the original pipeline IDs
      List<UUID> originalPipelineIds =
          dataSet.getPipelines().stream().map(Pipeline::getId).toList();

      // Attempt to update persistenceId and pipelines
      DataSetInputDTO updateInput = new DataSetInputDTO();
      updateInput.setName(dataSet.getName());
      updateInput.setDescription("Updated description");
      updateInput.setOpenDataAccess(false);
      updateInput.setPersistenceId(99999L); // Try to change persistence ID
      updateInput.setPipelineIds(
          Collections.singletonList(
              dataSet.getPipelines().stream()
                  .findFirst()
                  .map(Pipeline::getId)
                  .orElseThrow())); // Omit the second pipeline

      ResponseEntity<DataSetOutputDTO> response = performUpdate(dataSetId, updateInput);

      assertThat(response.getStatusCode())
          .as("Update should succeed (only description changes)")
          .isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      DataSetOutputDTO output = response.getBody();

      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo("Updated description");

      assertThat(output.getPersistenceId())
          .as("PersistenceId should remain unchanged after publishing")
          .isEqualTo(originalPersistenceId);

      assertThat(output.getPipelines())
          .as("Pipelines should remain unchanged after publishing")
          .isNotNull()
          .hasSize(2)
          .extracting(PipelineSummaryDTO::getId)
          .containsExactlyInAnyOrderElementsOf(originalPipelineIds);

      assertThat(output.getDataSetStatus()).as("Status should remain unchanged").isEqualTo(status);
    }

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

    @ParameterizedTest
    @EnumSource(
        value = DataSetStatus.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"DRAFT"})
    @DisplayName("Should fail to delete dataset when not in DRAFT status")
    void shouldFailToDeleteNonDraftDataSet(DataSetStatus status) {
      DataSet dataSet = createDataSetWithRelationships();
      dataSet.setDataSetStatus(status);
      dataSet = dataSetRepository.save(dataSet);
      UUID dataSetId = dataSet.getId();

      ResponseEntity<Void> response = performDelete(dataSetId);

      assertThat(response.getStatusCode())
          .as("Should not allow deletion of %s dataset", status)
          .isEqualTo(HttpStatus.BAD_REQUEST);

      ResponseEntity<DataSetOutputDTO> getResponse = performGetById(dataSetId);
      assertThat(getResponse.getStatusCode())
          .as("Dataset should still exist after failed deletion attempt")
          .isEqualTo(HttpStatus.OK);

      long pipelineCount =
          pipelineRepository.findAll().stream()
              .filter(p -> p.getDataSet() != null && p.getDataSet().getId().equals(dataSetId))
              .count();
      assertThat(pipelineCount)
          .as("Pipelines should still exist after failed deletion attempt")
          .isEqualTo(2);

      long distributionCount =
          distributionRepository.findAll().stream()
              .filter(d -> d.getDataSet() != null && d.getDataSet().getId().equals(dataSetId))
              .count();
      assertThat(distributionCount)
          .as("Distributions should still exist after failed deletion attempt")
          .isEqualTo(2);
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
  }

  @Nested
  @DisplayName("Publish DataSet Tests")
  class PublishDataSetTests {

    @Test
    @DisplayName("Should publish dataset with pipelines successfully")
    void shouldPublishDataSetWithPipelinesSuccessfully() {
      DataSet dataSet = new DataSet();
      dataSet.setName("test_dataset_publish_" + System.currentTimeMillis());
      dataSet.setDescription("Test dataset with pipelines");
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);
      dataSet.setPersistenceId(12345L);
      dataSet.setIdentifier("test-identifier-publish");
      dataSet.setVersion("1.0.0");
      dataSet.setExternalId("ext-dataset-publish-" + System.currentTimeMillis());
      dataSet.setFormat("JSON");
      dataSet.setOpenDataAccess(false);
      dataSet = dataSetRepository.save(dataSet);

      Pipeline pipeline1 = new Pipeline();
      pipeline1.setName("test_pipeline_api1_" + System.currentTimeMillis());
      pipeline1.setDescription("Pipeline with API 1");
      pipeline1.setDataSet(dataSet);
      pipeline1.setStyles(createSampleStyles());
      pipeline1.setModel(createSampleModel());
      pipeline1.setApis(Arrays.asList("/api/v1/traffic", "/api/v1/sensors"));
      pipeline1.setPersistences(Collections.singletonList(12345L));
      pipelineRepository.save(pipeline1);

      Pipeline pipeline2 = new Pipeline();
      pipeline2.setName("test_pipeline_api2_" + System.currentTimeMillis());
      pipeline2.setDescription("Pipeline with API 2");
      pipeline2.setDataSet(dataSet);
      pipeline2.setStyles(createSampleStyles());
      pipeline2.setModel(createSampleModel());
      pipeline2.setApis(Collections.singletonList("/api/v1/weather"));
      pipeline2.setPersistences(Collections.singletonList(12345L));
      pipelineRepository.save(pipeline2);

      UUID dataSetId = dataSet.getId();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/publish",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSetStatus())
          .as("DataSet status should be READY after publishing")
          .isEqualTo(DataSetStatus.READY);

      long distributionCount =
          distributionRepository.findAll().stream()
              .filter(d -> d.getDataSet() != null && d.getDataSet().getId().equals(dataSetId))
              .count();

      assertThat(distributionCount)
          .as("Should create 3 distributions for 3 unique API paths")
          .isEqualTo(3);

      distributionRepository.findAll().stream()
          .filter(d -> d.getDataSet() != null && d.getDataSet().getId().equals(dataSetId))
          .forEach(
              distribution -> {
                assertThat(distribution.getAccessUrl())
                    .as("Access URL should be set with their respective API paths")
                    .startsWith("/api/v1/");
                assertThat(distribution.getApiType())
                    .as("API type should be SensorThings")
                    .isEqualTo("SensorThings");
                assertThat(distribution.getFormat())
                    .as("Format should be application/json")
                    .isEqualTo("application/json");
                assertThat(distribution.getAutoGenerated())
                    .as("Distribution should be marked as auto-generated")
                    .isTrue();
              });
    }

    @Test
    @DisplayName("Should fail to publish dataset without pipelines")
    void shouldFailToPublishDataSetWithoutPipelines() {
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
              getEndpointPath() + "/" + dataSetId + "/publish",
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
          .as("DataSet status should remain DRAFT after failed publish")
          .isEqualTo(DataSetStatus.DRAFT);
    }

    @Test
    @DisplayName("Should fail to publish non-existent dataset")
    void shouldFailToPublishNonExistentDataSet() {
      UUID nonExistentId = UUID.randomUUID();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + nonExistentId + "/publish",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should avoid duplicate distributions for same API path")
    void shouldAvoidDuplicateDistributions() {
      DataSet dataSet = new DataSet();
      dataSet.setName("test_dataset_duplicate_apis_" + System.currentTimeMillis());
      dataSet.setDescription("Test dataset with duplicate API paths");
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);
      dataSet.setPersistenceId(12345L);
      dataSet.setFormat("JSON");
      dataSet.setOpenDataAccess(false);
      dataSet = dataSetRepository.save(dataSet);

      Pipeline pipeline1 = new Pipeline();
      pipeline1.setName("test_pipeline_dup1_" + System.currentTimeMillis());
      pipeline1.setDescription("Pipeline 1 with duplicate API");
      pipeline1.setDataSet(dataSet);
      pipeline1.setStyles(createSampleStyles());
      pipeline1.setModel(createSampleModel());
      pipeline1.setApis(Arrays.asList("/api/v1/traffic", "/api/v1/weather"));
      pipeline1.setPersistences(Collections.singletonList(12345L));
      pipelineRepository.save(pipeline1);

      Pipeline pipeline2 = new Pipeline();
      pipeline2.setName("test_pipeline_dup2_" + System.currentTimeMillis());
      pipeline2.setDescription("Pipeline 2 with duplicate API");
      pipeline2.setDataSet(dataSet);
      pipeline2.setStyles(createSampleStyles());
      pipeline2.setModel(createSampleModel());
      pipeline2.setApis(Collections.singletonList("/api/v1/traffic")); // Same as in pipeline1
      pipeline2.setPersistences(Collections.singletonList(12345L));
      pipelineRepository.save(pipeline2);

      UUID dataSetId = dataSet.getId();

      ResponseEntity<DataSetOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + dataSetId + "/publish",
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      long distributionCount =
          distributionRepository.findAll().stream()
              .filter(d -> d.getDataSet() != null && d.getDataSet().getId().equals(dataSetId))
              .count();

      assertThat(distributionCount)
          .as("Should create only 2 distributions for 2 unique API paths (not 3)")
          .isEqualTo(2);
    }
  }
}
