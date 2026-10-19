package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;

/**
 * Base class for integration tests on controllers nested under {@code /datasets/{dataSetId}/...}.
 *
 * <p>Subclasses inherit the ownership cases rather than restating them, so a sub-entity added later
 * is covered on the commit that wires up its suite.
 *
 * @param <I> the input DTO type
 * @param <O> the output DTO type
 */
public abstract class DataSetSubEntityControllerIntegrationTest<
        I extends DataSetOwnedInputDTO, O extends BaseOutputDTO>
    extends BaseControllerIntegrationTest<I, O> {

  @Autowired protected PortalTestDataFactory portalData;

  /**
   * The path segment naming this sub-entity collection, e.g. {@code styles} for {@code
   * /datasets/{dataSetId}/styles}.
   */
  protected abstract String getSubEntityPathSegment();

  /** Whether the controller serves PATCH. Layers and Styles answer 405 instead. */
  protected boolean supportsPatch() {
    return true;
  }

  protected void assertRejected(
      ResponseEntity<ProblemDetail> response, HttpStatus status, String urn) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType()).hasToString(urn);
  }

  private String pathUnderDataSet(UUID dataSetId, UUID entityId) {
    return "/datasets/" + dataSetId + "/" + getSubEntityPathSegment() + "/" + entityId;
  }

  private ResponseEntity<ProblemDetail> requestUnderForeignDataSet(
      HttpMethod method, UUID entityId, Object body) {
    UUID foreignDataSetId = portalData.dataSet().getId();
    return exchangeForProblem(
        pathUnderDataSet(foreignDataSetId, entityId), method, createAuthHeaders(), body);
  }

  @Nested
  @DisplayName("Parent ownership")
  class ParentOwnershipContractTests {

    @Test
    @DisplayName("GET answers 404 for an entity owned by another dataset")
    void getAnswers404ForForeignEntity() {
      ResponseEntity<ProblemDetail> response =
          requestUnderForeignDataSet(HttpMethod.GET, createTestEntity(), null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT answers 404 for an entity owned by another dataset")
    void putAnswers404ForForeignEntity() {
      ResponseEntity<ProblemDetail> response =
          requestUnderForeignDataSet(HttpMethod.PUT, createTestEntity(), createUpdateInput());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH answers 404 for an entity owned by another dataset")
    void patchAnswers404ForForeignEntity() {
      assumeTrue(supportsPatch());
      Map<String, Object> patchBody =
          objectMapper.convertValue(createUpdateInput(), new TypeReference<>() {});

      ResponseEntity<ProblemDetail> response =
          requestUnderForeignDataSet(HttpMethod.PATCH, createTestEntity(), patchBody);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE answers 404 for an entity owned by another dataset")
    void deleteAnswers404ForForeignEntity() {
      UUID entityId = createTestEntity();

      ResponseEntity<ProblemDetail> response =
          requestUnderForeignDataSet(HttpMethod.DELETE, entityId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(
              exchangeForProblem(
                      getEndpointPath() + "/" + entityId, HttpMethod.GET, createAuthHeaders(), null)
                  .getStatusCode())
          .as("A rejected cross-dataset DELETE must leave the entity in place")
          .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("A malformed dataSetId answers 400 on GET, PUT and DELETE")
    void malformedDataSetIdAnswers400() {
      assertMalformedDataSetIdAnswers400(HttpMethod.GET, null);
      assertMalformedDataSetIdAnswers400(HttpMethod.PUT, createUpdateInput());
      assertMalformedDataSetIdAnswers400(HttpMethod.DELETE, null);
    }

    @Test
    @DisplayName("A malformed dataSetId answers 400 on PATCH")
    void malformedDataSetIdAnswers400OnPatch() {
      assumeTrue(supportsPatch());

      assertMalformedDataSetIdAnswers400(
          HttpMethod.PATCH,
          objectMapper.convertValue(
              createUpdateInput(), new TypeReference<Map<String, Object>>() {}));
    }

    @Test
    @DisplayName("A malformed dataSetId answers 400 on create")
    void malformedDataSetIdAnswers400OnCreate() {
      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              "/datasets/not-a-valid-uuid/" + getSubEntityPathSegment(),
              HttpMethod.POST,
              createAuthHeaders(),
              createValidInput());

      assertMalformedDataSetIdProblem(response);
    }
  }

  private void assertMalformedDataSetIdAnswers400(HttpMethod method, Object body) {
    assertMalformedDataSetIdProblem(
        exchangeForProblem(
            "/datasets/not-a-valid-uuid/" + getSubEntityPathSegment() + "/" + UUID.randomUUID(),
            method,
            createAuthHeaders(),
            body));
  }

  private void assertMalformedDataSetIdProblem(ResponseEntity<ProblemDetail> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDetail())
        .isEqualTo("Missing or invalid dataSetId in path variables");
  }
}
