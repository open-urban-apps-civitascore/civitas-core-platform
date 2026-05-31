package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;

@DisplayName("Layer Controller Integration Tests")
class LayerControllerIntegrationTest
    extends BaseControllerIntegrationTest<LayerInputDTO, LayerOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private LayerRepository layerRepository;
  @Autowired private DataSetRepository dataSetRepository;

  private UUID testDataSetId;
  private DataSink testDataSink;

  private void ensurePrerequisites() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      DataSet ds = portalData.dataSet();
      testDataSetId = ds.getId();
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
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID")
    void shouldReturn400WhenCreateWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/layers",
              HttpMethod.POST,
              createAuthHeaders(),
              createValidInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should persist layerName and dataSinkId in database after create")
    void shouldPersistEntityInDatabase() {
      LayerInputDTO input = createValidInput();

      ResponseEntity<LayerOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      LayerOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(output.getDataSinkId()).isEqualTo(testDataSink.getId());
      assertThat(output.getLayerName()).isEqualTo(input.getLayerName());
      assertThat(output.getAlternativeStyleIds()).isNotNull().isEmpty();

      var saved = layerRepository.findById(output.getId()).orElseThrow();
      assertThat(saved.getLayerName()).isEqualTo(input.getLayerName());
    }
  }

  @Nested
  @DisplayName("Update Layer Tests")
  class UpdateLayerTests {

    @Test
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID")
    void shouldReturn400WhenUpdateWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/layers/" + UUID.randomUUID(),
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

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
    @DisplayName("Should return 400 when nativeBoundingBox is set but bboxAutoCalculate is true")
    void shouldReturn400WhenNativeBboxSetWithAutoCalculateTrue() {
      LayerInputDTO input = createValidInput();
      input.setBboxAutoCalculate(true);
      input.setNativeBoundingBox(BBOX);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName(
        "Should return 400 when bboxAutoCalculate is false but nativeBoundingBox is missing")
    void shouldReturn400WhenNativeBboxMissingWithAutoCalculateFalse() {
      LayerInputDTO input = createValidInput();
      input.setBboxAutoCalculate(false);
      input.setLatLonBoundingBox(BBOX);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName(
        "Should return 400 when bboxAutoCalculate is false but latLonBoundingBox is missing")
    void shouldReturn400WhenLatLonBboxMissingWithAutoCalculateFalse() {
      LayerInputDTO input = createValidInput();
      input.setBboxAutoCalculate(false);
      input.setNativeBoundingBox(BBOX);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName(
        "Should return 400 when bboxAutoCalculate is false but both bbox fields are missing")
    void shouldReturn400WhenBothBboxFieldsMissingWithAutoCalculateFalse() {
      LayerInputDTO input = createValidInput();
      input.setBboxAutoCalculate(false);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  @DisplayName("Cross-Dataset Access Tests")
  class CrossDatasetTests {

    @Test
    @DisplayName("Should return 404 when accessing Layer from a different dataset")
    void shouldReturn404ForLayerFromDifferentDataset() {
      UUID layerId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/layers/" + layerId,
              HttpMethod.GET,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID on GET by ID")
    void shouldReturn400WhenGetByIdWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/layers/" + UUID.randomUUID(),
              HttpMethod.GET,
              createAuthHeaders(),
              null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID on DELETE")
    void shouldReturn400WhenDeleteWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/layers/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }
  }
}
