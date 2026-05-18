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
  protected boolean supportsUpdateAndPatch() {
    return true;
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
  }
}
