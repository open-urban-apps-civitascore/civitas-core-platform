package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Base class for controller integration tests providing common test utilities.
 *
 * @param <I> Input DTO type
 * @param <O> Output DTO type
 */
public abstract class BaseControllerIntegrationTest<I extends BaseInputDTO, O extends BaseOutputDTO>
    extends BaseKeycloakIntegrationTest {

  @Autowired protected ObjectMapper objectMapper;

  protected abstract String getEndpointPath();

  protected abstract I createValidInput();

  protected abstract I createInvalidInput();

  protected abstract I createUpdateInput();

  protected abstract ParameterizedTypeReference<O> getOutputTypeReference();

  protected abstract ParameterizedTypeReference<RestPage<O>> getPageTypeReference();

  protected abstract UUID getIdFromOutput(O output);

  // Optional hook for domain-specific cleanup (files, stubs, etc.)
  protected void performAdditionalCleanup() {}

  @AfterEach
  void cleanupAfterTest() {
    performAdditionalCleanup();
  }

  protected HttpHeaders createAuthHeaders() {
    return createAuthHeaders(getValidAccessToken());
  }

  protected HttpHeaders createAuthHeaders(String username, String password) {
    return createAuthHeaders(getValidAccessToken(username, password));
  }

  private HttpHeaders createAuthHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(MediaType.parseMediaTypes("application/json"));
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }

  protected URI buildUri(String path, Map<String, ?> queryParams) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
    if (queryParams != null && !queryParams.isEmpty()) {
      MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
      queryParams.forEach((k, v) -> map.add(k, v == null ? "" : String.valueOf(v)));
      builder.queryParams(map);
    }
    return builder.build(true).toUri();
  }

  protected <T> ResponseEntity<T> exchange(
      String path,
      HttpMethod method,
      HttpHeaders headers,
      Object body,
      ParameterizedTypeReference<T> type) {
    return exchangeRaw(
        restTemplate.exchange(path, method, toHttpEntity(headers, body), String.class), type);
  }

  protected <T> ResponseEntity<T> exchange(
      URI uri, HttpMethod method, HttpHeaders headers, ParameterizedTypeReference<T> type) {
    return exchangeRaw(
        restTemplate.exchange(uri, method, new HttpEntity<>(headers), String.class), type);
  }

  private <T> ResponseEntity<T> exchangeRaw(
      ResponseEntity<String> raw, ParameterizedTypeReference<T> type) {
    if (raw.getStatusCode().is2xxSuccessful() && raw.getBody() != null) {
      try {
        T parsed =
            objectMapper.readValue(raw.getBody(), objectMapper.constructType(type.getType()));
        return ResponseEntity.status(raw.getStatusCode()).headers(raw.getHeaders()).body(parsed);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to deserialize response body", e);
      }
    }
    return ResponseEntity.status(raw.getStatusCode()).headers(raw.getHeaders()).build();
  }

  /**
   * Exchanges a request and returns the error response as a ProblemDetail. Use this for tests that
   * need to inspect error response bodies (type URN, detail message, custom properties).
   */
  protected ResponseEntity<ProblemDetail> exchangeForProblem(
      String path, HttpMethod method, HttpHeaders headers, Object body) {
    HttpEntity<?> request = toHttpEntity(headers, body);
    ResponseEntity<String> raw = restTemplate.exchange(path, method, request, String.class);
    if (raw.getBody() != null) {
      try {
        ProblemDetail parsed = objectMapper.readValue(raw.getBody(), ProblemDetail.class);
        return ResponseEntity.status(raw.getStatusCode()).headers(raw.getHeaders()).body(parsed);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to deserialize ProblemDetail response", e);
      }
    }
    return ResponseEntity.status(raw.getStatusCode()).headers(raw.getHeaders()).build();
  }

  private HttpEntity<?> toHttpEntity(HttpHeaders headers, Object body) {
    return (body == null) ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
  }

  protected ResponseEntity<O> performCreate(I input) {
    return exchange(
        getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input, getOutputTypeReference());
  }

  protected ResponseEntity<O> performGetById(UUID id) {
    String url = getEndpointPath() + "/" + id;
    return exchange(url, HttpMethod.GET, createAuthHeaders(), null, getOutputTypeReference());
  }

  protected ResponseEntity<RestPage<O>> performGetAll() {
    return performGetAll(Map.of());
  }

  protected ResponseEntity<RestPage<O>> performGetAll(Map<String, ?> queryParams) {
    URI uri = buildUri(getEndpointPath(), queryParams);
    return exchange(uri, HttpMethod.GET, createAuthHeaders(), getPageTypeReference());
  }

  protected ResponseEntity<O> performUpdate(UUID id, I input) {
    String url = getEndpointPath() + "/" + id;
    return exchange(url, HttpMethod.PUT, createAuthHeaders(), input, getOutputTypeReference());
  }

  protected ResponseEntity<O> performPatch(UUID id, Object patchBody) {
    String url = getEndpointPath() + "/" + id;
    HttpHeaders headers = createAuthHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return exchange(url, HttpMethod.PATCH, headers, patchBody, getOutputTypeReference());
  }

  protected ResponseEntity<Void> performDelete(UUID id) {
    String url = getEndpointPath() + "/" + id;
    HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());
    return restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
  }

  protected ResponseEntity<String> performRequestWithoutAuth(String path, HttpMethod method) {
    return restTemplate.exchange(getEndpointPath() + path, method, HttpEntity.EMPTY, String.class);
  }

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

  protected HttpHeaders withHeaders(Consumer<HttpHeaders> customizer) {
    HttpHeaders headers = createAuthHeaders();
    customizer.accept(headers);
    return headers;
  }

  @Test
  @DisplayName("Should return BAD_REQUEST when PATCH produces an invalid entity")
  void shouldRejectPatchThatResultsInInvalidEntity() {
    UUID id = createTestEntity();
    Map<String, Object> patchBody =
        objectMapper.convertValue(createInvalidInput(), new TypeReference<>() {});

    ResponseEntity<O> response = performPatch(id, patchBody);

    assertThat(response.getStatusCode())
        .as("PATCH producing an invalid entity should return BAD_REQUEST")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // --- Pagination tests ---

  @Test
  @DisplayName("Should return paginated results with default page size")
  void shouldReturnPaginatedResultsWithDefaultPageSize() {
    createTestEntity();
    createTestEntity();

    ResponseEntity<RestPage<O>> response = performGetAll();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getSize()).isEqualTo(20);
    assertThat(response.getBody().getTotalElements()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("Should respect custom page size")
  void shouldRespectCustomPageSize() {
    createTestEntity();
    createTestEntity();

    ResponseEntity<RestPage<O>> response = performGetAll(Map.of("size", 1));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getSize()).isEqualTo(1);
    assertThat(response.getBody().getNumberOfElements()).isEqualTo(1);
    assertThat(response.getBody().getTotalElements()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("Should return second page")
  void shouldReturnSecondPage() {
    createTestEntity();
    createTestEntity();

    ResponseEntity<RestPage<O>> response = performGetAll(Map.of("size", 1, "page", 1));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getNumber()).isEqualTo(1);
    assertThat(response.getBody().getNumberOfElements()).isEqualTo(1);
  }

  // --- Error response tests ---

  @Test
  @DisplayName("Should return 404 when getting non-existent entity")
  void shouldReturn404WhenGettingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId, HttpMethod.GET, createAuthHeaders(), null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 404 when updating non-existent entity")
  void shouldReturn404WhenUpdatingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId,
            HttpMethod.PUT,
            createAuthHeaders(),
            createUpdateInput());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 404 when patching non-existent entity")
  void shouldReturn404WhenPatchingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId,
            HttpMethod.PATCH,
            createAuthHeaders(),
            Map.of("name", "patched"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should return 404 when deleting non-existent entity")
  void shouldReturn404WhenDeletingNonExistentEntity() {
    UUID randomId = UUID.randomUUID();

    ResponseEntity<ProblemDetail> response =
        exchangeForProblem(
            getEndpointPath() + "/" + randomId, HttpMethod.DELETE, createAuthHeaders(), null);

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

  // --- Lifecycle tests ---

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

  @Test
  @DisplayName("Should return entity by ID after creation")
  void shouldReturnEntityByIdAfterCreation() {
    UUID id = createTestEntity();

    ResponseEntity<O> response = performGetById(id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(getIdFromOutput(response.getBody())).isEqualTo(id);
  }

  @Test
  @DisplayName("Should return 401 when no authentication")
  void shouldReturn401WhenNoAuthentication() {
    ResponseEntity<String> response = performRequestWithoutAuth("", HttpMethod.GET);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
