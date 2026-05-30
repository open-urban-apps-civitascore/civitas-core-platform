package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.RestPage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSink Controller Integration Tests")
class DataSinkControllerIntegrationTest
    extends BaseReadOnlyControllerIntegrationTest<DataSinkInputDTO, DataSinkOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private PipelineRepository pipelineRepository;

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

  /** Creates a DataSink directly via the repository since the controller is read-only. */
  @Override
  protected UUID createTestEntity() {
    ensureTestData();
    Pipeline pipeline = pipelineRepository.findById(testPipelineId).orElseThrow();
    DataSink sink = new DataSink();
    sink.setPipeline(pipeline);
    sink.setDataSinkType(DataSinkType.FROST);
    return dataSinkRepository.save(sink).getId();
  }

  @Override
  protected DataSinkInputDTO createValidInput() {
    ensureTestData();
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSinkType(DataSinkType.FROST);
    input.setConfiguration(Map.of());
    input.setPipelineId(testPipelineId);
    return input;
  }

  @Override
  protected DataSinkInputDTO createInvalidInput() {
    ensureTestData();
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setConfiguration(Map.of());
    input.setPipelineId(testPipelineId);
    return input;
  }

  @Override
  protected DataSinkInputDTO createUpdateInput() {
    return createValidInput();
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

  // ── Write endpoints return 405 ────────────────────────────────────────────

  @Nested
  @DisplayName("Write endpoints are not exposed")
  class WriteEndpointsReturn405 {

    @Test
    @DisplayName("POST returns 405")
    void postReturns405() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath(), HttpMethod.POST, createAuthHeaders(), createValidInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("PUT returns 405")
    void putReturns405() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + UUID.randomUUID(),
              HttpMethod.PUT,
              createAuthHeaders(),
              createValidInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("PATCH returns 405")
    void patchReturns405() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + UUID.randomUUID(),
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("DELETE returns 405")
    void deleteReturns405() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
  }

  // ── Read tests ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Read DataSink Tests")
  class ReadDataSinkTests {

    @Test
    @DisplayName("Should return DataSink with correct fields by ID")
    void shouldReturnDataSinkWithCorrectFields() {
      UUID id = createTestEntity();

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

    @Test
    @DisplayName("Should read back a POSTGIS DataSink with correct dataSetId")
    void shouldReadPostgisDataSinkWithCorrectDataSetId() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      Pipeline pipeline = pipelineRepository.findById(testPipelineId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(
          Map.of("tableName", "sensor_data", "dataStructureVersionId", dsv.getId().toString()));
      UUID id = dataSinkRepository.save(sink).getId();

      ResponseEntity<DataSinkOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(response.getBody().getDataSetId()).isEqualTo(testDataSetId);
    }
  }
}
