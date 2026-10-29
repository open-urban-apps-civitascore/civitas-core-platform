package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.model.output.LayerOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.RestPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;

@DisplayName("Layer Controller Integration Tests")
class LayerControllerIntegrationTest
    extends DataSetSubEntityControllerIntegrationTest<LayerInputDTO, LayerOutputDTO> {

  @Override
  protected String getSubEntityPathSegment() {
    return "layers";
  }

  @Override
  protected boolean supportsPatch() {
    return false;
  }

  @Autowired private LayerRepository layerRepository;
  @Autowired private DataSetRepository dataSetRepository;

  private UUID testDataSetId;
  private DataSink testDataSink;

  private void ensurePrerequisites() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      DataSet ds = portalData.dataSet();
      testDataSetId = ds.getId();
      portalData.namedApi(ds, ApiStandard.OWS);
      Pipeline pipeline = portalData.pipeline(ds);
      testDataSink = portalData.dataSink(ds, pipeline);
    }
  }

  @Override
  protected String getEndpointPath() {
    ensurePrerequisites();
    return "/datasets/" + testDataSetId + "/layers";
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
    testDataSetId = null;
    testDataSink = null;
  }

  @Override
  protected LayerInputDTO createValidInput() {
    ensurePrerequisites();
    LayerInputDTO input = new LayerInputDTO();
    input.setDataSinkId(testDataSink.getId());
    input.setLayerName("test-layer-" + System.currentTimeMillis());
    return input;
  }

  @Override
  protected LayerInputDTO createInvalidInput() {
    LayerInputDTO input = new LayerInputDTO();
    // missing required dataSinkId — triggers @NotNull
    input.setLayerName("invalid-layer");
    return input;
  }

  @Override
  protected LayerInputDTO createUpdateInput() {
    ensurePrerequisites();
    LayerInputDTO input = new LayerInputDTO();
    input.setDataSinkId(testDataSink.getId());
    input.setLayerName("updated-layer-" + System.currentTimeMillis());
    return input;
  }

  @Override
  protected ParameterizedTypeReference<LayerOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<LayerOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(LayerOutputDTO output) {
    return output.getId();
  }

  @Override
  @Test
  @DisplayName("PATCH is not supported — always returns 405")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    Map<String, Object> patchBody =
        objectMapper.convertValue(createInvalidInput(), new TypeReference<>() {});
    ResponseEntity<LayerOutputDTO> response = performPatch(id, patchBody);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Override
  @Test
  @DisplayName("PATCH is not supported — always returns 405")
  void shouldReturn404WhenPatchingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId,
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of("layerName", "x"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Nested
  @DisplayName("Create Layer Tests")
  class CreateLayerTests {

    @Test
    @DisplayName(
        "Should persist layerName, dataSinkId and alternativeStyleIds in database after create")
    void shouldPersistEntityInDatabase() {
      ensurePrerequisites();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Style altStyle = portalData.style(dataSet);

      LayerInputDTO input = createValidInput();
      input.setAlternativeStyleIds(List.of(altStyle.getId()));

      ResponseEntity<LayerOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      LayerOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(output.getDataSinkId()).isEqualTo(testDataSink.getId());
      assertThat(output.getLayerName()).isEqualTo(input.getLayerName());
      assertThat(output.getAlternativeStyleIds()).containsExactly(altStyle.getId());

      var saved = layerRepository.findById(output.getId()).orElseThrow();
      assertThat(saved.getLayerName()).isEqualTo(input.getLayerName());
    }
  }

  @Nested
  @DisplayName("Update Layer Tests")
  class UpdateLayerTests {

    @Test
    @DisplayName("Should persist updated layerName in database after PUT")
    void shouldPersistUpdatedEntityInDatabase() {
      UUID id = createTestEntity();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Style altStyle1 = portalData.style(dataSet);
      Style altStyle2 = portalData.style(dataSet);

      LayerInputDTO update = createUpdateInput();
      update.setAlternativeStyleIds(List.of(altStyle1.getId(), altStyle2.getId()));

      ResponseEntity<LayerOutputDTO> response = performUpdate(id, update);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      LayerOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getLayerName()).isEqualTo(update.getLayerName());
      assertThat(output.getAlternativeStyleIds())
          .containsExactlyInAnyOrder(altStyle1.getId(), altStyle2.getId());

      var saved = layerRepository.findById(id).orElseThrow();
      assertThat(saved.getLayerName()).isEqualTo(update.getLayerName());
    }

    @Test
    @DisplayName("alternativeStyleIds round-trip: POST → GET → PUT → GET")
    void shouldRoundTripAlternativeStyleIds() {
      ensurePrerequisites();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      Style altStyle1 = portalData.style(dataSet);
      Style altStyle2 = portalData.style(dataSet);

      LayerInputDTO create = createValidInput();
      create.setAlternativeStyleIds(List.of(altStyle1.getId(), altStyle2.getId()));
      LayerOutputDTO created = performCreate(create).getBody();
      assertThat(created).isNotNull();
      UUID id = getIdFromOutput(created);

      ResponseEntity<LayerOutputDTO> afterCreate = performGetById(id);
      assertThat(afterCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(afterCreate.getBody()).isNotNull();
      assertThat(afterCreate.getBody().getAlternativeStyleIds())
          .containsExactlyInAnyOrder(altStyle1.getId(), altStyle2.getId());

      LayerInputDTO update = createUpdateInput();
      update.setAlternativeStyleIds(List.of(altStyle1.getId()));
      performUpdate(id, update);

      ResponseEntity<LayerOutputDTO> afterUpdate = performGetById(id);
      assertThat(afterUpdate.getBody()).isNotNull();
      assertThat(afterUpdate.getBody().getAlternativeStyleIds()).containsExactly(altStyle1.getId());
    }
  }

  @Nested
  @DisplayName("Uniqueness Tests")
  class UniquenessTests {

    @Test
    @DisplayName(
        "Should return 409 when creating a Layer with a layerName that already exists in the dataSink")
    void shouldReturn409OnDuplicateLayerName() {
      LayerInputDTO first = createValidInput();
      performCreate(first);

      LayerInputDTO duplicate = createValidInput();
      duplicate.setLayerName(first.getLayerName());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Style Ownership Tests")
  class StyleOwnershipTests {

    @Test
    @DisplayName("Should return 400 when defaultStyleId belongs to a different dataset")
    void shouldReturn400WhenDefaultStyleFromWrongDataset() {
      DataSet otherDataSet = portalData.dataSet();
      Style foreignStyle = portalData.style(otherDataSet);

      LayerInputDTO input = createValidInput();
      input.setDefaultStyleId(foreignStyle.getId());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Bbox Validation Tests")
  class BboxValidationTests {

    private static final Map<String, Object> BBOX =
        Map.of("minx", -180, "miny", -90, "maxx", 180, "maxy", 90);

    @Test
    @DisplayName("Should return 400 when only latLonBoundingBox is set")
    void shouldReturn400WhenOnlyLatLonBboxSet() {
      LayerInputDTO input = createValidInput();
      input.setLatLonBoundingBox(BBOX);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should return 400 when only nativeBoundingBox is set")
    void shouldReturn400WhenOnlyNativeBboxSet() {
      LayerInputDTO input = createValidInput();
      input.setNativeBoundingBox(BBOX);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("CQL Filter Validation Tests")
  class CqlFilterValidationTests {

    private static final String VALIDATION_FAILED_URN = "urn:civitas:error:VALIDATION_FAILED";
    private static final String INCOMPLETE =
        "CQL filter is incomplete: a value, condition or closing bracket is missing at the end";

    @Test
    @DisplayName("Should save a layer whose CQL filter is valid ECQL")
    void shouldSaveValidFilter() {
      LayerInputDTO input = createValidInput();
      input.setCqlFilter("status = 'frei' AND kapazitaet > 50");

      ResponseEntity<LayerOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(layerRepository.findById(response.getBody().getId()).orElseThrow().getCqlFilter())
          .isEqualTo("status = 'frei' AND kapazitaet > 50");
    }

    @Test
    @DisplayName("Should save a layer with an empty CQL filter")
    void shouldSaveEmptyFilter() {
      LayerInputDTO input = createValidInput();
      input.setCqlFilter("");

      assertThat(performCreate(input).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    static Stream<Arguments> syntaxErrors() {
      return Stream.of(
          arguments("status = 'frei", "CQL filter is not valid ECQL near line 1, column 15"),
          arguments("kapazitaet >", "CQL filter is not valid ECQL near line 1, column 12"),
          arguments("(status = 'frei' OR kapazitaet > 50", INCOMPLETE));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("syntaxErrors")
    @DisplayName("POST with a CQL syntax error returns 400 at the filter and saves nothing")
    void createRejectsSyntaxError(String cqlFilter, String expectedMessage) {
      LayerInputDTO input = createValidInput();
      input.setCqlFilter(cqlFilter);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString(VALIDATION_FAILED_URN);
      assertThat(response.getBody().getProperties().get("fieldErrors"))
          .isEqualTo(List.of(Map.of("field", "cqlFilter", "message", expectedMessage)));
      assertThat(layerRepository.existsByDataSetId(testDataSetId)).isFalse();
    }

    @Test
    @DisplayName("PUT with a CQL syntax error returns 400 at the filter and keeps the stored one")
    void updateRejectsSyntaxError() {
      LayerInputDTO create = createValidInput();
      create.setCqlFilter("kapazitaet > 50");
      UUID id = performCreate(create).getBody().getId();
      LayerInputDTO update = createUpdateInput();
      update.setCqlFilter("kapazitaet >");

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + id, HttpMethod.PUT, createAuthHeaders(), update);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getType()).hasToString(VALIDATION_FAILED_URN);
      assertThat(response.getBody().getProperties().get("fieldErrors"))
          .isEqualTo(
              List.of(
                  Map.of(
                      "field",
                      "cqlFilter",
                      "message",
                      "CQL filter is not valid ECQL near line 1, column 12")));
      assertThat(layerRepository.findById(id).orElseThrow().getCqlFilter())
          .isEqualTo("kapazitaet > 50");
    }
  }

  @Nested
  @DisplayName("Parent DataSet Mutation Guard")
  class MutationGuardTests {

    private static final String NOT_EDITABLE_URN = "urn:civitas:error:DATASET_NOT_EDITABLE";
    private static final String SAGA_IN_FLIGHT_URN = "urn:civitas:error:SAGA_IN_FLIGHT";

    private void setParentStatus(DataSetStatus status) {
      ensurePrerequisites();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setDataSetStatus(status);
      dataSetRepository.save(dataSet);
    }

    private void setParentPendingSaga(PendingSagaType pendingSagaType) {
      ensurePrerequisites();
      DataSet dataSet = dataSetRepository.findById(testDataSetId).orElseThrow();
      dataSet.setPendingSagaType(pendingSagaType);
      dataSetRepository.save(dataSet);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("POST is rejected when the parent dataset is not DRAFT")
    void createRejectedWhenParentNotDraft(DataSetStatus status) {
      LayerInputDTO input = createValidInput();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(layerRepository.existsByDataSetId(testDataSetId)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PUT is rejected when the parent dataset is not DRAFT")
    void updateRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID layerId = createTestEntity();
      String originalName = layerRepository.findById(layerId).orElseThrow().getLayerName();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + layerId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(layerRepository.findById(layerId).orElseThrow().getLayerName())
          .isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("DELETE is rejected when the parent dataset is not DRAFT")
    void deleteRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID layerId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + layerId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(layerRepository.findById(layerId)).isPresent();
    }

    @Test
    @DisplayName("POST is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void createRejectedWhileSagaInFlight() {
      LayerInputDTO input = createValidInput();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertSagaRejected(response);
      assertThat(layerRepository.existsByDataSetId(testDataSetId)).isFalse();
    }

    @Test
    @DisplayName("PUT is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void updateRejectedWhileSagaInFlight() {
      UUID layerId = createTestEntity();
      String originalName = layerRepository.findById(layerId).orElseThrow().getLayerName();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + layerId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertSagaRejected(response);
      assertThat(layerRepository.findById(layerId).orElseThrow().getLayerName())
          .isEqualTo(originalName);
    }

    @Test
    @DisplayName("DELETE is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void deleteRejectedWhileSagaInFlight() {
      UUID layerId = createTestEntity();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + layerId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertSagaRejected(response);
      assertThat(layerRepository.findById(layerId))
          .as("Entity survives the rejected delete")
          .isPresent();
    }

    private void assertSagaRejected(ResponseEntity<ProblemDetail> response) {
      assertRejected(response, HttpStatus.CONFLICT, SAGA_IN_FLIGHT_URN);
      assertThat(response.getBody().getProperties())
          .containsEntry("pendingSagaType", PendingSagaType.UNRELEASE.name());
    }

    @Test
    @DisplayName("PUT and DELETE both succeed on a DRAFT dataset")
    void mutationsAcceptedOnDraftParent() {
      UUID layerId = createTestEntity();

      assertThat(performUpdate(layerId, createUpdateInput()).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performDelete(layerId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }
}
