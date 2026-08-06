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
import java.util.HashMap;
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
    extends BaseControllerIntegrationTest<DataSinkInputDTO, DataSinkOutputDTO> {

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

  @Override
  protected DataSinkInputDTO createValidInput() {
    ensureTestData();
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setDataSinkType(DataSinkType.FROST);
    input.setConfiguration(Map.of());
    return input;
  }

  @Override
  protected DataSinkInputDTO createInvalidInput() {
    ensureTestData();
    DataSinkInputDTO input = new DataSinkInputDTO();
    input.setConfiguration(Map.of());
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
      assertThat(body.getPipelineId()).isNull();
      assertThat(body.isInUse()).isFalse();
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

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
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

    @Test
    @DisplayName("Should report inUse=true when the DataSink is linked to a pipeline")
    void shouldReportInUseWhenLinkedToPipeline() {
      ensureTestData();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Pipeline pipeline = pipelineRepository.findById(testPipelineId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.FROST);
      UUID id = dataSinkRepository.save(sink).getId();

      ResponseEntity<DataSinkOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().isInUse()).isTrue();
      assertThat(response.getBody().getPipelineId()).isEqualTo(testPipelineId);
    }
  }

  // ── Write tests ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Create DataSink Tests")
  class CreateDataSinkTests {

    @Test
    @DisplayName("POST creates a FROST DataSink scoped to the parent dataset")
    void postCreatesFrostDataSink() {
      ResponseEntity<DataSinkOutputDTO> response = performCreate(createValidInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      DataSinkOutputDTO body = response.getBody();
      assertThat(body.getId()).isNotNull();
      assertThat(body.getDataSinkType()).isEqualTo(DataSinkType.FROST);
      assertThat(body.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(body.getPipelineId()).isNull();
      assertThat(body.isInUse()).isFalse();
      assertThat(dataSinkRepository.findById(body.getId()))
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getDataSet().getId()).isEqualTo(testDataSetId));
    }

    @Test
    @DisplayName("POST creates a POSTGIS DataSink with a resolved DSV summary")
    void postCreatesPostgisDataSink() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of("tableName", "sensor_data", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(response.getBody().getDataSetId()).isEqualTo(testDataSetId);
    }

    @Test
    @DisplayName("POST returns 409 when a sibling POSTGIS sink already uses the tableName")
    void postRejectsDuplicatePostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(
          Map.of("tableName", "shared_table", "dataStructureVersionId", dsv.getId().toString()));
      assertThat(performCreate(first).getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataSinkInputDTO duplicate = new DataSinkInputDTO();
      duplicate.setDataSinkType(DataSinkType.POSTGIS);
      duplicate.setConfiguration(
          Map.of("tableName", "shared_table", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("POST returns 409 when the tableName differs from a sibling only in case")
    void postRejectsCaseOnlyDuplicatePostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(
          Map.of("tableName", "case_table", "dataStructureVersionId", dsv.getId().toString()));
      assertThat(performCreate(first).getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataSinkInputDTO duplicate = new DataSinkInputDTO();
      duplicate.setDataSinkType(DataSinkType.POSTGIS);
      duplicate.setConfiguration(
          Map.of("tableName", "CASE_TABLE", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("POST returns 400 when the POSTGIS tableName is not a plain identifier")
    void postRejectsNonIdentifierPostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of(
              "tableName", "  padded_table  ", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST returns 400 when FROST configuration carries an unknown key")
    void postRejectsFrostWithUnknownConfigurationKey() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("unexpected", "value"));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST accepts a FROST DataSink referencing its mapping's target structure")
    void postAcceptsFrostWithDataStructureVersionId() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<DataSinkOutputDTO> response =
          exchange(
              getEndpointPath(),
              HttpMethod.POST,
              createAuthHeaders(),
              input,
              getOutputTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("POST returns 400 when the FROST dataStructureVersionId does not exist")
    void postRejectsFrostWithUnknownDataStructureVersion() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("dataStructureVersionId", UUID.randomUUID().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST returns 400 when POSTGIS configuration is missing tableName")
    void postRejectsPostgisWithoutTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST returns 400 when POSTGIS dataStructureVersionId does not resolve")
    void postRejectsPostgisWithUnknownDsv() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of(
              "tableName", "sensor_data", "dataStructureVersionId", UUID.randomUUID().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Update DataSink Tests")
  class UpdateDataSinkTests {

    @Test
    @DisplayName("PUT replaces the configuration of an existing same-type DataSink")
    void putReplacesDataSinkConfiguration() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(
          Map.of("tableName", "original_table", "dataStructureVersionId", dsv.getId().toString()));
      UUID id = dataSinkRepository.save(sink).getId();

      DataSinkInputDTO updateInput = new DataSinkInputDTO();
      updateInput.setDataSinkType(DataSinkType.POSTGIS);
      updateInput.setConfiguration(
          Map.of("tableName", "updated_table", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<DataSinkOutputDTO> response = performUpdate(id, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(dataSinkRepository.findById(id))
          .isPresent()
          .get()
          .satisfies(
              s -> assertThat(s.getConfiguration()).containsEntry("tableName", "updated_table"));
    }

    @Test
    @DisplayName("PUT returns 400 when changing the immutable dataSinkType")
    void putRejectsTypeChange() {
      UUID id = createTestEntity();

      DataSinkInputDTO updateInput = new DataSinkInputDTO();
      updateInput.setDataSinkType(DataSinkType.POSTGIS);
      updateInput.setConfiguration(
          Map.of("tableName", "any_table", "dataStructureVersionId", UUID.randomUUID().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PUT, createAuthHeaders(), updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail()).contains("dataSinkType cannot be changed");
      assertThat(dataSinkRepository.findById(id))
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getDataSinkType()).isEqualTo(DataSinkType.FROST));
    }

    @Test
    @DisplayName("PUT returns 404 when updating a DataSink from a different dataset")
    void putReturns404ForCrossDatasetUpdate() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT returns 400 when configuration is invalid for the type")
    void putRejectsInvalidConfiguration() {
      UUID id = createTestEntity();

      DataSinkInputDTO invalidUpdate = new DataSinkInputDTO();
      invalidUpdate.setDataSinkType(DataSinkType.FROST);
      invalidUpdate.setConfiguration(Map.of("unexpected", "value"));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PUT, createAuthHeaders(), invalidUpdate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Patch DataSink Tests")
  class PatchDataSinkTests {

    @Test
    @DisplayName("PATCH updates configuration on a POSTGIS DataSink in place")
    void patchUpdatesPostgisConfiguration() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(
          Map.of("tableName", "original_table", "dataStructureVersionId", dsv.getId().toString()));
      UUID id = dataSinkRepository.save(sink).getId();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put(
          "configuration",
          Map.of("tableName", "renamed_table", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<DataSinkOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(dataSinkRepository.findById(id))
          .isPresent()
          .get()
          .satisfies(
              s -> assertThat(s.getConfiguration()).containsEntry("tableName", "renamed_table"));
    }

    @Test
    @DisplayName("PATCH leaves omitted fields unchanged")
    void patchLeavesOmittedFieldsUnchanged() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(
          Map.of("tableName", "sensor_data", "dataStructureVersionId", dsv.getId().toString()));
      UUID id = dataSinkRepository.save(sink).getId();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put(
          "configuration",
          Map.of("tableName", "renamed_table", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<DataSinkOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType())
          .as("dataSinkType should remain POSTGIS")
          .isEqualTo(DataSinkType.POSTGIS);
      assertThat(response.getBody().getDataSetId())
          .as("dataSetId should remain the parent dataset")
          .isEqualTo(testDataSetId);
    }

    /**
     * The data-loss guard answers 409 on this path too, so a status-only assertion could not tell
     * the two rejections apart.
     */
    @Test
    @DisplayName("PATCH returns 409 when renaming a sink onto a sibling POSTGIS tableName")
    void patchRejectsDuplicatePostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(
          Map.of("tableName", "t_one", "dataStructureVersionId", dsv.getId().toString()));
      ResponseEntity<DataSinkOutputDTO> existing = performCreate(first);
      assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(existing.getBody()).isNotNull();

      DataSinkInputDTO second = new DataSinkInputDTO();
      second.setDataSinkType(DataSinkType.POSTGIS);
      second.setConfiguration(
          Map.of("tableName", "t_two", "dataStructureVersionId", dsv.getId().toString()));
      ResponseEntity<DataSinkOutputDTO> created = performCreate(second);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(created.getBody()).isNotNull();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put(
          "configuration",
          Map.of("tableName", "T_ONE", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + created.getBody().getId(),
              HttpMethod.PATCH,
              createAuthHeaders(),
              patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .contains("tableName")
          .contains("T_ONE")
          .doesNotContain("confirmDataLoss");
      assertThat(dataSinkRepository.findById(existing.getBody().getId()))
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getConfiguration()).containsEntry("tableName", "t_one"));
      assertThat(dataSinkRepository.findById(created.getBody().getId()))
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getConfiguration()).containsEntry("tableName", "t_two"));
    }

    @Test
    @DisplayName("PATCH returns 400 when changing the immutable dataSinkType")
    void patchRejectsTypeChange() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));

      UUID id = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("dataSinkType", DataSinkType.POSTGIS.name());
      patchMap.put(
          "configuration",
          Map.of("tableName", "patched_table", "dataStructureVersionId", dsv.getId().toString()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PATCH, createAuthHeaders(), patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail()).contains("dataSinkType cannot be changed");
      assertThat(dataSinkRepository.findById(id))
          .isPresent()
          .get()
          .satisfies(s -> assertThat(s.getDataSinkType()).isEqualTo(DataSinkType.FROST));
    }

    @Test
    @DisplayName("PATCH allows explicitly setting dataSinkType to its current value")
    void patchAllowsSameTypeRestate() {
      UUID id = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("dataSinkType", DataSinkType.FROST.name());

      ResponseEntity<DataSinkOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.FROST);
    }

    @Test
    @DisplayName("PATCH returns 400 when resulting configuration is invalid for the type")
    void patchRejectsInvalidConfigurationForType() {
      UUID id = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("configuration", Map.of("unexpected", "value"));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PATCH, createAuthHeaders(), patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PATCH returns 404 when DataSink belongs to a different dataset")
    void patchReturns404ForCrossDatasetUpdate() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("configuration", Map.of());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.PATCH,
              createAuthHeaders(),
              patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH returns 400 when patched against a non-UUID dataSetId path variable")
    void patchReturns400ForInvalidDataSetIdPath() {
      UUID sinkId = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("configuration", Map.of());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/datasinks/" + sinkId,
              HttpMethod.PATCH,
              createAuthHeaders(),
              patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Empty PATCH leaves the DataSink unchanged")
    void emptyPatchLeavesDataSinkUnchanged() {
      UUID id = createTestEntity();

      ResponseEntity<DataSinkOutputDTO> before = performGetById(id);
      assertThat(before.getBody()).isNotNull();
      DataSinkType originalType = before.getBody().getDataSinkType();

      Map<String, Object> patchMap = new HashMap<>();

      ResponseEntity<DataSinkOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(originalType);
      assertThat(response.getBody().getDataSetId()).isEqualTo(testDataSetId);
    }
  }

  @Nested
  @DisplayName("Delete DataSink Tests")
  class DeleteDataSinkTests {

    @Test
    @DisplayName("DELETE removes a free DataSink")
    void deleteRemovesFreeDataSink() {
      UUID id = createTestEntity();

      ResponseEntity<Void> response = performDelete(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(dataSinkRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("DELETE returns 404 when DataSink belongs to a different dataset")
    void deleteReturns404ForCrossDataset() {
      UUID sinkId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.DRAFT));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/datasinks/" + sinkId,
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(dataSinkRepository.findById(sinkId)).isPresent();
    }

    @Test
    @DisplayName("DELETE returns 409 when a Layer references the DataSink")
    void deleteReturns409WhenLayerReferencesDataSink() {
      ensureTestData();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Pipeline pipeline = pipelineRepository.findById(testPipelineId).orElseThrow();
      DataSink sink = portalData.dataSink(dataSet, pipeline);
      portalData.layer(dataSet, sink);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sink.getId(), HttpMethod.DELETE, createAuthHeaders(), null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(dataSinkRepository.findById(sink.getId())).isPresent();
    }
  }
}
