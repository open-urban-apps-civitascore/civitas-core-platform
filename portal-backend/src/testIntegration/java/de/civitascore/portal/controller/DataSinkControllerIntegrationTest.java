package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSink Controller Integration Tests")
class DataSinkControllerIntegrationTest
    extends BaseControllerIntegrationTest<DataSinkInputDTO, DataSinkOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;

  private UUID testDataSetId;
  private UUID testPipelineId;

  private void ensureTestData() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      DataSet ds = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));
      testDataSetId = ds.getId();
      testPipelineId = portalData.pipeline(ds).getId();
    }
  }

  @Override
  protected String getEndpointPath() {
    ensureTestData();
    return "/datasets/" + testDataSetId + "/datasinks";
  }

  private UUID getTestPipelineId() {
    ensureTestData();
    return testPipelineId;
  }

  @Override
  protected DataSinkInputDTO createValidInput() {
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSinkType(DataSinkType.FROST);
    input.setConfiguration(Map.of());
    input.setPipelineId(getTestPipelineId());
    return input;
  }

  @Override
  protected DataSinkInputDTO createInvalidInput() {
    DataSinkInputDTO input = new DataSinkInputDTO();
    // dataSinkType intentionally missing to trigger @NotNull violation → 400
    input.setConfiguration(Map.of());
    input.setPipelineId(getTestPipelineId());
    return input;
  }

  @Override
  protected DataSinkInputDTO createUpdateInput() {
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSinkType(DataSinkType.FROST);
    input.setConfiguration(Map.of());
    input.setPipelineId(getTestPipelineId());
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataSinkOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSinkOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSinkOutputDTO output) {
    return output.getId();
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
    testDataSetId = null;
    testPipelineId = null;
  }

  // PATCH is not supported for DataSinks — override base tests that expect 404/400 for PATCH

  @Override
  @Test
  @DisplayName("PATCH is not supported — should return 405")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    ResponseEntity<DataSinkOutputDTO> response = performPatch(id, Map.of());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Override
  @Test
  @DisplayName("PATCH on any DataSink returns 405 regardless of entity existence")
  void shouldReturn404WhenPatchingNonExistentEntity() {
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + UUID.randomUUID(),
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Nested
  @DisplayName("Create DataSink Tests")
  class CreateDataSinkTests {

    @Test
    @DisplayName("Should create FROST DataSink with empty configuration")
    void shouldCreateFrostDataSink() {
      ResponseEntity<DataSinkOutputDTO> response = performCreate(createValidInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      DataSinkOutputDTO body = response.getBody();
      assertThat(body.getDataSinkType()).isEqualTo(DataSinkType.FROST);
      assertThat(body.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(body.getPipelineId()).isEqualTo(testPipelineId);
      assertThat(body.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject FROST DataSink with non-empty configuration")
    void shouldRejectFrostDataSinkWithNonEmptyConfig() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("unexpected", "value"));
      input.setPipelineId(getTestPipelineId());

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject create when pipeline belongs to a different dataset")
    void shouldRejectCreateWithPipelineFromDifferentDataset() {
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));
      Pipeline pipelineInOtherDataSet = portalData.pipeline(otherDataSet);

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of());
      input.setPipelineId(pipelineInOtherDataSet.getId());

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should create POSTGIS DataSink with valid configuration")
    void shouldCreatePostgisDataSink() {
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of("tableName", "sensor_data", "dataStructureVersionId", dsv.getId().toString()));
      input.setPipelineId(getTestPipelineId());

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
    }

    @Test
    @DisplayName("Should reject POSTGIS DataSink with non-existent dataStructureVersionId")
    void shouldRejectPostgisDataSinkWithInvalidDsvId() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of(
              "tableName", "sensor_data", "dataStructureVersionId", UUID.randomUUID().toString()));
      input.setPipelineId(getTestPipelineId());

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 400 when creating DataSink with a non-UUID dataSetId")
    void shouldReturn400WhenCreateWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/datasinks",
              HttpMethod.POST,
              createAuthHeaders(),
              createValidInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should reject POSTGIS DataSink without tableName")
    void shouldRejectPostgisDataSinkWithoutTableName() {
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("dataStructureVersionId", dsv.getId().toString()));
      input.setPipelineId(getTestPipelineId());

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Read DataSink Tests")
  class ReadDataSinkTests {

    @Test
    @DisplayName("Should return DataSink with correct fields by ID")
    void shouldReturnDataSinkWithCorrectFields() {
      ResponseEntity<DataSinkOutputDTO> createResponse = performCreate(createValidInput());
      assertThat(createResponse.getBody()).isNotNull();
      UUID id = createResponse.getBody().getId();

      ResponseEntity<DataSinkOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      DataSinkOutputDTO body = response.getBody();
      assertThat(body.getId()).isEqualTo(id);
      assertThat(body.getDataSinkType()).isEqualTo(DataSinkType.FROST);
      assertThat(body.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(body.getPipelineId()).isEqualTo(testPipelineId);
    }

    @Test
    @DisplayName("Should return 400 when getting DataSink by ID with a non-UUID dataSetId")
    void shouldReturn400WhenGetByIdWithInvalidDataSetId() {
      UUID sinkId = createTestEntity();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/datasinks/" + sinkId,
              HttpMethod.GET,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should return 404 when getting DataSink from wrong dataset")
    void shouldReturn404WhenGettingFromWrongDataset() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      ResponseEntity<DataSinkOutputDTO> response =
          exchange(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.GET,
              createAuthHeaders(),
              null,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Update DataSink Tests")
  class UpdateDataSinkTests {

    @Test
    @DisplayName("Should update DataSink with PUT")
    void shouldUpdateDataSinkWithPut() {
      UUID id = createTestEntity();

      ResponseEntity<DataSinkOutputDTO> response = performUpdate(id, createUpdateInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getId()).isEqualTo(id);
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.FROST);
    }

    @Test
    @DisplayName("Should return 400 when updating DataSink with a non-UUID dataSetId")
    void shouldReturn400WhenUpdateWithInvalidDataSetId() {
      UUID sinkId = createTestEntity();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/datasinks/" + sinkId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should return 404 when updating DataSink from wrong dataset")
    void shouldReturn404WhenUpdatingFromWrongDataset() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      ResponseEntity<DataSinkOutputDTO> response =
          exchange(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput(),
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Delete DataSink Tests")
  class DeleteDataSinkTests {

    @Test
    @DisplayName("Should return 400 when deleting DataSink with a non-UUID dataSetId")
    void shouldReturn400WhenDeleteWithInvalidDataSetId() {
      UUID sinkId = createTestEntity();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/datasinks/" + sinkId,
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should return 404 when deleting DataSink from wrong dataset")
    void shouldReturn404WhenDeletingFromWrongDataset() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(dataSinkRepository.findById(sinkId))
          .as("DataSink should still exist after failed cross-dataset delete")
          .isPresent();
    }

    @Test
    @DisplayName("Should fail to delete DataSink without authentication")
    void shouldFailToDeleteWithoutAuthentication() {
      UUID id = createTestEntity();

      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + id, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }
}
