package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for scope-based collection filtering (M5.5).
 *
 * <p>Verifies that the X-Allowed-Scope-Ids header correctly filters collection endpoint results.
 * Tests the full path: AllowedScopesFilter -> AllowedScopes bean -> preProcessQuery() -> JPA
 * Specification.
 */
@DisplayName("Scope Filtering Integration Tests")
class ScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String DATASETS_ENDPOINT = "/datasets";
  private static final String DATASPACES_ENDPOINT = "/dataspaces";

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSpaceRepository dataSpaceRepository;

  private UUID dataSpaceA;
  private UUID dataSpaceB;
  private UUID dataSpaceC;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("scope_test_dataset_" + System.currentTimeMillis());
    input.setDescription("Dataset for scope filtering test");
    input.setFormat("JSON");
    input.setExternalId("ext-scope-" + System.currentTimeMillis());
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
    dataSpaceRepository.deleteAll();
  }

  /** Seed three dataspaces and datasets distributed across them. */
  @BeforeEach
  void seedTestData() {
    dataSpaceA = createDataSpace("Scope Test Space A");
    dataSpaceB = createDataSpace("Scope Test Space B");
    dataSpaceC = createDataSpace("Scope Test Space C");
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
      UUID ds1 = createDataSetInSpace("DS-A1", dataSpaceA);
      UUID ds2 = createDataSetInSpace("DS-B1", dataSpaceB);
      UUID ds3 = createDataSetInSpace("DS-C1", dataSpaceC);

      RestPage<DataSetOutputDTO> page = getAllDataSets("*");

      assertThat(page.getContent()).hasSize(3);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2, ds3);
    }

    @Test
    @DisplayName("Single scope ID filters to matching dataset only")
    void singleScopeFilters() {
      UUID dsA = createDataSetInSpace("DS-A1", dataSpaceA);
      createDataSetInSpace("DS-B1", dataSpaceB);

      RestPage<DataSetOutputDTO> page = getAllDataSets(dsA.toString());

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(dsA);
    }

    @Test
    @DisplayName("Multiple scope IDs filter to union of matching datasets")
    void multipleScopesFilterUnion() {
      UUID dsA = createDataSetInSpace("DS-A1", dataSpaceA);
      UUID dsB = createDataSetInSpace("DS-B1", dataSpaceB);
      createDataSetInSpace("DS-C1", dataSpaceC);

      RestPage<DataSetOutputDTO> page = getAllDataSets(dsA + "," + dsB);

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(dsA, dsB);
    }

    @Test
    @DisplayName("Non-matching scope ID returns empty result")
    void nonMatchingScopeReturnsEmpty() {
      createDataSetInSpace("DS-A1", dataSpaceA);

      RestPage<DataSetOutputDTO> page = getAllDataSets(UUID.randomUUID().toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Empty scope header returns empty result (fail-secure)")
    void emptyScopeHeaderReturnsEmpty() {
      createDataSetInSpace("DS-A1", dataSpaceA);

      RestPage<DataSetOutputDTO> page = getAllDataSets("");

      assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Missing scope header returns 403 Forbidden")
    void noHeaderReturnsForbidden() {
      createDataSetInSpace("DS-A1", dataSpaceA);

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
      UUID dsSensor = createDataSetInSpace("Sensor Data", dataSpaceA);
      UUID dsWeather = createDataSetInSpace("Weather Data", dataSpaceA);
      UUID dsSensorB = createDataSetInSpace("Sensor Data B", dataSpaceB);

      RestPage<DataSetOutputDTO> page =
          getAllDataSetsWithParams(
              dsSensor + "," + dsWeather + "," + dsSensorB, Map.of("name", "Sensor"));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getName)
          .containsExactlyInAnyOrder("Sensor Data", "Sensor Data B");
    }
  }

  // --- DataSpace scope filtering ---

  @Nested
  @DisplayName("DataSpace Collection Filtering")
  class DataSpaceFiltering {

    @Test
    @DisplayName("Wildcard scope returns all dataspaces")
    void wildcardReturnsAll() {
      RestPage<DataSpaceOutputDTO> page = getAllDataSpaces("*");

      assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Specific scope IDs filter dataspaces by ID")
    void specificScopeFilters() {
      RestPage<DataSpaceOutputDTO> page = getAllDataSpaces(dataSpaceA + "," + dataSpaceB);

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSpaceOutputDTO::getId)
          .containsExactlyInAnyOrder(dataSpaceA, dataSpaceB);
    }

    @Test
    @DisplayName("Non-matching scope returns empty dataspaces")
    void nonMatchingReturnsEmpty() {
      RestPage<DataSpaceOutputDTO> page = getAllDataSpaces(UUID.randomUUID().toString());

      assertThat(page.getContent()).isEmpty();
    }
  }

  // --- Malformed header handling ---

  @Nested
  @DisplayName("Malformed Header Handling")
  class MalformedHeaders {

    @Test
    @DisplayName("Invalid UUID in header is skipped, valid ones still work")
    void invalidUuidSkipped() {
      UUID dsA = createDataSetInSpace("DS-A1", dataSpaceA);

      RestPage<DataSetOutputDTO> page = getAllDataSets("not-a-uuid," + dsA);

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(dsA);
    }

    @Test
    @DisplayName("All-invalid UUIDs in header returns empty (fail-secure)")
    void allInvalidReturnsEmpty() {
      createDataSetInSpace("DS-A1", dataSpaceA);

      RestPage<DataSetOutputDTO> page = getAllDataSets("not-a-uuid,also-invalid");

      assertThat(page.getContent()).isEmpty();
    }
  }

  // --- Helper methods ---

  private UUID createDataSpace(String name) {
    DataSpaceInputDTO input = new DataSpaceInputDTO();
    input.setName(name);
    input.setDescription("Test dataspace for scope filtering");
    input.setExternalId("ext-" + name.toLowerCase().replace(" ", "-"));

    HttpHeaders headers = createAuthHeaders();
    HttpEntity<DataSpaceInputDTO> request = new HttpEntity<>(input, headers);
    ResponseEntity<DataSpaceOutputDTO> response =
        restTemplate.exchange(
            DATASPACES_ENDPOINT,
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<DataSpaceOutputDTO>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().getId();
  }

  private UUID createDataSetInSpace(String name, UUID dataSpaceId) {
    return createDataSetInSpaces(name, List.of(dataSpaceId));
  }

  private UUID createDataSetInSpaces(String name, List<UUID> dataSpaceIds) {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name);
    input.setDescription("Test dataset");
    input.setFormat("JSON");
    input.setExternalId("ext-" + name.toLowerCase().replace(" ", "-"));
    input.setDataSpaceIds(dataSpaceIds);

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

  private RestPage<DataSpaceOutputDTO> getAllDataSpaces(String scopeHeaderValue) {
    HttpHeaders headers = freshAuthHeadersWithScope(scopeHeaderValue);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<RestPage<DataSpaceOutputDTO>> response =
        restTemplate.exchange(
            DATASPACES_ENDPOINT,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<RestPage<DataSpaceOutputDTO>>() {});

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
