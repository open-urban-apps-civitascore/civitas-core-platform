package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.net.URI;
import java.util.List;
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
 * Integration tests for scope-based collection filtering on DataSource endpoints.
 *
 * <p>Verifies that the X-Allowed-Scope-Ids header correctly filters data source collection results.
 * Tests the full path: AllowedScopesFilter → AllowedScopes bean → BaseController.applyScopeFilter()
 * → JPA Specification.
 */
@DisplayName("DataSource Scope Filtering Integration Tests")
class DataSourceScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataSourceInputDTO, DataSourceOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String DATASOURCES_ENDPOINT = "/datasources";

  @Autowired private DataSourceRepository dataSourceRepository;

  @Override
  protected String getEndpointPath() {
    return DATASOURCES_ENDPOINT;
  }

  @Override
  protected DataSourceInputDTO createValidInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("scope_test_datasource_" + System.currentTimeMillis());
    input.setDescription("DataSource for scope filtering test");
    input.setConnectorType(ConnectorType.MQTT);
    input.setConfiguration(
        Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));
    return input;
  }

  @Override
  protected DataSourceInputDTO createInvalidInput() {
    return new DataSourceInputDTO();
  }

  @Override
  protected DataSourceInputDTO createUpdateInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("updated_scope_datasource");
    input.setDescription("Updated");
    input.setConnectorType(ConnectorType.MQTT);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataSourceOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataSourceOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataSourceOutputDTO output) {
    return output.getId();
  }

  @Override
  protected void performAdditionalCleanup() {
    dataSourceRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    performAdditionalCleanup();
  }

  // --- DataSource scope filtering ---

  @Nested
  @DisplayName("DataSource Collection Filtering")
  class DataSourceFiltering {

    @Test
    @DisplayName("Wildcard scope returns all data sources")
    void wildcardReturnsAll() {
      UUID ds1 = createDataSource("DS-1");
      UUID ds2 = createDataSource("DS-2");
      UUID ds3 = createDataSource("DS-3");

      RestPage<DataSourceOutputDTO> page = getAllDataSources("*");

      assertThat(page.getContent()).hasSize(3);
      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2, ds3);
    }

    @Test
    @DisplayName("Single scope ID filters to matching data source only")
    void singleScopeFilters() {
      UUID ds1 = createDataSource("DS-1");
      createDataSource("DS-2");

      RestPage<DataSourceOutputDTO> page = getAllDataSources(ds1.toString());

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getId()).isEqualTo(ds1);
    }

    @Test
    @DisplayName("Multiple scope IDs filter to union of matching data sources")
    void multipleScopesFilterUnion() {
      UUID ds1 = createDataSource("DS-1");
      UUID ds2 = createDataSource("DS-2");
      createDataSource("DS-3");

      RestPage<DataSourceOutputDTO> page = getAllDataSources(ds1 + "," + ds2);

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactlyInAnyOrder(ds1, ds2);
    }

    @Test
    @DisplayName("Non-matching scope ID returns empty result")
    void nonMatchingScopeReturnsEmpty() {
      createDataSource("DS-1");

      RestPage<DataSourceOutputDTO> page = getAllDataSources(UUID.randomUUID().toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Empty scope header returns empty result (fail-secure)")
    void emptyScopeHeaderReturnsEmpty() {
      createDataSource("DS-1");

      RestPage<DataSourceOutputDTO> page = getAllDataSources("");

      assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Missing scope header returns 403 Forbidden")
    void noHeaderReturnsForbidden() {
      createDataSource("DS-1");

      HttpHeaders headers = createAuthHeaders();
      headers.remove(AllowedScopesFilter.HEADER_NAME);
      HttpEntity<Void> request = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(DATASOURCES_ENDPOINT, HttpMethod.GET, request, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Scope filtering composes with query parameter filters")
    void scopeComposesWithQueryParams() {
      UUID dsSensor = createDataSource("Sensor Source");
      UUID dsWeather = createDataSource("Weather Source");
      UUID dsSensorB = createDataSource("Sensor Source B");

      RestPage<DataSourceOutputDTO> page =
          getAllDataSourcesWithParams(
              dsSensor + "," + dsWeather + "," + dsSensorB, Map.of("name", "Sensor"));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getName)
          .containsExactlyInAnyOrder("Sensor Source", "Sensor Source B");
    }
  }

  // --- Helper methods ---

  private UUID createDataSource(String name) {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName(name);
    input.setDescription("Test data source");
    input.setConnectorType(ConnectorType.MQTT);
    input.setConfiguration(
        Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("sensor/data"), "qos", 1));

    ResponseEntity<DataSourceOutputDTO> response = performCreate(input);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().getId();
  }

  private RestPage<DataSourceOutputDTO> getAllDataSources(String scopeHeaderValue) {
    return getAllDataSourcesWithParams(scopeHeaderValue, Map.of());
  }

  private RestPage<DataSourceOutputDTO> getAllDataSourcesWithParams(
      String scopeHeaderValue, Map<String, String> queryParams) {
    URI uri = buildUri(DATASOURCES_ENDPOINT, queryParams);
    HttpHeaders headers = freshAuthHeadersWithScope(scopeHeaderValue);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<RestPage<DataSourceOutputDTO>> response =
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
