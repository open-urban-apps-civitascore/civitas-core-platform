package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Integration tests for scope-based collection filtering.
 *
 * <p>Verifies that the X-Allowed-Scope-Ids header correctly filters dataset collection results.
 * Scope IDs correspond to dataset IDs directly (tenant→dataset hierarchy, no intermediate
 * dataspace). Tests the full path: AllowedScopesFilter → AllowedScopes bean →
 * BaseController.applyScopeFilter() → JPA Specification.
 */
@DisplayName("Scope Filtering Integration Tests")
class ScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String DATASETS_ENDPOINT = "/datasets";

  @Autowired private DataSetRepository dataSetRepository;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("scope_test_dataset_" + System.currentTimeMillis());
    input.setDescription("Dataset for scope filtering test");
    return input;
  }

  @Override
  protected DataSetInputDTO createInvalidInput() {
    return new DataSetInputDTO();
  }

  @Override
  protected DataSetInputDTO createUpdateInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("updated_scope_dataset");
    input.setDescription("Updated");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataSetOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSetOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSetOutputDTO output) {
    return output.getId();
  }

  @Override
  protected void performAdditionalCleanup() {
    dataSetRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    performAdditionalCleanup();
  }

  // --- Dataset scope filtering ---

  @Nested
  @DisplayName("Dataset Collection Filtering")
  class DataSetFiltering {

    @Test
    @DisplayName("Wildcard scope returns all datasets")
    void wildcardReturnsAll() {
      UUID ds1 = createDataSet("DS-1");
      UUID ds2 = createDataSet("DS-2");
      UUID ds3 = createDataSet("DS-3");

      RestPage<DataSetOutputDTO> page = getAllDataSets("*");

      assertThat(page.getContent()).hasSize(3);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2, ds3);
    }

    @Test
    @DisplayName("Single scope ID filters to matching dataset only")
    void singleScopeFilters() {
      UUID ds1 = createDataSet("DS-1");
      createDataSet("DS-2");

      RestPage<DataSetOutputDTO> page = getAllDataSets(ds1.toString());

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(ds1);
    }

    @Test
    @DisplayName("Multiple scope IDs filter to union of matching datasets")
    void multipleScopesFilterUnion() {
      UUID ds1 = createDataSet("DS-1");
      UUID ds2 = createDataSet("DS-2");
      createDataSet("DS-3");

      RestPage<DataSetOutputDTO> page = getAllDataSets(ds1 + "," + ds2);

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2);
    }

    @Test
    @DisplayName("Non-matching scope ID returns empty result")
    void nonMatchingScopeReturnsEmpty() {
      createDataSet("DS-1");

      RestPage<DataSetOutputDTO> page = getAllDataSets(UUID.randomUUID().toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Empty scope header returns empty result (fail-secure)")
    void emptyScopeHeaderReturnsEmpty() {
      createDataSet("DS-1");

      RestPage<DataSetOutputDTO> page = getAllDataSets("");

      assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Missing scope header returns 403 Forbidden")
    void noHeaderReturnsForbidden() {
      createDataSet("DS-1");

      HttpHeaders headers = createAuthHeaders();
      headers.remove(AllowedScopesFilter.HEADER_NAME);
      HttpEntity<Void> request = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(DATASETS_ENDPOINT, HttpMethod.GET, request, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Scope filtering composes with query parameter filters")
    void scopeComposesWithQueryParams() {
      UUID dsSensor = createDataSet("Sensor Data");
      UUID dsWeather = createDataSet("Weather Data");
      UUID dsSensorB = createDataSet("Sensor Data B");

      RestPage<DataSetOutputDTO> page =
          getAllDataSetsWithParams(
              dsSensor + "," + dsWeather + "," + dsSensorB, Map.of("name", "Sensor"));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getName)
          .containsExactlyInAnyOrder("Sensor Data", "Sensor Data B");
    }
  }

  // --- Malformed header handling ---

  @Nested
  @DisplayName("Malformed Header Handling")
  class MalformedHeaders {

    @Test
    @DisplayName("Invalid UUID in header is skipped, valid ones still work")
    void invalidUuidSkipped() {
      UUID ds1 = createDataSet("DS-1");

      RestPage<DataSetOutputDTO> page = getAllDataSets("not-a-uuid," + ds1);

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(ds1);
    }

    @Test
    @DisplayName("All-invalid UUIDs in header returns empty (fail-secure)")
    void allInvalidReturnsEmpty() {
      createDataSet("DS-1");

      RestPage<DataSetOutputDTO> page = getAllDataSets("not-a-uuid,also-invalid");

      assertThat(page.getContent()).isEmpty();
    }
  }

  // --- Helper methods ---

  private UUID createDataSet(String name) {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name);
    input.setDescription("Test dataset");

    ResponseEntity<DataSetOutputDTO> response = performCreate(input);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().getId();
  }

  private RestPage<DataSetOutputDTO> getAllDataSets(String scopeHeaderValue) {
    return getAllDataSetsWithParams(scopeHeaderValue, Map.of());
  }

  private RestPage<DataSetOutputDTO> getAllDataSetsWithParams(
      String scopeHeaderValue, Map<String, String> queryParams) {
    URI uri = buildUri(DATASETS_ENDPOINT, queryParams);
    HttpHeaders headers = freshAuthHeadersWithScope(scopeHeaderValue);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<RestPage<DataSetOutputDTO>> response =
        restTemplate.exchange(uri, HttpMethod.GET, request, getPageTypeReference());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  /** Get a fresh auth token with the scope header added. Avoids token expiration issues. */
  private HttpHeaders freshAuthHeadersWithScope(String scopeHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    headers.set(SCOPE_HEADER, scopeHeaderValue);
    return headers;
  }
}
