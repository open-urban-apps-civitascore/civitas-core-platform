package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.util.RestPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Base class for controller integration tests providing common test utilities. Subclasses must
 * implement abstract methods to define entity-specific test data.
 *
 * @param <I> Input DTO type
 * @param <O> Output DTO type
 */
public abstract class BaseControllerIntegrationTest<I extends BaseInputDTO, O extends BaseOutputDTO>
    extends BaseKeycloakIntegrationTest {

  @Autowired protected ObjectMapper objectMapper;

  protected final List<String> createdEntityIds = new ArrayList<>();

  protected abstract String getEndpointPath();

  protected abstract I createValidInput();

  protected abstract I createInvalidInput();

  protected abstract I createUpdateInput();

  protected abstract ParameterizedTypeReference<O> getOutputTypeReference();

  protected abstract ParameterizedTypeReference<RestPage<O>> getPageTypeReference();

  protected abstract String getIdFromOutput(O output);

  @AfterEach
  void cleanupAfterTest() {
    try {
      for (int i = createdEntityIds.size() - 1; i >= 0; i--) {
        String id = createdEntityIds.get(i);
        try {
          performDelete(id);
        } catch (Exception e) {
          System.err.println("Could not delete entity with ID " + id + ": " + e.getMessage());
        }
      }
      createdEntityIds.clear();

      try {
        performAdditionalCleanup();
      } catch (Exception e) {
        System.err.println("Additional cleanup warning: " + e.getMessage());
      }

    } catch (Exception e) {
      System.err.println("Cleanup warning: " + e.getMessage());
    }
  }

  /** Override this method to perform additional cleanup for specific entities. */
  protected void performAdditionalCleanup() {}

  protected HttpHeaders createAuthHeaders() {
    String token = getValidAccessToken();
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  protected HttpHeaders createAuthHeaders(String username, String password) {
    String token = getValidAccessToken(username, password);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  protected ResponseEntity<O> performCreate(I input) {
    return performCreate(input, true);
  }

  protected ResponseEntity<O> performCreate(I input, boolean trackForCleanup) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<I> request = new HttpEntity<>(input, headers);
    ResponseEntity<O> response =
        restTemplate.exchange(
            getEndpointPath(), HttpMethod.POST, request, getOutputTypeReference());

    if (trackForCleanup
        && response.getStatusCode() == HttpStatus.CREATED
        && response.getBody() != null) {
      String id = getIdFromOutput(response.getBody()).toString();
      createdEntityIds.add(id);
    }

    return response;
  }

  protected ResponseEntity<O> performGetById(String id) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<Void> request = new HttpEntity<>(headers);
    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.GET, request, getOutputTypeReference());
  }

  protected ResponseEntity<RestPage<O>> performGetAll() {
    return performGetAll(Map.of());
  }

  protected ResponseEntity<RestPage<O>> performGetAll(Map<String, String> queryParams) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<Void> request = new HttpEntity<>(headers);

    StringBuilder url = new StringBuilder(getEndpointPath());
    if (!queryParams.isEmpty()) {
      url.append("?");
      queryParams.forEach((key, value) -> url.append(key).append("=").append(value).append("&"));
      url.setLength(url.length() - 1);
    }

    return restTemplate.exchange(url.toString(), HttpMethod.GET, request, getPageTypeReference());
  }

  protected ResponseEntity<O> performUpdate(String id, I input) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<I> request = new HttpEntity<>(input, headers);
    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.PUT, request, getOutputTypeReference());
  }

  protected ResponseEntity<O> performPatch(String id, I input) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<I> request = new HttpEntity<>(input, headers);
    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.PATCH, request, getOutputTypeReference());
  }

  protected ResponseEntity<O> performPatch(String id, Object patchBody) {
    HttpHeaders headers = createAuthHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Object> request = new HttpEntity<>(patchBody, headers);

    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.PATCH, request, getOutputTypeReference());
  }

  protected ResponseEntity<Void> performDelete(String id) {
    HttpHeaders headers = createAuthHeaders();
    HttpEntity<Void> request = new HttpEntity<>(headers);
    return restTemplate.exchange(
        getEndpointPath() + "/" + id, HttpMethod.DELETE, request, Void.class);
  }

  protected ResponseEntity<String> performRequestWithoutAuth(String path, HttpMethod method) {
    return restTemplate.exchange(
        getEndpointPath() + path, method, new HttpEntity<>(new HttpHeaders()), String.class);
  }

  protected String createTestEntity() {
    ResponseEntity<O> response = performCreate(createValidInput());
    if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
      return getIdFromOutput(response.getBody()).toString();
    }
    throw new IllegalStateException("Failed to create test entity");
  }
}
