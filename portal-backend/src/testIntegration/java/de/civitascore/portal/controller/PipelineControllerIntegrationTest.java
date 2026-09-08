package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.RestPage;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Pipeline Controller Integration Tests")
class PipelineControllerIntegrationTest
    extends DataSetSubEntityControllerIntegrationTest<PipelineInputDTO, PipelineOutputDTO> {

  @Override
  protected String getSubEntityPathSegment() {
    return "pipelines";
  }

  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSinkRepository dataSinkRepository;

  private UUID testDataSetId;

  private void ensureTestDataSet() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      testDataSetId = createTestDataSet().getId();
    }
  }

  @Override
  protected String getEndpointPath() {
    ensureTestDataSet();
    return "/datasets/" + testDataSetId + "/pipelines";
  }

  /** Helper method to create a sample styles map for Pipeline. */
  private Map<String, Object> createSampleStyles() {
    Map<String, Object> styles = new HashMap<>();
    styles.put("nodes", List.of(Map.of("id", "1", "type", "input")));
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
    model.put("input", Map.of("type", "kafka", "brokers", List.of("localhost:9092")));
    model.put("pipeline", List.of(Map.of("processor", "transform")));
    model.put("output", Map.of("type", "frost"));
    return model;
  }

  /** Create a test DataSet to associate pipelines with. */
  private DataSet createTestDataSet() {
    return portalData.dataSet(
        b ->
            b.description("Test dataset for pipeline integration tests")
                .dataSetStatus(DataSetStatus.DRAFT));
  }

  /** Create a test DataSource for pipeline associations. */
  private DataSource createTestDataSource() {
    return portalData.dataSource(b -> b.description("Test data source for pipelines"));
  }

  private DataSource createAvailableTestDataSource() {
    return portalData.dataSource(
        b ->
            b.description("Test data source for pipelines")
                .dataSourceStatus(DataSourceStatus.AVAILABLE));
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
    testDataSetId = null; // Reset for next test
  }

  @Override
  protected PipelineInputDTO createValidInput() {
    PipelineInputDTO input = new PipelineInputDTO();
    input.setName("test_pipeline_" + System.currentTimeMillis());
    input.setDescription("A test pipeline for integration testing");
    input.setStyles(createSampleStyles());
    input.setModel(createSampleModel());
    return input;
  }

  @Override
  protected PipelineInputDTO createInvalidInput() {
    PipelineInputDTO input = new PipelineInputDTO();
    // Missing required name field
    input.setDescription("Invalid pipeline without required fields");
    return input;
  }

  @Override
  protected PipelineInputDTO createUpdateInput() {
    PipelineInputDTO input = new PipelineInputDTO();
    input.setName("Updated Pipeline");
    input.setDescription("Updated description");
    input.setStyles(createSampleStyles());
    input.setModel(createSampleModel());
    return input;
  }

  @Override
  protected ParameterizedTypeReference<PipelineOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<PipelineOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(PipelineOutputDTO output) {
    return output.getId();
  }

  @Nested
  @DisplayName("Create Pipeline Tests")
  class CreatePipelineTests {

    @Test
    @DisplayName("Should create pipeline successfully with valid data")
    void shouldCreatePipelineSuccessfully() {
      PipelineInputDTO input = createValidInput();

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return CREATED status")
          .isEqualTo(HttpStatus.CREATED);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PipelineOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should be generated").isNotNull();
      assertThat(output.getName()).as("Name should match input").isEqualTo(input.getName());
      assertThat(output.getDescription())
          .as("Description should match input")
          .isEqualTo(input.getDescription());
      assertThat(output.getStyles()).as("Styles should match input").isEqualTo(input.getStyles());
      assertThat(output.getModel()).as("Model should match input").isEqualTo(input.getModel());
      assertThat(output.getDataSinkIds()).as("DataSink IDs should be empty").isEmpty();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should create pipeline with data sources")
    void shouldCreatePipelineWithDataSources() {
      DataSource ds1 = createAvailableTestDataSource();
      DataSource ds2 = createAvailableTestDataSource();

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(ds1.getId(), ds2.getId()));

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      // Verify data sources are associated in database using eager fetch
      UUID pipelineId = response.getBody().getId();
      Pipeline savedPipeline = pipelineRepository.findById(pipelineId).orElseThrow();

      assertThat(savedPipeline.getDataSources())
          .hasSize(2)
          .extracting(DataSource::getId)
          .containsExactlyInAnyOrder(ds1.getId(), ds2.getId());
    }

    @Test
    @DisplayName("Should fail to create pipeline with missing required fields")
    void shouldFailToCreatePipelineWithMissingFields() {
      PipelineInputDTO input = createInvalidInput();

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode())
          .as("Should return BAD_REQUEST status")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should fail to create pipeline without authentication")
    void shouldFailToCreatePipelineWithoutAuth() {
      ResponseEntity<String> response =
          performRequestWithoutAuth("", org.springframework.http.HttpMethod.POST);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to create pipeline with duplicate name in same dataset")
    void shouldFailToCreatePipelineWithDuplicateName() {
      PipelineInputDTO input = createValidInput();
      String duplicateName = "duplicate_pipeline_name";
      input.setName(duplicateName);

      // Create first pipeline
      ResponseEntity<PipelineOutputDTO> firstResponse = performCreate(input);
      assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      // Try to create second pipeline with same name
      PipelineInputDTO duplicateInput = createValidInput();
      duplicateInput.setName(duplicateName);

      ResponseEntity<PipelineOutputDTO> duplicateResponse = performCreate(duplicateInput);
      assertThat(duplicateResponse.getStatusCode())
          .as("Should return CONFLICT status for duplicate name")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should create pipeline with minimal required fields")
    void shouldCreatePipelineWithMinimalFields() {
      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("minimal_pipeline_" + System.currentTimeMillis());

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(input.getName());
      assertThat(response.getBody().getDescription()).isNull();
    }
  }

  @Nested
  @DisplayName("Read Pipeline Tests")
  class ReadPipelineTests {

    @Test
    @DisplayName("Should retrieve pipeline by ID successfully")
    void shouldRetrievePipelineById() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<PipelineOutputDTO> response = performGetById(pipelineId);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PipelineOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should match").isEqualTo(pipelineId);
      assertThat(output.getName()).as("Name should be present").isNotNull();
      assertThat(output.getCreatedAt()).as("Created timestamp should be set").isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existent pipeline")
    void shouldReturn404ForNonExistentPipeline() {
      ResponseEntity<PipelineOutputDTO> response = performGetById(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to retrieve pipeline without authentication")
    void shouldFailToRetrievePipelineWithoutAuth() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + pipelineId, org.springframework.http.HttpMethod.GET);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should retrieve all pipelines with pagination")
    void shouldRetrieveAllPipelinesWithPagination() {
      createTestEntity();
      createTestEntity();

      ResponseEntity<RestPage<PipelineOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      RestPage<PipelineOutputDTO> page = response.getBody();
      assertThat(page.getContent()).as("Should contain pipelines").isNotEmpty();
      assertThat(page.getTotalElements()).as("Total elements should be positive").isPositive();
    }

    @Test
    @DisplayName("Should retrieve pipelines with pagination parameters")
    void shouldRetrievePipelinesWithPaginationParams() {
      Map<String, String> params =
          Map.of(
              "page", "0",
              "size", "5",
              "sort", "name,asc");

      ResponseEntity<RestPage<PipelineOutputDTO>> response = performGetAll(params);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Update Pipeline Tests")
  class UpdatePipelineTests {

    @Test
    @DisplayName("Should update pipeline successfully with PUT")
    void shouldUpdatePipelineWithPut() {
      UUID pipelineId = createTestEntity();

      PipelineInputDTO updateInput = createUpdateInput();
      ResponseEntity<PipelineOutputDTO> response = performUpdate(pipelineId, updateInput);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

      assertThat(response.getBody()).as("Response body should not be null").isNotNull();

      PipelineOutputDTO output = response.getBody();
      assertThat(output.getId()).as("ID should remain the same").isEqualTo(pipelineId);
      assertThat(output.getName()).as("Name should be updated").isEqualTo(updateInput.getName());
      assertThat(output.getDescription())
          .as("Description should be updated")
          .isEqualTo(updateInput.getDescription());
      assertThat(output.getModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should partially update pipeline with PATCH - single field")
    void shouldPartiallyUpdatePipelineWithPatch() {
      UUID pipelineId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Only description updated");

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be updated")
          .isEqualTo("Only description updated");
    }

    @Test
    @DisplayName("Should update multiple fields with PATCH")
    void shouldUpdateMultipleFieldsWithPatch() {
      UUID pipelineId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", "PatchedPipeline");
      patchMap.put("description", "Patched description");

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("PatchedPipeline");
      assertThat(response.getBody().getDescription()).isEqualTo("Patched description");
    }

    @Test
    @DisplayName("Should update pipeline's model with PATCH")
    void shouldUpdateModelWithPatch() {
      UUID pipelineId = createTestEntity();

      Map<String, Object> newModel = new HashMap<>();
      newModel.put("input", Map.of("type", "mqtt"));
      newModel.put("output", Map.of("type", "postgres"));

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("model", newModel);

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel()).isEqualTo(newModel);
    }

    @Test
    @DisplayName("Should set description to null with PATCH")
    void shouldSetDescriptionToNullWithPatch() {
      UUID pipelineId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", null);

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription())
          .as("Description should be set to null")
          .isNull();
    }

    @Test
    @DisplayName("Should leave omitted fields unchanged with PATCH")
    void shouldLeaveOmittedFieldsUnchangedWithPatch() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<PipelineOutputDTO> initialResponse = performGetById(pipelineId);
      PipelineOutputDTO initialPipeline = initialResponse.getBody();
      assertThat(initialPipeline).isNotNull();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "New description");

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isEqualTo("New description");
      assertThat(response.getBody().getName())
          .as("Name should remain unchanged")
          .isEqualTo(initialPipeline.getName());
    }

    @Test
    @DisplayName("Should handle empty PATCH (no changes)")
    void shouldHandleEmptyPatch() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<PipelineOutputDTO> initialResponse = performGetById(pipelineId);
      PipelineOutputDTO initialPipeline = initialResponse.getBody();
      assertThat(initialPipeline).isNotNull();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(initialPipeline.getName());
      assertThat(response.getBody().getDescription()).isEqualTo(initialPipeline.getDescription());
    }

    @Test
    @DisplayName("Should fail to update non-existent pipeline")
    void shouldFailToUpdateNonExistentPipeline() {
      PipelineInputDTO updateInput = createUpdateInput();

      ResponseEntity<PipelineOutputDTO> response = performUpdate(UUID.randomUUID(), updateInput);

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to update pipeline without authentication")
    void shouldFailToUpdatePipelineWithoutAuth() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + pipelineId, org.springframework.http.HttpMethod.PUT);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should fail to update pipeline name to duplicate in same dataset")
    void shouldFailToUpdatePipelineToDuplicateName() {
      // Create two pipelines
      PipelineInputDTO input1 = createValidInput();
      input1.setName("pipeline_one");
      ResponseEntity<PipelineOutputDTO> response1 = performCreate(input1);
      assertThat(response1.getBody()).isNotNull();

      PipelineInputDTO input2 = createValidInput();
      input2.setName("pipeline_two");
      ResponseEntity<PipelineOutputDTO> response2 = performCreate(input2);
      assertThat(response2.getBody()).isNotNull();

      // Try to update pipeline2 to have the same name as pipeline1
      PipelineInputDTO updateInput = createUpdateInput();
      updateInput.setName("pipeline_one");

      ResponseEntity<PipelineOutputDTO> updateResponse =
          performUpdate(response2.getBody().getId(), updateInput);

      assertThat(updateResponse.getStatusCode())
          .as("Should return error status for duplicate name")
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should allow updating with same name (idempotent) using PATCH")
    void shouldAllowUpdatingWithSameName() {
      UUID pipelineId = createTestEntity();
      ResponseEntity<PipelineOutputDTO> initialResponse = performGetById(pipelineId);
      PipelineOutputDTO initialPipeline = initialResponse.getBody();
      assertThat(initialPipeline).isNotNull();
      String originalName = initialPipeline.getName();

      // Use PATCH instead of PUT to avoid BaseController bug
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("name", originalName); // Same name
      patchMap.put("description", "Updated description");

      ResponseEntity<PipelineOutputDTO> response = performPatch(pipelineId, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(originalName);
      assertThat(response.getBody().getDescription()).isEqualTo("Updated description");
    }
  }

  @Nested
  @DisplayName("Delete Pipeline Tests")
  class DeletePipelineTests {

    @Test
    @DisplayName("Should delete pipeline successfully when dataset is DRAFT")
    void shouldDeletePipelineSuccessfully() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<Void> response = performDelete(pipelineId);

      assertThat(response.getStatusCode())
          .as("Should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT);

      ResponseEntity<PipelineOutputDTO> getResponse = performGetById(pipelineId);
      assertThat(getResponse.getStatusCode())
          .as("Deleted pipeline should not be found")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete non-existent pipeline")
    void shouldFailToDeleteNonExistentPipeline() {
      ResponseEntity<Void> response = performDelete(UUID.randomUUID());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should fail to delete pipeline without authentication")
    void shouldFailToDeletePipelineWithoutAuth() {
      UUID pipelineId = createTestEntity();

      ResponseEntity<String> response =
          performRequestWithoutAuth("/" + pipelineId, org.springframework.http.HttpMethod.DELETE);

      assertThat(response.getStatusCode())
          .as("Should return UNAUTHORIZED status")
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should detach (not delete) DataSinks when pipeline is deleted")
    void shouldDetachDataSinksWhenPipelineIsDeleted() {
      UUID pipelineId = createTestEntity();
      Pipeline pipeline = pipelineRepository.findById(pipelineId).orElseThrow();

      DataSink sink = new DataSink();
      sink.setDataSet(pipeline.getDataSet());
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.FROST);
      DataSink savedSink = dataSinkRepository.save(sink);
      assertThat(dataSinkRepository.findByPipelineId(pipelineId)).hasSize(1);

      ResponseEntity<Void> deleteResponse = performDelete(pipelineId);
      assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      assertThat(dataSinkRepository.findById(savedSink.getId()))
          .as("DataSink survives pipeline deletion")
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getPipeline()).isNull());
    }

    @Test
    @DisplayName("Should cascade delete pipeline data sources associations")
    void shouldCascadeDeletePipelineDataSourceAssociations() {
      DataSource ds1 = createAvailableTestDataSource();
      DataSource ds2 = createAvailableTestDataSource();

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(ds1.getId(), ds2.getId()));

      ResponseEntity<PipelineOutputDTO> createResponse = performCreate(input);
      assertThat(createResponse.getBody()).isNotNull();
      UUID pipelineId = createResponse.getBody().getId();

      // Verify associations exist
      Pipeline pipeline = pipelineRepository.findById(pipelineId).orElseThrow();
      assertThat(pipeline.getDataSources()).hasSize(2);

      // Delete pipeline
      ResponseEntity<Void> deleteResponse = performDelete(pipelineId);
      assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      // Verify data sources still exist (not cascade deleted)
      assertThat(dataSourceRepository.findById(ds1.getId())).isPresent();
      assertThat(dataSourceRepository.findById(ds2.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {
      PipelineInputDTO input = createValidInput();
      input.setName("pipeline_with_special_äöü_ß_@#$%");

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo(input.getName());
    }

    @Test
    @DisplayName("Should handle null description")
    void shouldHandleNullDescription() {
      PipelineInputDTO input = createValidInput();
      input.setDescription(null);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDescription()).isNull();
    }

    @Test
    @DisplayName("Should handle empty name as invalid")
    void shouldHandleEmptyName() {
      PipelineInputDTO input = createValidInput();
      input.setName("");

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle null styles")
    void shouldHandleNullStyles() {
      PipelineInputDTO input = createValidInput();
      input.setStyles(null);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStyles()).isNull();
    }

    @Test
    @DisplayName("Should handle null model")
    void shouldHandleNullModel() {
      PipelineInputDTO input = createValidInput();
      input.setModel(null);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel()).isNull();
    }

    @Test
    @DisplayName("Should handle complex nested JSON in styles")
    void shouldHandleComplexNestedJsonInStyles() {
      PipelineInputDTO input = createValidInput();
      Map<String, Object> complexStyles = new HashMap<>();
      complexStyles.put(
          "nodes",
          List.of(
              Map.of("id", "1", "type", "input", "position", Map.of("x", 100, "y", 200)),
              Map.of("id", "2", "type", "output", "position", Map.of("x", 300, "y", 400))));
      complexStyles.put("edges", List.of(Map.of("source", "1", "target", "2")));
      input.setStyles(complexStyles);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStyles()).isEqualTo(complexStyles);
    }

    @Test
    @DisplayName("Should handle complex nested JSON in model")
    void shouldHandleComplexNestedJsonInModel() {
      PipelineInputDTO input = createValidInput();
      Map<String, Object> complexModel = new HashMap<>();
      complexModel.put(
          "input",
          Map.of(
              "type",
              "kafka",
              "brokers",
              List.of("broker1:9092", "broker2:9092"),
              "topics",
              List.of("topic1", "topic2")));
      complexModel.put(
          "pipeline",
          List.of(Map.of("processor", "transform", "config", Map.of("field", "value"))));
      input.setModel(complexModel);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getModel()).isEqualTo(complexModel);
    }

    @Test
    @DisplayName("Should handle null dataSinkIds (treated as empty — no DataSinks linked)")
    void shouldHandleNullDataSinkIds() {
      PipelineInputDTO input = createValidInput();
      input.setDataSinkIds(null);

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkIds()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty data source IDs set")
    void shouldHandleEmptyDataSourceIdsSet() {
      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(new HashSet<>());

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      Pipeline savedPipeline =
          pipelineRepository.findById(response.getBody().getId()).orElseThrow();
      assertThat(savedPipeline.getDataSources()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Dataset Association Tests")
  class DatasetAssociationTests {

    @Test
    @DisplayName("Should automatically associate pipeline with dataset from URL path")
    void shouldAutomaticallyAssociatePipelineWithDataset() {
      PipelineInputDTO input = createValidInput();

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      // Verify association in database
      Pipeline savedPipeline =
          pipelineRepository.findById(response.getBody().getId()).orElseThrow();
      assertThat(savedPipeline.getDataSet()).isNotNull();
      assertThat(savedPipeline.getDataSet().getId()).isEqualTo(testDataSetId);
    }

    @Test
    @DisplayName("Should allow same pipeline name in different datasets")
    void shouldAllowSamePipelineNameInDifferentDatasets() {
      // Create first pipeline in first dataset
      PipelineInputDTO input1 = createValidInput();
      input1.setName("shared_pipeline_name");
      ResponseEntity<PipelineOutputDTO> response1 = performCreate(input1);
      assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      // Create second dataset
      DataSet dataSet2 = createTestDataSet();

      // Create second pipeline with same name in second dataset
      PipelineInputDTO input2 = createValidInput();
      input2.setName("shared_pipeline_name");

      String endpoint2 = "/datasets/" + dataSet2.getId() + "/pipelines";
      ResponseEntity<PipelineOutputDTO> response2 =
          exchange(
              endpoint2,
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              input2,
              getOutputTypeReference());

      assertThat(response2.getStatusCode())
          .as("Should allow same name in different datasets")
          .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should fail to create pipeline for non-existent dataset")
    void shouldFailToCreatePipelineForNonExistentDataset() {
      UUID nonExistentDatasetId = UUID.randomUUID();
      String invalidEndpoint = "/datasets/" + nonExistentDatasetId + "/pipelines";

      PipelineInputDTO input = createValidInput();

      ResponseEntity<PipelineOutputDTO> response =
          exchange(
              invalidEndpoint,
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              input,
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("Should return NOT_FOUND for non-existent dataset")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should only return pipelines belonging to the specified dataset")
    void shouldOnlyReturnPipelinesForSpecifiedDataset() {
      performCreate(createValidInput());

      DataSet dataSet2 = createTestDataSet();
      PipelineInputDTO input2 = createValidInput();
      input2.setName("pipeline_in_other_dataset_" + System.currentTimeMillis());
      exchange(
          "/datasets/" + dataSet2.getId() + "/pipelines",
          org.springframework.http.HttpMethod.POST,
          createAuthHeaders(),
          input2,
          getOutputTypeReference());

      ResponseEntity<RestPage<PipelineOutputDTO>> response = performGetAll();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements())
          .as("Should only return pipelines belonging to the test dataset")
          .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "Should return 404, not the parent's status, for PUT from a non-DRAFT wrong dataset")
    void shouldNotRevealParentStatusOnPutFromWrongDataset() {
      UUID pipelineId = createTestEntity();
      DataSet ownDataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      ownDataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
      dataSetRepository.save(ownDataSet);
      DataSet otherDataSet = createTestDataSet();

      ResponseEntity<PipelineOutputDTO> response =
          exchange(
              "/datasets/" + otherDataSet.getId() + "/pipelines/" + pipelineId,
              org.springframework.http.HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput(),
              getOutputTypeReference());

      assertThat(response.getStatusCode())
          .as("A foreign pipeline id must answer 404 regardless of its parent's status")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // Without these three, nothing would catch the removal of the path-injection in
    // DataSetSubEntityController.preProcessInput, the only mechanism keeping a
    // sub-entity in its path dataset since the per-service guards were consolidated.
    @Test
    @DisplayName("Should ignore a foreign dataSetId in the create body and use the path dataset")
    void shouldIgnoreForeignDataSetIdOnCreate() {
      DataSet otherDataSet = createTestDataSet();

      Map<String, Object> body = new HashMap<>();
      body.put("name", "pipeline_create_with_foreign_dataset_id");
      body.put("dataSetId", otherDataSet.getId().toString());

      ResponseEntity<PipelineOutputDTO> response =
          exchange(
              getEndpointPath(),
              HttpMethod.POST,
              createAuthHeaders(),
              body,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      assertThat(response.getBody().getDataSetId())
          .as("POST must echo the dataset from the path, not the one named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());

      Pipeline saved = pipelineRepository.findById(response.getBody().getId()).orElseThrow();
      assertThat(saved.getDataSet().getId())
          .as("POST must use the dataset from the path, not the one named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());
    }

    @Test
    @DisplayName("Should ignore a foreign dataSetId in the PUT body and keep the path dataset")
    void shouldIgnoreForeignDataSetIdOnUpdate() {
      UUID pipelineId = createTestEntity();
      DataSet otherDataSet = createTestDataSet();

      Map<String, Object> body = new HashMap<>();
      body.put("name", "pipeline_put_with_foreign_dataset_id");
      body.put("dataSetId", otherDataSet.getId().toString());

      ResponseEntity<PipelineOutputDTO> response =
          exchange(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PUT,
              createAuthHeaders(),
              body,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSetId())
          .as("PUT must echo the path dataset, not the one named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());

      Pipeline saved = pipelineRepository.findById(pipelineId).orElseThrow();
      assertThat(saved.getDataSet().getId())
          .as("PUT must not move the pipeline to the dataset named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());
    }

    @Test
    @DisplayName("Should ignore a foreign dataSetId in the PATCH body and keep the path dataset")
    void shouldIgnoreForeignDataSetIdOnPatch() {
      UUID pipelineId = createTestEntity();
      DataSet otherDataSet = createTestDataSet();

      ResponseEntity<PipelineOutputDTO> response =
          performPatch(
              pipelineId,
              Map.of(
                  "name",
                  "pipeline_patch_with_foreign_dataset_id",
                  "dataSetId",
                  otherDataSet.getId().toString()));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSetId())
          .as("PATCH must echo the path dataset, not the one named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());

      Pipeline saved = pipelineRepository.findById(pipelineId).orElseThrow();
      assertThat(saved.getDataSet().getId())
          .as("PATCH must not move the pipeline to the dataset named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());
    }
  }

  @Nested
  @DisplayName("DataSource Status Validation Tests")
  class DataSourceStatusValidationTests {

    @Test
    @DisplayName("Should reject creating a pipeline with a DRAFT datasource")
    void shouldRejectDraftDataSource() {
      DataSource draftDataSource = createTestDataSource();
      // draftDataSource is DRAFT by default (DataSourceStatus.DRAFT)

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(draftDataSource.getId()));

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      // 422 with a status-agnostic message: a DRAFT source answers exactly like a nonexistent or
      // out-of-pool one, so referencing cannot be used to probe the DataSource table.
      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains("cannot be used by this Dataset's pipelines");
      assertThat(response.getBody()).doesNotContain("AVAILABLE status");
    }

    @Test
    @DisplayName("Should accept creating a pipeline with an AVAILABLE datasource")
    void shouldAcceptAvailableDataSource() {
      DataSource availableDataSource = createTestDataSource();
      availableDataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      dataSourceRepository.save(availableDataSource);

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(availableDataSource.getId()));

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should reject when one of multiple datasources is DRAFT")
    void shouldRejectWhenOneDataSourceIsDraft() {
      DataSource availableDataSource = createTestDataSource();
      availableDataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      dataSourceRepository.save(availableDataSource);

      DataSource draftDataSource = createTestDataSource();
      // draftDataSource is DRAFT by default

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(availableDataSource.getId(), draftDataSource.getId()));

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath(),
              HttpMethod.POST,
              new HttpEntity<>(input, createAuthHeaders()),
              String.class);

      // 422 with a status-agnostic message: a DRAFT source answers exactly like a nonexistent or
      // out-of-pool one, so referencing cannot be used to probe the DataSource table.
      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains("cannot be used by this Dataset's pipelines");
      assertThat(response.getBody()).doesNotContain("AVAILABLE status");
    }

    @Test
    @DisplayName("PATCH of a non-datasource field preserves the pipeline's linked datasources")
    void shouldPreserveDataSourcesWhenPatchingOtherFields() {
      DataSource dataSource = createTestDataSource();
      dataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      dataSourceRepository.save(dataSource);

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(dataSource.getId()));
      ResponseEntity<PipelineOutputDTO> createResponse = performCreate(input);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID pipelineId = createResponse.getBody().getId();

      // PATCH a non-datasource field; dataSourceIds is omitted from the body.
      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("description", "Updated description");

      ResponseEntity<PipelineOutputDTO> patchResponse = performPatch(pipelineId, patchMap);

      assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(patchResponse.getBody().getDescription()).isEqualTo("Updated description");
      // Omitted dataSourceIds must be preserved, not wiped (work item #1759).
      assertThat(patchResponse.getBody().getDataSourceIds()).containsExactly(dataSource.getId());
    }

    @Test
    @DisplayName("PUT rejects linking a DRAFT (non-AVAILABLE) datasource")
    void shouldRejectUpdateAddingDraftDataSource() {
      // A freshly created, never-linked datasource is DRAFT by default and may not be attached.
      UUID pipelineId = createTestEntity();
      DataSource draftDataSource = createTestDataSource();

      PipelineInputDTO updateInput = createUpdateInput();
      updateInput.setDataSourceIds(Set.of(draftDataSource.getId()));

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PUT,
              new HttpEntity<>(updateInput, createAuthHeaders()),
              String.class);

      // 422 with a status-agnostic message: a DRAFT source answers exactly like a nonexistent or
      // out-of-pool one, so referencing cannot be used to probe the DataSource table.
      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains("cannot be used by this Dataset's pipelines");
      assertThat(response.getBody()).doesNotContain("AVAILABLE status");
    }

    @Test
    @DisplayName("PUT omitting dataSourceIds clears the pipeline's datasource associations")
    void shouldClearDataSourcesWhenPutOmitsDataSourceIds() {
      DataSource dataSource = createTestDataSource();
      dataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);
      dataSourceRepository.save(dataSource);

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(dataSource.getId()));
      ResponseEntity<PipelineOutputDTO> createResponse = performCreate(input);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID pipelineId = createResponse.getBody().getId();

      // PUT is a full replace: omitting dataSourceIds clears the associations (unlike PATCH, which
      // reconstructs and preserves them).
      PipelineInputDTO putInput = createUpdateInput();

      ResponseEntity<PipelineOutputDTO> putResponse = performUpdate(pipelineId, putInput);

      assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(putResponse.getBody().getDataSourceIds()).isEmpty();
    }
  }

  @Nested
  @DisplayName("DataSource Scope Violation Tests (Epic 3 / Schicht C)")
  class DataSourceScopeViolationTests {

    /** Creates an AVAILABLE DataSource with the given datapool scope type and scoped pools. */
    private DataSource availableScopedDataSource(DatapoolScopeType scopeType, DataPool... pools) {
      return portalData.dataSource(
          b ->
              b.description("scope test datasource")
                  .dataSourceStatus(DataSourceStatus.AVAILABLE)
                  .datapoolScopeType(scopeType)
                  .scopedDataPools(new HashSet<>(Set.of(pools))));
    }

    /** Points the endpoint at a fresh DRAFT dataset that belongs to the given pool. */
    private void useDatasetInPool(DataPool pool) {
      DataSet dataset =
          portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT).dataPool(pool));
      testDataSetId = dataset.getId();
    }

    private ResponseEntity<String> postPipelineWith(Set<UUID> dataSourceIds) {
      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(dataSourceIds);
      return restTemplate.exchange(
          getEndpointPath(),
          HttpMethod.POST,
          new HttpEntity<>(input, createAuthHeaders()),
          String.class);
    }

    @Test
    @DisplayName("Should reject NONE-scope DataSource (dataset without pool)")
    void shouldRejectNoneScopeDataSource() {
      // Endpoint lazily creates a pool-less dataset; NONE is rejected regardless of pool.
      DataSource noneDs = availableScopedDataSource(DatapoolScopeType.NONE);

      ResponseEntity<String> response = postPipelineWith(Set.of(noneDs.getId()));

      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody())
          .contains("offendingDataSourceIds")
          .contains(noneDs.getId().toString());
    }

    @Test
    @DisplayName("Should reject SPECIFIC DataSource when DataSet's pool is not in scopedDataPools")
    void shouldRejectSpecificDataSourcePoolMismatch() {
      DataPool poolA = portalData.dataPool(b -> {});
      DataPool poolB = portalData.dataPool(b -> {});
      useDatasetInPool(poolA);
      DataSource specificDs = availableScopedDataSource(DatapoolScopeType.SPECIFIC, poolB);

      ResponseEntity<String> response = postPipelineWith(Set.of(specificDs.getId()));

      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains(specificDs.getId().toString());
    }

    @Test
    @DisplayName("Should allow SPECIFIC DataSource when DataSet's pool matches scopedDataPools")
    void shouldAllowSpecificDataSourceMatchingPool() {
      DataPool poolA = portalData.dataPool(b -> {});
      useDatasetInPool(poolA);
      DataSource specificDs = availableScopedDataSource(DatapoolScopeType.SPECIFIC, poolA);

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(specificDs.getId()));
      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID pipelineId = response.getBody().getId();
      Pipeline saved = pipelineRepository.findById(pipelineId).orElseThrow();
      assertThat(saved.getDataSources())
          .extracting(DataSource::getId)
          .containsExactly(specificDs.getId());

      // The API response must expose the linked datasource via dataSourceIds (work item #1760).
      assertThat(response.getBody().getDataSourceIds()).containsExactly(specificDs.getId());

      ResponseEntity<PipelineOutputDTO> getResponse = performGetById(pipelineId);
      assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(getResponse.getBody().getDataSourceIds()).containsExactly(specificDs.getId());
    }

    @Test
    @DisplayName("Should allow ALL-scope DataSource regardless of DataSet's pool")
    void shouldAllowAllScopeDataSource() {
      DataPool poolA = portalData.dataPool(b -> {});
      useDatasetInPool(poolA);
      DataSource allDs = availableScopedDataSource(DatapoolScopeType.ALL);

      PipelineInputDTO input = createValidInput();
      input.setDataSourceIds(Set.of(allDs.getId()));
      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Should reject SPECIFIC DataSource when DataSet has no pool")
    void shouldRejectSpecificDataSourceWhenDatasetHasNoPool() {
      // Endpoint lazily creates a pool-less dataset; a SPECIFIC datasource is confined to its pools
      // and must NOT be usable in a pool-less dataset (would defeat the confinement, F3).
      DataPool poolB = portalData.dataPool(b -> {});
      DataSource specificDs = availableScopedDataSource(DatapoolScopeType.SPECIFIC, poolB);

      ResponseEntity<String> response = postPipelineWith(Set.of(specificDs.getId()));

      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains(specificDs.getId().toString());
    }

    @Test
    @DisplayName("Should report all offending DataSources without fail-fast")
    void shouldReportAllOffendingDataSources() {
      DataPool poolA = portalData.dataPool(b -> {});
      DataPool poolB = portalData.dataPool(b -> {});
      useDatasetInPool(poolA);
      DataSource noneDs = availableScopedDataSource(DatapoolScopeType.NONE);
      DataSource specificMismatch = availableScopedDataSource(DatapoolScopeType.SPECIFIC, poolB);

      ResponseEntity<String> response =
          postPipelineWith(Set.of(noneDs.getId(), specificMismatch.getId()));

      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody())
          .contains(noneDs.getId().toString())
          .contains(specificMismatch.getId().toString());
    }

    @Test
    @DisplayName("Should reject PUT that adds an offending DataSource to an existing pipeline")
    void shouldRejectUpdateIntroducingOffendingDataSource() {
      // Pool-less dataset; create a valid pipeline with an ALL-scope datasource.
      DataSource allDs = availableScopedDataSource(DatapoolScopeType.ALL);
      PipelineInputDTO createInput = createValidInput();
      createInput.setDataSourceIds(Set.of(allDs.getId()));
      ResponseEntity<PipelineOutputDTO> createResponse = performCreate(createInput);
      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      UUID pipelineId = createResponse.getBody().getId();

      // PUT adding a NONE-scope datasource must be rejected.
      DataSource noneDs = availableScopedDataSource(DatapoolScopeType.NONE);
      PipelineInputDTO updateInput = createUpdateInput();
      updateInput.setDataSourceIds(Set.of(allDs.getId(), noneDs.getId()));

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PUT,
              new HttpEntity<>(updateInput, createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode().value()).isEqualTo(422);
      assertThat(response.getBody()).contains(noneDs.getId().toString());
    }
  }

  @Nested
  @DisplayName("DataSink linkage via dataSinkIds")
  class DataSinkLinkageTests {

    private DataSink saveFreeSink(DataSet dataSet) {
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.FROST);
      return dataSinkRepository.save(sink);
    }

    @Test
    @DisplayName("Creating a pipeline with dataSinkIds attaches existing DataSinks")
    void shouldAttachDataSinksOnCreate() {
      getEndpointPath();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = saveFreeSink(dataSet);

      PipelineInputDTO input = createValidInput();
      input.setDataSinkIds(Set.of(sink.getId()));

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      UUID pipelineId = response.getBody().getId();

      assertThat(response.getBody().getDataSinkIds()).containsExactly(sink.getId());
      assertThat(dataSinkRepository.findByPipelineId(pipelineId))
          .extracting(DataSink::getId)
          .containsExactly(sink.getId());
    }

    @Test
    @DisplayName("Updating a pipeline with empty dataSinkIds detaches but preserves DataSinks")
    void shouldDetachDataSinksOnEmptyList() {
      UUID pipelineId = createTestEntity();
      Pipeline pipeline = pipelineRepository.findById(pipelineId).orElseThrow();
      DataSet dataSet = pipeline.getDataSet();

      DataSink sink = saveFreeSink(dataSet);
      sink.setPipeline(pipeline);
      dataSinkRepository.save(sink);
      UUID sinkId = sink.getId();
      assertThat(dataSinkRepository.findByPipelineId(pipelineId)).hasSize(1);

      PipelineInputDTO updateInput = createUpdateInput();
      updateInput.setDataSinkIds(Set.of());

      ResponseEntity<PipelineOutputDTO> response = performUpdate(pipelineId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(dataSinkRepository.findByPipelineId(pipelineId)).isEmpty();
      assertThat(dataSinkRepository.findById(sinkId))
          .as("DataSink should survive pipeline detachment")
          .isPresent();
    }

    @Test
    @DisplayName("GET pipeline includes its dataSinkIds in the response")
    void shouldReturnDataSinkIdsInGetResponse() {
      UUID pipelineId = createTestEntity();
      Pipeline pipeline = pipelineRepository.findById(pipelineId).orElseThrow();
      DataSet dataSet = pipeline.getDataSet();

      DataSink sink = saveFreeSink(dataSet);
      sink.setPipeline(pipeline);
      DataSink saved = dataSinkRepository.save(sink);

      ResponseEntity<PipelineOutputDTO> response = performGetById(pipelineId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkIds()).containsExactly(saved.getId());
    }

    @Test
    @DisplayName("Should reject create when dataSinkIds includes an unknown UUID")
    void shouldRejectUnknownDataSinkId() {
      PipelineInputDTO input = createValidInput();
      input.setDataSinkIds(Set.of(UUID.randomUUID()));

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject create when a DataSink belongs to a different dataset")
    void shouldRejectCrossDatasetDataSink() {
      getEndpointPath();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));
      DataSink crossSink = saveFreeSink(otherDataSet);

      PipelineInputDTO input = createValidInput();
      input.setDataSinkIds(Set.of(crossSink.getId()));

      ResponseEntity<PipelineOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject update when a DataSink is already attached to another pipeline")
    void shouldRejectDataSinkAttachedElsewhere() {
      UUID pipelineId = createTestEntity();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();

      Pipeline otherPipeline =
          pipelineRepository.save(
              Pipeline.builder()
                  .name("other-pipeline-" + UUID.randomUUID())
                  .dataSet(dataSet)
                  .build());
      DataSink sink = saveFreeSink(dataSet);
      sink.setPipeline(otherPipeline);
      dataSinkRepository.save(sink);

      PipelineInputDTO updateInput = createUpdateInput();
      updateInput.setDataSinkIds(Set.of(sink.getId()));

      ResponseEntity<PipelineOutputDTO> response = performUpdate(pipelineId, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deleting a pipeline detaches its DataSinks but does not delete them")
    void deletingPipelineDetachesDataSinks() {
      UUID pipelineId = createTestEntity();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Pipeline pipeline = pipelineRepository.findById(pipelineId).orElseThrow();

      DataSink sink = saveFreeSink(dataSet);
      sink.setPipeline(pipeline);
      DataSink saved = dataSinkRepository.save(sink);

      ResponseEntity<Void> response = performDelete(pipelineId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(dataSinkRepository.findById(saved.getId()))
          .as("DataSink should survive pipeline deletion")
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getPipeline()).isNull());
    }
  }

  @Nested
  @DisplayName("Parent DataSet Mutation Guard")
  class MutationGuardTests {

    private static final String NOT_EDITABLE_URN = "urn:civitas:error:DATASET_NOT_EDITABLE";
    private static final String IN_USE_URN = "urn:civitas:error:RESOURCE_IN_USE";

    private void setParentStatus(DataSetStatus status) {
      ensureTestDataSet();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setDataSetStatus(status);
      dataSetRepository.save(dataSet);
    }

    private void setParentPendingSaga(PendingSagaType pendingSagaType) {
      ensureTestDataSet();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setPendingSagaType(pendingSagaType);
      dataSetRepository.save(dataSet);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("POST is rejected when the parent dataset is not DRAFT")
    void createRejectedWhenParentNotDraft(DataSetStatus status) {
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath(), HttpMethod.POST, createAuthHeaders(), createValidInput());

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(pipelineRepository.count()).as("No pipeline was created").isZero();
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PUT is rejected when the parent dataset is not DRAFT")
    void updateRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID pipelineId = createTestEntity();
      String originalName = pipelineRepository.findById(pipelineId).orElseThrow().getName();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(pipelineRepository.findById(pipelineId).orElseThrow().getName())
          .isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PATCH is rejected when the parent dataset is not DRAFT")
    void patchRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID pipelineId = createTestEntity();
      String originalName = pipelineRepository.findById(pipelineId).orElseThrow().getName();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of("name", "patched_pipeline"));

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(pipelineRepository.findById(pipelineId).orElseThrow().getName())
          .isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("DELETE is rejected when the parent dataset is not DRAFT")
    void deleteRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID pipelineId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + pipelineId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(pipelineRepository.findById(pipelineId)).isPresent();
    }

    @Test
    @DisplayName("POST is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void createRejectedWhileSagaInFlight() {
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath(), HttpMethod.POST, createAuthHeaders(), createValidInput());

      assertRejected(response, HttpStatus.CONFLICT, IN_USE_URN);
      assertThat(pipelineRepository.count()).as("No pipeline was created").isZero();
    }

    @Test
    @DisplayName("PUT is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void updateRejectedWhileSagaInFlight() {
      UUID pipelineId = createTestEntity();
      String originalName = pipelineRepository.findById(pipelineId).orElseThrow().getName();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + pipelineId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertRejected(response, HttpStatus.CONFLICT, IN_USE_URN);
      assertThat(pipelineRepository.findById(pipelineId).orElseThrow().getName())
          .isEqualTo(originalName);
    }

    @Test
    @DisplayName("DELETE is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void deleteRejectedWhileSagaInFlight() {
      UUID pipelineId = createTestEntity();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + pipelineId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertRejected(response, HttpStatus.CONFLICT, IN_USE_URN);
      assertThat(pipelineRepository.findById(pipelineId))
          .as("Entity survives the rejected delete")
          .isPresent();
    }

    @Test
    @DisplayName("PUT, PATCH and DELETE all succeed on a DRAFT dataset")
    void mutationsAcceptedOnDraftParent() {
      UUID pipelineId = createTestEntity();

      assertThat(performUpdate(pipelineId, createUpdateInput()).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performPatch(pipelineId, Map.of("description", "patched")).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performDelete(pipelineId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }
}
