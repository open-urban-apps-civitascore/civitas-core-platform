package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Abstract base class for integration tests on data-entity controllers that support scope-based
 * access control and assignments.
 *
 * <p>Extends {@link BaseControllerIntegrationTest} with common contract tests for:
 *
 * <ul>
 *   <li>Scope filtering via the {@code X-Allowed-Scope-Ids} header (missing, wildcard, specific,
 *       non-matching)
 *   <li>The {@code GET /{id}/assignments} endpoint (existing entity, non-existent entity)
 * </ul>
 *
 * @param <I> the input DTO type (must extend {@link BaseDataEntityInputDTO})
 * @param <O> the output DTO type
 */
public abstract class BaseDataEntityControllerIntegrationTest<
        I extends BaseDataEntityInputDTO, O extends BaseOutputDTO>
    extends BaseControllerIntegrationTest<I, O> {

  /**
   * Creates auth headers without the scope header. Used to verify that requests without the
   * X-Allowed-Scope-Ids header are rejected.
   */
  protected HttpHeaders createAuthHeadersWithoutScope() {
    HttpHeaders headers = createAuthHeaders();
    headers.remove(AllowedScopesFilter.HEADER_NAME);
    return headers;
  }

  /**
   * Creates auth headers with a specific scope header value.
   *
   * @param scopeValue the value for the X-Allowed-Scope-Ids header
   */
  protected HttpHeaders createAuthHeadersWithScope(String scopeValue) {
    HttpHeaders headers = createAuthHeaders();
    headers.set(AllowedScopesFilter.HEADER_NAME, scopeValue);
    return headers;
  }

  // --- Scope filtering contract tests ---

  @Nested
  @DisplayName("Scope Filtering")
  class ScopeFilteringContractTests {

    @Test
    @DisplayName("Should return 403 when X-Allowed-Scope-Ids header is missing")
    void shouldReturn403WhenScopeHeaderMissing() {
      HttpHeaders headers = createAuthHeadersWithoutScope();
      HttpEntity<Void> request = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(getEndpointPath(), HttpMethod.GET, request, String.class);

      assertThat(response.getStatusCode())
          .as("Missing scope header should return 403 Forbidden")
          .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should return entities when wildcard scope is used")
    void shouldReturnEntitiesWithWildcardScope() {
      createTestEntity();

      ResponseEntity<RestPage<O>> response = performGetAll();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements())
          .as("Wildcard scope should return at least the created entity")
          .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should return only matching entity when specific scope ID is used")
    void shouldReturnOnlyMatchingEntityWithSpecificScope() {
      UUID entityId = createTestEntity();

      HttpHeaders headers = createAuthHeadersWithScope(entityId.toString());
      URI uri = buildUri(getEndpointPath(), null);

      ResponseEntity<RestPage<O>> response =
          exchange(uri, HttpMethod.GET, headers, getPageTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements())
          .as("Specific scope ID should return exactly the matching entity")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("Should return empty result when non-matching scope ID is used")
    void shouldReturnEmptyWithNonMatchingScope() {
      createTestEntity();

      HttpHeaders headers = createAuthHeadersWithScope(UUID.randomUUID().toString());
      URI uri = buildUri(getEndpointPath(), null);

      ResponseEntity<RestPage<O>> response =
          exchange(uri, HttpMethod.GET, headers, getPageTypeReference());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getTotalElements())
          .as("Non-matching scope ID should return zero results")
          .isZero();
    }
  }

  // --- Assignments endpoint contract tests ---

  @Nested
  @DisplayName("Assignments Endpoint")
  class AssignmentsEndpointContractTests {

    @Test
    @DisplayName("Should return 200 for GET /{id}/assignments on existing entity")
    void shouldReturn200ForAssignmentsOnExistingEntity() {
      UUID entityId = createTestEntity();

      ResponseEntity<List<AssignmentOutputDTO>> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + entityId + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              new ParameterizedTypeReference<>() {});

      assertThat(response.getStatusCode())
          .as("GET /{id}/assignments should return 200 for existing entity")
          .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should return 404 for GET /{id}/assignments on non-existent entity")
    void shouldReturn404ForAssignmentsOnNonExistentEntity() {
      ResponseEntity<String> response =
          restTemplate.exchange(
              getEndpointPath() + "/" + UUID.randomUUID() + "/assignments",
              HttpMethod.GET,
              new HttpEntity<>(createAuthHeaders()),
              String.class);

      assertThat(response.getStatusCode())
          .as("GET /{id}/assignments should return 404 for non-existent entity")
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }
}
