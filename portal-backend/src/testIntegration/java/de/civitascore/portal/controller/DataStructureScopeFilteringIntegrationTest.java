package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.repository.DataStructureRepository;
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
 * Integration tests for scope-based collection filtering on DataStructure endpoints.
 *
 * <p>Verifies that the X-Allowed-Scope-Ids header correctly filters data structure collection
 * results. Tests the full path: AllowedScopesFilter → AllowedScopes bean →
 * BaseController.applyScopeFilter() → JPA Specification.
 */
@DisplayName("DataStructure Scope Filtering Integration Tests")
class DataStructureScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataStructureInputDTO, DataStructureOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String DATASTRUCTURES_ENDPOINT = "/datastructures";

  @Autowired private DataStructureRepository dataStructureRepository;

  @Override
  protected String getEndpointPath() {
    return DATASTRUCTURES_ENDPOINT;
  }

  @Override
  protected DataStructureInputDTO createValidInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setName("scope_test_datastructure_" + System.currentTimeMillis());
    input.setDescription("DataStructure for scope filtering test");
    input.setCreatedFromDataSource(false);
    return input;
  }

  @Override
  protected DataStructureInputDTO createInvalidInput() {
    return new DataStructureInputDTO();
  }

  @Override
  protected DataStructureInputDTO createUpdateInput() {
    DataStructureInputDTO input = new DataStructureInputDTO();
    input.setName("updated_scope_datastructure");
    input.setDescription("Updated");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataStructureOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataStructureOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataStructureOutputDTO output) {
    return output.getId();
  }

  @Override
  protected void performAdditionalCleanup() {
    dataStructureRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    performAdditionalCleanup();
  }

  // --- DataStructure scope filtering ---

  @Nested
  @DisplayName("DataStructure Collection Filtering")
  class DataStructureFiltering {

    @Test
    @DisplayName("Wildcard scope returns all data structures")
    void wildcardReturnsAll() {
      UUID ds1 = createDataStructure("DS-1");
      UUID ds2 = createDataStructure("DS-2");
      UUID ds3 = createDataStructure("DS-3");

      RestPage<DataStructureOutputDTO> page = getAllDataStructures("*");

      assertThat(page.getContent()).hasSize(3);
      assertThat(page.getContent())
          .extracting(DataStructureOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2, ds3);
    }

    @Test
    @DisplayName("Single scope ID filters to matching data structure only")
    void singleScopeFilters() {
      UUID ds1 = createDataStructure("DS-1");
      createDataStructure("DS-2");

      RestPage<DataStructureOutputDTO> page = getAllDataStructures(ds1.toString());

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().getFirst().getId()).isEqualTo(ds1);
    }

    @Test
    @DisplayName("Multiple scope IDs filter to union of matching data structures")
    void multipleScopesFilterUnion() {
      UUID ds1 = createDataStructure("DS-1");
      UUID ds2 = createDataStructure("DS-2");
      createDataStructure("DS-3");

      RestPage<DataStructureOutputDTO> page = getAllDataStructures(ds1 + "," + ds2);

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataStructureOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2);
    }

    @Test
    @DisplayName("Non-matching scope ID returns empty result")
    void nonMatchingScopeReturnsEmpty() {
      createDataStructure("DS-1");

      RestPage<DataStructureOutputDTO> page = getAllDataStructures(UUID.randomUUID().toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Empty scope header returns empty result (fail-secure)")
    void emptyScopeHeaderReturnsEmpty() {
      createDataStructure("DS-1");

      RestPage<DataStructureOutputDTO> page = getAllDataStructures("");

      assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Missing scope header returns 403 Forbidden")
    void noHeaderReturnsForbidden() {
      createDataStructure("DS-1");

      HttpHeaders headers = createAuthHeaders();
      headers.remove(AllowedScopesFilter.HEADER_NAME);
      HttpEntity<Void> request = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(DATASTRUCTURES_ENDPOINT, HttpMethod.GET, request, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Scope filtering composes with query parameter filters")
    void scopeComposesWithQueryParams() {
      UUID dsSensor = createDataStructure("Sensor Structure");
      UUID dsWeather = createDataStructure("Weather Structure");
      UUID dsSensorB = createDataStructure("Sensor Structure B");

      RestPage<DataStructureOutputDTO> page =
          getAllDataStructuresWithParams(
              dsSensor + "," + dsWeather + "," + dsSensorB, Map.of("name", "Sensor"));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataStructureOutputDTO::getName)
          .containsExactlyInAnyOrder("Sensor Structure", "Sensor Structure B");
    }
  }

  // --- Helper methods ---

  /** Create a DataStructure directly via the repository. */
  private UUID createDataStructure(String name) {
    DataStructure entity = new DataStructure();
    entity.setName(name);
    entity.setDescription("Test data structure");
    entity.setDataStructureStatus(DataStructureStatus.DRAFT);
    entity.setCreatedFromDataSource(false);
    entity = dataStructureRepository.save(entity);
    return entity.getId();
  }

  private RestPage<DataStructureOutputDTO> getAllDataStructures(String scopeHeaderValue) {
    return getAllDataStructuresWithParams(scopeHeaderValue, Map.of());
  }

  private RestPage<DataStructureOutputDTO> getAllDataStructuresWithParams(
      String scopeHeaderValue, Map<String, String> queryParams) {
    URI uri = buildUri(DATASTRUCTURES_ENDPOINT, queryParams);
    HttpHeaders headers = freshAuthHeadersWithScope(scopeHeaderValue);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<RestPage<DataStructureOutputDTO>> response =
        restTemplate.exchange(uri, HttpMethod.GET, request, getPageTypeReference());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  /** Get a fresh auth token with the scope header added. */
  private HttpHeaders freshAuthHeadersWithScope(String scopeHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    headers.set(SCOPE_HEADER, scopeHeaderValue);
    return headers;
  }
}
