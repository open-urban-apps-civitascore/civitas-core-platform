package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataSink Controller Integration Tests")
class DataSinkControllerIntegrationTest
    extends DataSetSubEntityControllerIntegrationTest<DataSinkInputDTO, DataSinkOutputDTO> {

  @Override
  protected String getSubEntityPathSegment() {
    return "datasinks";
  }

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;
  @Autowired private PipelineRepository pipelineRepository;

  private UUID testDataSetId;
  private UUID testPipelineId;

  /** A sink's {@code tableName} lives in its registry configuration document, not on the row. */
  private String storedTableName(UUID dataSinkId) {
    return dataSinkRepository
        .findById(dataSinkId)
        .map(DataSink::getConfigurationUrn)
        .flatMap(modelRegistryGateway::fetchPayload)
        .map(ModelRegistryGateway.RegistryDocument::content)
        .map(content -> content.get("tableName"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse(null);
  }

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
    @DisplayName("Should read back a POSTGIS DataSink with correct dataSetId")
    void shouldReadPostgisDataSinkWithCorrectDataSetId() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("ReadTest"));
      String modelUrn = dsv.getModelUrn();
      UUID versionId = dsv.getId();

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink = dataSinkRepository.save(sink);
      UUID id =
          portalData
              .attachSinkConfiguration(
                  sink, Map.of("tableName", "sensor_data", "element", dsv.getModelUrn()))
              .getId();

      ResponseEntity<DataSinkOutputDTO> response = performGetById(id);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(response.getBody().getDataSetId()).isEqualTo(testDataSetId);
      assertThat(response.getBody().getConfiguration())
          .asInstanceOf(type(PostgisConfigurationOutput.class))
          .satisfies(
              config -> {
                assertThat(config.getTableName()).isEqualTo("sensor_data");
                assertThat(config.getElement()).isEqualTo(modelUrn);
                assertThat(config.getDataStructureVersion()).isNotNull();
                assertThat(config.getDataStructureVersion().getId()).isEqualTo(versionId);
                assertThat(config.getDataStructureVersion().getDataStructureId())
                    .isEqualTo(ds.getId());
              });
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
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PostgisCreate"));
      String elementUrn = dsv.getModelUrn();
      UUID versionId = dsv.getId();

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("tableName", "sensor_data", "element", elementUrn));

      ResponseEntity<DataSinkOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(response.getBody().getDataSetId()).isEqualTo(testDataSetId);
      assertThat(response.getBody().getConfiguration())
          .asInstanceOf(type(PostgisConfigurationOutput.class))
          .satisfies(
              config -> {
                assertThat(config.getElement()).isEqualTo(elementUrn);
                assertThat(config.getDataStructureVersion()).isNotNull();
                assertThat(config.getDataStructureVersion().getId()).isEqualTo(versionId);
                assertThat(config.getDataStructureVersion().getDataStructureId())
                    .isEqualTo(ds.getId());
              });
    }

    @Test
    @DisplayName("POST returns 409 when a sibling POSTGIS sink already uses the tableName")
    void postRejectsDuplicatePostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PostgisDuplicate"));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(Map.of("tableName", "shared_table", "element", dsv.getModelUrn()));
      assertThat(performCreate(first).getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataSinkInputDTO duplicate = new DataSinkInputDTO();
      duplicate.setDataSinkType(DataSinkType.POSTGIS);
      duplicate.setConfiguration(Map.of("tableName", "shared_table", "element", dsv.getModelUrn()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail()).contains("tableName").contains("shared_table");
    }

    @Test
    @DisplayName("POST returns 409 when the tableName differs from a sibling only in case")
    void postRejectsCaseOnlyDuplicatePostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PostgisCaseDup"));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(Map.of("tableName", "case_table", "element", dsv.getModelUrn()));
      assertThat(performCreate(first).getStatusCode()).isEqualTo(HttpStatus.CREATED);

      DataSinkInputDTO duplicate = new DataSinkInputDTO();
      duplicate.setDataSinkType(DataSinkType.POSTGIS);
      duplicate.setConfiguration(Map.of("tableName", "CASE_TABLE", "element", dsv.getModelUrn()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail()).contains("tableName").contains("CASE_TABLE");
    }

    @Test
    @DisplayName("POST returns 400 when the POSTGIS tableName is not a plain identifier")
    void postRejectsNonIdentifierPostgisTableName() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PostgisBadTable"));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(Map.of("tableName", "  padded_table  ", "element", dsv.getModelUrn()));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .contains("tableName must start with a letter or underscore");
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
    void postAcceptsFrostWithElement() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("FrostAccept"));

      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(Map.of("element", dsv.getModelUrn()));

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
    @DisplayName("POST returns 400 when the FROST element does not exist")
    void postRejectsFrostWithUnknownElement() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.FROST);
      input.setConfiguration(
          Map.of("element", "urn:core:platform:civitas:element:common:missing:1.0.0"));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST returns 400 when POSTGIS configuration is missing tableName")
    void postRejectsPostgisWithoutTableName() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of("element", "urn:core:platform:civitas:element:common:missing:1.0.0"));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      // The element URN is deliberately unresolvable too, so pin the tableName cause.
      assertThat(response.getBody().getDetail())
          .isEqualTo("tableName is required for POSTGIS sinks");
    }

    @Test
    @DisplayName("POST returns 400 when POSTGIS element does not resolve")
    void postRejectsPostgisWithUnknownElement() {
      DataSinkInputDTO input = new DataSinkInputDTO();
      input.setDataSinkType(DataSinkType.POSTGIS);
      input.setConfiguration(
          Map.of(
              "tableName",
              "sensor_data",
              "element",
              "urn:core:platform:civitas:element:common:missing:1.0.0"));

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
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PutReplace"));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink = dataSinkRepository.save(sink);
      UUID id =
          portalData
              .attachSinkConfiguration(
                  sink, Map.of("tableName", "original_table", "element", dsv.getModelUrn()))
              .getId();

      DataSinkInputDTO updateInput = new DataSinkInputDTO();
      updateInput.setDataSinkType(DataSinkType.POSTGIS);
      updateInput.setConfiguration(
          Map.of("tableName", "updated_table", "element", dsv.getModelUrn()));

      ResponseEntity<DataSinkOutputDTO> response = performUpdate(id, updateInput);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      // The configuration lives in the Model Forge registry; the API response serves it from there.
      assertThat(response.getBody().getConfiguration())
          .isInstanceOfSatisfying(
              PostgisConfigurationOutput.class,
              c -> assertThat(c.getTableName()).isEqualTo("updated_table"));
    }

    @Test
    @DisplayName("PUT returns 400 when changing the immutable dataSinkType")
    void putRejectsTypeChange() {
      UUID id = createTestEntity();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PutTypeChange"));

      DataSinkInputDTO updateInput = new DataSinkInputDTO();
      updateInput.setDataSinkType(DataSinkType.POSTGIS);
      updateInput.setConfiguration(Map.of("tableName", "any_table", "element", dsv.getModelUrn()));

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
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PatchUpdate"));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink = dataSinkRepository.save(sink);
      UUID id =
          portalData
              .attachSinkConfiguration(
                  sink, Map.of("tableName", "original_table", "element", dsv.getModelUrn()))
              .getId();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put(
          "configuration", Map.of("tableName", "renamed_table", "element", dsv.getModelUrn()));

      ResponseEntity<DataSinkOutputDTO> response = performPatch(id, patchMap);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      // The configuration lives in the Model Forge registry; the API response serves it from there.
      assertThat(response.getBody().getConfiguration())
          .isInstanceOfSatisfying(
              PostgisConfigurationOutput.class,
              c -> assertThat(c.getTableName()).isEqualTo("renamed_table"));
    }

    @Test
    @DisplayName("PATCH leaves omitted fields unchanged")
    void patchLeavesOmittedFieldsUnchanged() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PatchOmitted"));

      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink = dataSinkRepository.save(sink);
      UUID id =
          portalData
              .attachSinkConfiguration(
                  sink, Map.of("tableName", "sensor_data", "element", dsv.getModelUrn()))
              .getId();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put(
          "configuration", Map.of("tableName", "renamed_table", "element", dsv.getModelUrn()));

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
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PatchDuplicate"));

      DataSinkInputDTO first = new DataSinkInputDTO();
      first.setDataSinkType(DataSinkType.POSTGIS);
      first.setConfiguration(Map.of("tableName", "t_one", "element", dsv.getModelUrn()));
      ResponseEntity<DataSinkOutputDTO> existing = performCreate(first);
      assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(existing.getBody()).isNotNull();

      DataSinkInputDTO second = new DataSinkInputDTO();
      second.setDataSinkType(DataSinkType.POSTGIS);
      second.setConfiguration(Map.of("tableName", "t_two", "element", dsv.getModelUrn()));
      ResponseEntity<DataSinkOutputDTO> created = performCreate(second);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(created.getBody()).isNotNull();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("configuration", Map.of("tableName", "T_ONE", "element", dsv.getModelUrn()));

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
      assertThat(storedTableName(existing.getBody().getId())).isEqualTo("t_one");
      assertThat(storedTableName(created.getBody().getId())).isEqualTo("t_two");
    }

    @Test
    @DisplayName("PATCH returns 400 when changing the immutable dataSinkType")
    void patchRejectsTypeChange() {
      ensureTestData();
      var ds = portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
      DataStructureVersion dsv =
          portalData.dataStructureVersion(
              ds, b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE));
      dsv = portalData.attachModel(dsv, portalData.dataStructureVersionModel("PatchTypeChange"));

      UUID id = createTestEntity();

      Map<String, Object> patchMap = new HashMap<>();
      patchMap.put("dataSinkType", DataSinkType.POSTGIS.name());
      patchMap.put(
          "configuration", Map.of("tableName", "patched_table", "element", dsv.getModelUrn()));

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

      assertRejected(response, HttpStatus.CONFLICT, "urn:civitas:error:RESOURCE_IN_USE");
      assertThat(dataSinkRepository.findById(sink.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("Parent DataSet Mutation Guard")
  class MutationGuardTests {

    private static final String NOT_EDITABLE_URN = "urn:civitas:error:DATASET_NOT_EDITABLE";
    private static final String SAGA_IN_FLIGHT_URN = "urn:civitas:error:SAGA_IN_FLIGHT";

    private void setParentStatus(DataSetStatus status) {
      ensureTestData();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setDataSetStatus(status);
      dataSetRepository.save(dataSet);
    }

    private void setParentPendingSaga(PendingSagaType pendingSagaType) {
      ensureTestData();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setPendingSagaType(pendingSagaType);
      dataSetRepository.save(dataSet);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("POST is rejected when the parent dataset is not DRAFT")
    void createRejectedWhenParentNotDraft(DataSetStatus status) {
      DataSinkInputDTO input = createValidInput();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(dataSinkRepository.findByDataSetId(testDataSetId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PUT is rejected when the parent dataset is not DRAFT")
    void updateRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID sinkId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sinkId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(dataSinkRepository.findById(sinkId)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PATCH is rejected when the parent dataset is not DRAFT")
    void patchRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID sinkId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sinkId,
              HttpMethod.PATCH,
              createAuthHeaders(),
              Map.of("configuration", Map.of()));

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("DELETE is rejected when the parent dataset is not DRAFT")
    void deleteRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID sinkId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sinkId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(dataSinkRepository.findById(sinkId)).isPresent();
    }

    @Test
    @DisplayName("POST is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void createRejectedWhileSagaInFlight() {
      DataSinkInputDTO input = createValidInput();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertSagaRejected(response);
      assertThat(dataSinkRepository.findByDataSetId(testDataSetId)).isEmpty();
    }

    @Test
    @DisplayName("PUT is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void updateRejectedWhileSagaInFlight() {
      UUID sinkId = createTestEntity();
      String originalConfigurationUrn =
          dataSinkRepository.findById(sinkId).orElseThrow().getConfigurationUrn();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sinkId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertSagaRejected(response);
      assertThat(dataSinkRepository.findById(sinkId).orElseThrow().getConfigurationUrn())
          .isEqualTo(originalConfigurationUrn);
    }

    @Test
    @DisplayName("DELETE is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void deleteRejectedWhileSagaInFlight() {
      UUID sinkId = createTestEntity();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + sinkId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertSagaRejected(response);
      assertThat(dataSinkRepository.findById(sinkId))
          .as("Entity survives the rejected delete")
          .isPresent();
    }

    private void assertSagaRejected(ResponseEntity<ProblemDetail> response) {
      assertRejected(response, HttpStatus.CONFLICT, SAGA_IN_FLIGHT_URN);
      assertThat(response.getBody().getProperties())
          .containsEntry("pendingSagaType", PendingSagaType.UNRELEASE.name());
    }

    @Test
    @DisplayName("PUT, PATCH and DELETE all succeed on a DRAFT dataset")
    void mutationsAcceptedOnDraftParent() {
      UUID sinkId = createTestEntity();

      assertThat(performUpdate(sinkId, createUpdateInput()).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performPatch(sinkId, Map.of("configuration", Map.of())).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performDelete(sinkId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }
}
