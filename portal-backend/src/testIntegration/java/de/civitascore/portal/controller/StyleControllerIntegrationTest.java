package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;

@DisplayName("Style Controller Integration Tests")
class StyleControllerIntegrationTest
    extends DataSetSubEntityControllerIntegrationTest<StyleInputDTO, StyleOutputDTO> {

  @Override
  protected String getSubEntityPathSegment() {
    return "styles";
  }

  @Override
  protected boolean supportsPatch() {
    return false;
  }

  @Autowired private StyleRepository styleRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private LayerRepository layerRepository;

  private UUID testDataSetId;

  private void ensureTestDataSet() {
    if (testDataSetId == null || dataSetRepository.findById(testDataSetId).isEmpty()) {
      testDataSetId = portalData.dataSet().getId();
    }
  }

  @Override
  protected String getEndpointPath() {
    ensureTestDataSet();
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

    @Test
    @DisplayName("Should ignore a foreign dataSetId in the create body and use the path dataset")
    void shouldIgnoreForeignDataSetIdOnCreate() {
      DataSet otherDataSet = portalData.dataSet();

      Map<String, Object> body =
          Map.of(
              "name",
              "style_with_foreign_dataset_id",
              "sldContent",
              "<StyledLayerDescriptor version=\"1.0.0\"/>",
              "dataSetId",
              otherDataSet.getId().toString());

      ResponseEntity<StyleOutputDTO> response =
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

      Style saved = styleRepository.findById(response.getBody().getId()).orElseThrow();
      assertThat(saved.getDataSet().getId())
          .as("POST must use the dataset from the path, not the one named in the body")
          .isEqualTo(testDataSetId)
          .isNotEqualTo(otherDataSet.getId());
    }
  }

  @Nested
  @DisplayName("Update Style Tests")
  class UpdateStyleTests {

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

    @Test
    @DisplayName(
        "Should return 400 locating the fault when creating a Style whose SLD is malformed")
    void shouldReturn400OnMalformedSldCreate() {
      StyleInputDTO input = createValidInput();
      input.setSldContent("<?xml version=\"1.0\"?><StyledLayerDescriptor version=\"1.0.0\">");

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getDetail())
          .contains("sldContent", "not well-formed")
          .containsPattern("line \\d+, column \\d+");
      assertThat(styleRepository.findAll()).isEmpty();
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

  @Nested
  @DisplayName("Parent DataSet Mutation Guard")
  class MutationGuardTests {

    private static final String NOT_EDITABLE_URN = "urn:civitas:error:DATASET_NOT_EDITABLE";
    private static final String SAGA_IN_FLIGHT_URN = "urn:civitas:error:SAGA_IN_FLIGHT";

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
      StyleInputDTO input = createValidInput();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(styleRepository.count()).as("No style was created").isZero();
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("PUT is rejected when the parent dataset is not DRAFT")
    void updateRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID styleId = createTestEntity();
      String originalName = styleRepository.findById(styleId).orElseThrow().getName();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + styleId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(styleRepository.findById(styleId).orElseThrow().getName()).isEqualTo(originalName);
    }

    @ParameterizedTest
    @EnumSource(value = DataSetStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
    @DisplayName("DELETE is rejected when the parent dataset is not DRAFT")
    void deleteRejectedWhenParentNotDraft(DataSetStatus status) {
      UUID styleId = createTestEntity();
      setParentStatus(status);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + styleId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertRejected(response, HttpStatus.BAD_REQUEST, NOT_EDITABLE_URN);
      assertThat(styleRepository.findById(styleId)).isPresent();
    }

    @Test
    @DisplayName("POST is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void createRejectedWhileSagaInFlight() {
      StyleInputDTO input = createValidInput();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input);

      assertSagaRejected(response);
      assertThat(styleRepository.count()).as("No style was created").isZero();
    }

    @Test
    @DisplayName("PUT is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void updateRejectedWhileSagaInFlight() {
      UUID styleId = createTestEntity();
      String originalName = styleRepository.findById(styleId).orElseThrow().getName();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + styleId,
              HttpMethod.PUT,
              createAuthHeaders(),
              createUpdateInput());

      assertSagaRejected(response);
      assertThat(styleRepository.findById(styleId).orElseThrow().getName()).isEqualTo(originalName);
    }

    @Test
    @DisplayName("DELETE is rejected with 409 while a saga is in flight on a DRAFT dataset")
    void deleteRejectedWhileSagaInFlight() {
      UUID styleId = createTestEntity();
      setParentPendingSaga(PendingSagaType.UNRELEASE);

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              getEndpointPath() + "/" + styleId, HttpMethod.DELETE, createAuthHeaders(), null);

      assertSagaRejected(response);
      assertThat(styleRepository.findById(styleId))
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
      UUID styleId = createTestEntity();

      assertThat(performUpdate(styleId, createUpdateInput()).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(performDelete(styleId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }
}
