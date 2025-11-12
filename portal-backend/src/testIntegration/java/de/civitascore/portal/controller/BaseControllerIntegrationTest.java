package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

  protected abstract String getIdFromOutput(O output);

  // Optional hook for domain-specific cleanup (files, stubs, etc.)
  protected void performAdditionalCleanup() {}

  @AfterEach
  void cleanupAfterTest() {
    // Prefer DB-level isolation (TRUNCATE/reset) configured once for the profile.
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
    HttpEntity<?> request =
        (body == null) ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    return restTemplate.exchange(path, method, request, type);
  }

  protected ResponseEntity<O> performCreate(I input) {
    return exchange(
        getEndpointPath(), HttpMethod.POST, createAuthHeaders(), input, getOutputTypeReference());
  }

  protected ResponseEntity<O> performGetById(String id) {
    String url = getEndpointPath() + "/" + id;
    return exchange(url, HttpMethod.GET, createAuthHeaders(), null, getOutputTypeReference());
  }

  protected ResponseEntity<RestPage<O>> performGetAll() {
    return performGetAll(Map.of());
  }

  protected ResponseEntity<RestPage<O>> performGetAll(Map<String, ?> queryParams) {
    URI uri = buildUri(getEndpointPath(), queryParams);
    HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());
    return restTemplate.exchange(uri, HttpMethod.GET, request, getPageTypeReference());
  }

  protected ResponseEntity<O> performUpdate(String id, I input) {
    String url = getEndpointPath() + "/" + id;
    return exchange(url, HttpMethod.PUT, createAuthHeaders(), input, getOutputTypeReference());
  }

  protected ResponseEntity<O> performPatch(String id, Object patchBody) {
    String url = getEndpointPath() + "/" + id;
    HttpHeaders headers = createAuthHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return exchange(url, HttpMethod.PATCH, headers, patchBody, getOutputTypeReference());
  }

  protected ResponseEntity<Void> performDelete(String id) {
    String url = getEndpointPath() + "/" + id;
    HttpEntity<Void> request = new HttpEntity<>(createAuthHeaders());
    return restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
  }

  protected ResponseEntity<String> performRequestWithoutAuth(String path, HttpMethod method) {
    return restTemplate.exchange(getEndpointPath() + path, method, HttpEntity.EMPTY, String.class);
  }

  protected String createTestEntity() {
    ResponseEntity<O> response = performCreate(createValidInput());
    if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
      return getIdFromOutput(response.getBody());
    }
    throw new IllegalStateException("Failed to create test entity");
  }

  protected HttpHeaders withHeaders(Consumer<HttpHeaders> customizer) {
    HttpHeaders headers = createAuthHeaders();
    customizer.accept(headers);
    return headers;
  }
}
