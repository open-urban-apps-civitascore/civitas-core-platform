package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.model.output.StyleOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
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
import tools.jackson.core.type.TypeReference;

@DisplayName("Style Controller Integration Tests")
class StyleControllerIntegrationTest
    extends BaseControllerIntegrationTest<StyleInputDTO, StyleOutputDTO> {

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private StyleRepository styleRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private LayerRepository layerRepository;

  private UUID testDataSetId;

  @Override
  protected String getEndpointPath() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      testDataSetId = portalData.dataSet().getId();
    }
    return "/datasets/" + testDataSetId + "/styles";
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
    testDataSetId = null;
  }

  @Override
  protected StyleInputDTO createValidInput() {
    StyleInputDTO input = new StyleInputDTO();
    input.setName("test-style-" + System.currentTimeMillis());
    input.setSldContent("<StyledLayerDescriptor version=\"1.0.0\"/>");
    return input;
  }

  @Override
  protected StyleInputDTO createInvalidInput() {
    StyleInputDTO input = new StyleInputDTO();
    // missing required name — triggers @NotBlank
    input.setSldContent("<StyledLayerDescriptor/>");
    return input;
  }

  @Override
  protected StyleInputDTO createUpdateInput() {
    StyleInputDTO input = new StyleInputDTO();
    input.setName("updated-style-" + System.currentTimeMillis());
    input.setSldContent("<StyledLayerDescriptor version=\"1.1.0\"/>");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<StyleOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<StyleOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(StyleOutputDTO output) {
    return output.getId();
  }

  @Override
  @Test
  @DisplayName("PATCH is not supported — always returns 405")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    Map<String, Object> patchBody =
        objectMapper.convertValue(createInvalidInput(), new TypeReference<>() {});
    ResponseEntity<StyleOutputDTO> response = performPatch(id, patchBody);
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
            Map.of("name", "x"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Nested
  @DisplayName("Create Style Tests")
  class CreateStyleTests {

    @Test
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID")
    void shouldReturn400WhenCreateWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/styles",
              HttpMethod.POST,
              createAuthHeaders(),
              createValidInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should persist name and sldContent in database after create")
    void shouldPersistEntityInDatabase() {
      StyleInputDTO input = createValidInput();

      ResponseEntity<StyleOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      StyleOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getDataSetId()).isEqualTo(testDataSetId);
      assertThat(output.getName()).isEqualTo(input.getName());
      assertThat(output.getSldContent()).isEqualTo(input.getSldContent());

      Style saved = styleRepository.findById(output.getId()).orElseThrow();
      assertThat(saved.getName()).isEqualTo(input.getName());
      assertThat(saved.getSldContent()).isEqualTo(input.getSldContent());
    }
  }

  @Nested
  @DisplayName("Update Style Tests")
  class UpdateStyleTests {

    @Test
    @DisplayName("Should return 400 when dataSetId in path is not a valid UUID")
    void shouldReturn400WhenUpdateWithInvalidDataSetId() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/styles/" + UUID.randomUUID(),
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }

    @Test
    @DisplayName("Should persist updated name and sldContent in database after PUT")
    void shouldPersistUpdatedEntityInDatabase() {
      UUID id = createTestEntity();
      StyleInputDTO update = createUpdateInput();

      ResponseEntity<StyleOutputDTO> response = performUpdate(id, update);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      StyleOutputDTO output = response.getBody();
      assertThat(output).isNotNull();
      assertThat(output.getName()).isEqualTo(update.getName());
      assertThat(output.getSldContent()).isEqualTo(update.getSldContent());

      Style saved = styleRepository.findById(id).orElseThrow();
      assertThat(saved.getName()).isEqualTo(update.getName());
      assertThat(saved.getSldContent()).isEqualTo(update.getSldContent());
    }
  }

  @Nested
  @DisplayName("Cross-Dataset Access Tests")
  class CrossDatasetTests {

    @Test
    @DisplayName("Should return 404 when accessing Style from a different dataset")
    void shouldReturn404ForStyleFromDifferentDataset() {
      UUID styleId = createTestEntity();
      DataSet otherDataSet = portalData.dataSet();

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/" + otherDataSet.getId() + "/styles/" + styleId,
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
              "/datasets/not-a-valid-uuid/styles/" + UUID.randomUUID(),
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
              "/datasets/not-a-valid-uuid/styles/" + UUID.randomUUID(),
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .isEqualTo("Missing or invalid dataSetId in path variables");
    }
  }

  @Nested
  @DisplayName("Uniqueness Tests")
  class UniquenessTests {

    @Test
    @DisplayName(
        "Should return 409 when creating a Style with a name that already exists in the dataset")
    void shouldReturn409OnDuplicateName() {
      StyleInputDTO first = createValidInput();
      performCreate(first);

      StyleInputDTO duplicate = createValidInput();
      duplicate.setName(first.getName());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), duplicate);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("SLD Content Tests")
  class SldContentTests {

    @Test
    @DisplayName("Should return 400 naming DOCTYPE when creating a Style whose SLD declares one")
    void shouldReturn400OnDoctypeCreate() {
      StyleInputDTO input = createValidInput();
      input.setSldContent(
          "<?xml version=\"1.0\"?><!DOCTYPE StyledLayerDescriptor>"
              + "<StyledLayerDescriptor version=\"1.0.0\"/>");

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail()).contains("DOCTYPE", "sldContent");
      assertThat(styleRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Should return 400 when updating a Style to an SLD that declares a DOCTYPE")
    void shouldReturn400OnDoctypeUpdate() {
      StyleOutputDTO created = performCreate(createValidInput()).getBody();
      assertThat(created).isNotNull();

      StyleInputDTO update = createUpdateInput();
      update.setSldContent(
          "<?xml version=\"1.0\"?><!DOCTYPE StyledLayerDescriptor [ <!ENTITY x \"y\"> ]>"
              + "<StyledLayerDescriptor version=\"1.0.0\"/>");

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + created.getId(),
              HttpMethod.PUT,
              createAuthHeaders(),
              update);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(styleRepository.findById(created.getId()))
          .get()
          .extracting(Style::getSldContent)
          .isEqualTo("<StyledLayerDescriptor version=\"1.0.0\"/>");
    }

    @Test
    @DisplayName("Should accept an SLD carrying a DOCTYPE only inside a comment, as GeoServer does")
    void shouldAcceptDoctypeInsideComment() {
      StyleInputDTO input = createValidInput();
      input.setSldContent(
          "<?xml version=\"1.0\"?><!-- <!DOCTYPE StyledLayerDescriptor> -->"
              + "<StyledLayerDescriptor version=\"1.0.0\"/>");

      assertThat(performCreate(input).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
  }

  @Nested
  @DisplayName("Delete Protection Tests")
  class DeleteProtectionTests {

    @Test
    @DisplayName("Should return 409 when deleting a Style that is a Layer's default style")
    void shouldReturn409WhenStyleReferencedByLayerAsDefault() {
      getEndpointPath(); // ensures testDataSetId is initialized
      DataSet ds = dataSetRepository.findById(testDataSetId).orElseThrow();
      Style style = portalData.style(ds);
      DataSink dataSink = portalData.dataSink(ds, portalData.pipeline(ds));
      Layer layer = portalData.layer(ds, dataSink);
      layer.setDefaultStyle(style);
      layerRepository.save(layer);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + style.getId(),
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should return 409 when deleting a Style that is a Layer's alternative style")
    void shouldReturn409WhenStyleReferencedByLayerAsAlternative() {
      getEndpointPath(); // ensures testDataSetId is initialized
      DataSet ds = dataSetRepository.findById(testDataSetId).orElseThrow();
      Style style = portalData.style(ds);
      DataSink dataSink = portalData.dataSink(ds, portalData.pipeline(ds));
      Layer layer = portalData.layer(ds, dataSink);
      layer.getAlternativeStyles().add(style);
      layerRepository.save(layer);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + style.getId(),
              HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }
}
