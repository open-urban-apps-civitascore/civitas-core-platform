package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;

/**
 * Base class for full-CRUD controller integration tests. Read-only controllers should extend {@link
 * BaseReadOnlyControllerIntegrationTest} directly.
 *
 * @param <I> Input DTO type
 * @param <O> Output DTO type
 */
public abstract class BaseControllerIntegrationTest<I extends BaseInputDTO, O extends BaseOutputDTO>
    extends BaseReadOnlyControllerIntegrationTest<I, O> {

  /** Whether the controller supports PUT/PATCH. Return false to replace 404 tests with 405. */
  protected boolean supportsUpdateAndPatch() {
    return true;
  }

  // ── Write helpers ─────────────────────────────────────────────────────────

  protected ResponseEntity<O> performCreate(I input) {
    return exchange(
        getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input, getOutputTypeReference());
  }

  protected ResponseEntity<O> performUpdate(UUID id, I input) {
    return exchange(
        getEndpointPath() + "/" + id,
        HttpMethod.PUT,
        createAuthHeaders(),
        input,
        getOutputTypeReference());
  }

  protected ResponseEntity<O> performPatch(UUID id, Object patchBody) {
    HttpHeaders headers = createAuthHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return exchange(
        getEndpointPath() + "/" + id,
        HttpMethod.PATCH,
        headers,
        patchBody,
        getOutputTypeReference());
  }

  protected ResponseEntity<Void> performDelete(UUID id) {
    HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());
    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.DELETE, request, Void.class);
  }

  @Override
  protected UUID createTestEntity() {
    ResponseEntity<O> response = performCreate(createValidInput());
    if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
      return getIdFromOutput(response.getBody());
    }
    throw new IllegalStateException(
        "Failed to create test entity. Status: "
            + response.getStatusCode()
            + ", Body: "
            + response.getBody());
  }

  // ── Write tests ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("Should return BAD_REQUEST when PATCH produces an invalid entity")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    if (!supportsUpdateAndPatch()) return;
    UUID id = createTestEntity();
    Map<String, Object> patchBody =
        objectMapper.convertValue(createInvalidInput(), new TypeReference<>() {});

    ResponseEntity<O> response = performPatch(id, patchBody);

    assertThat(response.getStatusCode())
        .as("PATCH producing an invalid entity should return BAD_REQUEST")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Should return 404 when updating non-existent entity")
  void shouldReturn404WhenUpdatingNonExistentEntity() {
    if (!supportsUpdateAndPatch()) return;
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + UUID.randomUUID(),
            HttpMethod.PUT,
            createAuthHeaders(),
            createUpdateInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 404 when patching non-existent entity")
  void shouldReturn404WhenPatchingNonExistentEntity() {
    if (!supportsUpdateAndPatch()) return;
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + UUID.randomUUID(),
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of("name", "patched"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should reject PUT when operation is not supported")
  void shouldRejectUnsupportedPut() {
    if (supportsUpdateAndPatch()) return;
    UUID id = createTestEntity();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + id, HttpMethod.PUT, createAuthHeaders(), createValidInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("Should reject PATCH when operation is not supported")
  void shouldRejectUnsupportedPatch() {
    if (supportsUpdateAndPatch()) return;
    UUID id = createTestEntity();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + id,
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of("name", "patched"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("Should return 404 when deleting non-existent entity")
  void shouldReturn404WhenDeletingNonExistentEntity() {
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + UUID.randomUUID(),
            HttpMethod.DELETE,
            createAuthHeaders(),
            null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 400 when creating with invalid input")
  void shouldReturn400WhenCreatingWithInvalidInput() {
    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath(), HttpMethod.POST, createAuthHeaders(), createInvalidInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Should return 201 with Location header on create")
  void shouldReturn201WithLocationHeaderOnCreate() {
    ResponseEntity<O> response = performCreate(createValidInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    UUID id = getIdFromOutput(response.getBody());
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString()).contains(id.toString());
  }

  @Test
  @DisplayName("Should return 204 on successful delete")
  void shouldReturn204OnSuccessfulDelete() {
    UUID id = createTestEntity();

    ResponseEntity<Void> response = performDelete(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
