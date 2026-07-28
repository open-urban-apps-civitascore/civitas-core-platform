package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * Integration tests for datapool-inherited data source filtering.
 *
 * <p>Verifies that OPA's X-Allowed-Pool-Ids header widens the data source collection the same way
 * it widens the dataset collection, so a caller scoped only to a datapool sees the data sources
 * their pipelines are built from: directly scoped data sources OR the ones usable in an authorized
 * pool.
 *
 * <p>Exercises the full path against the real database: AllowedScopesFilter → AllowedScopes bean →
 * DataSourceController.scopeSpecification() → ScopeFilteringSpecification.dataSourceByScopeOrPool()
 * → SQL. In particular it confirms that the pool branch matches assignment, not linkability (only
 * data sources assigned to an authorized pool) and that a data source confined to several
 * authorized pools yields no duplicate rows.
 */
@DisplayName("DataSource Pool-Inherited Scope Filtering Integration Tests")
class DataSourcePoolScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataSourceInputDTO, DataSourceOutputDTO> {

  private static final String DATASOURCES_ENDPOINT = "/datasources";
  private static final String POOL_HEADER = AllowedScopesFilter.HEADER_NAME_POOL;

  @Autowired private PortalTestDataFactory portalData;

  @Override
  protected String getEndpointPath() {
    return DATASOURCES_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  @Override
  protected DataSourceInputDTO createValidInput() {
    DataSourceInputDTO input = new DataSourceInputDTO();
    input.setName("pool_scope_datasource_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("DataSource for pool scope filtering test");
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
    DataSourceInputDTO input = createValidInput();
    input.setDescription("Updated");
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

  @Nested
  @DisplayName("Pool-inherited collection filtering")
  class PoolInheritedFiltering {

    @Test
    @DisplayName("A pool-only caller sees the data sources assigned to that pool")
    void poolOnlySeesUsableDataSources() {
      DataPool pool = portalData.dataPool();
      DataPool otherPool = portalData.dataPool();
      DataSource unrestricted = unrestrictedDataSource();
      DataSource confinedToPool = dataSourceConfinedTo(pool);
      DataSource confinedToOther = dataSourceConfinedTo(otherPool);
      DataSource unusable = unusableDataSource();

      RestPage<DataSourceOutputDTO> page = getDataSources("", pool.getId().toString());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .as("only the data sources assigned to the pool")
          .containsExactly(confinedToPool.getId())
          .doesNotContain(unrestricted.getId(), confinedToOther.getId(), unusable.getId());
    }

    @Test
    @DisplayName("A data source assigned to no pool is never inherited")
    void unusableDataSourceIsNeverInherited() {
      DataPool pool = portalData.dataPool();
      DataSource unusable = unusableDataSource();

      RestPage<DataSourceOutputDTO> page = getDataSources("", pool.getId().toString());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .doesNotContain(unusable.getId());
    }

    @Test
    @DisplayName("Union: a directly scoped data source and a pool-inherited one are both returned")
    void unionOfDirectScopeAndPool() {
      DataPool pool = portalData.dataPool();
      DataPool otherPool = portalData.dataPool();
      DataSource confinedToPool = dataSourceConfinedTo(pool);
      // Confined elsewhere, so it can only be reached via its direct scope id.
      DataSource direct = dataSourceConfinedTo(otherPool);

      RestPage<DataSourceOutputDTO> page =
          getDataSources(direct.getId().toString(), pool.getId().toString());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactlyInAnyOrder(confinedToPool.getId(), direct.getId());
    }

    @Test
    @DisplayName("A direct scope id reaches even a data source usable in no pipeline")
    void directScopeReachesUnusableDataSource() {
      // The pool branch must narrow only itself: an explicit DATASOURCE grant is unaffected by the
      // datapool-usability rule, which governs pipeline linkability, not direct ownership.
      DataPool pool = portalData.dataPool();
      DataSource unusable = unusableDataSource();

      RestPage<DataSourceOutputDTO> page =
          getDataSources(unusable.getId().toString(), pool.getId().toString());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactly(unusable.getId());
    }

    @Test
    @DisplayName("Multiple authorized pools return the union of their data sources")
    void multiplePoolsUnion() {
      DataPool poolA = portalData.dataPool();
      DataPool poolB = portalData.dataPool();
      DataPool poolC = portalData.dataPool();
      DataSource inA = dataSourceConfinedTo(poolA);
      DataSource inB = dataSourceConfinedTo(poolB);
      DataSource inC = dataSourceConfinedTo(poolC);

      RestPage<DataSourceOutputDTO> page = getDataSources("", poolA.getId() + "," + poolB.getId());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactlyInAnyOrder(inA.getId(), inB.getId())
          .doesNotContain(inC.getId());
    }

    @Test
    @DisplayName("A data source confined to several authorized pools yields no duplicate rows")
    void noDuplicateRowsForMultiPoolDataSource() {
      DataPool poolA = portalData.dataPool();
      DataPool poolB = portalData.dataPool();
      DataSource inBoth =
          portalData.dataSource(
              b ->
                  b.datapoolScopeType(DatapoolScopeType.SPECIFIC)
                      .scopedDataPools(new HashSet<>(Set.of(poolA, poolB))));

      RestPage<DataSourceOutputDTO> page = getDataSources("", poolA.getId() + "," + poolB.getId());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .containsExactly(inBoth.getId());
      assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("No scope ids and no pool ids returns nothing (fail-secure)")
    void noScopeAndNoPoolReturnsEmpty() {
      unrestrictedDataSource();

      RestPage<DataSourceOutputDTO> page = getDataSources("", null);

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("An authorized pool with no usable data sources returns nothing")
    void poolWithoutUsableDataSourcesReturnsEmpty() {
      DataPool pool = portalData.dataPool();
      DataPool otherPool = portalData.dataPool();
      dataSourceConfinedTo(otherPool);
      unusableDataSource();

      RestPage<DataSourceOutputDTO> page = getDataSources("", pool.getId().toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Wildcard scope dominates an incidental pool header and returns everything")
    void wildcardDominatesPoolHeader() {
      DataPool pool = portalData.dataPool();
      DataSource confinedElsewhere = dataSourceConfinedTo(portalData.dataPool());
      DataSource unusable = unusableDataSource();

      RestPage<DataSourceOutputDTO> page = getDataSources("*", pool.getId().toString());

      assertThat(page.getContent())
          .extracting(DataSourceOutputDTO::getId)
          .contains(confinedElsewhere.getId(), unusable.getId());
    }
  }

  // --- Helpers ---

  private DataSource unrestrictedDataSource() {
    return portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.ALL));
  }

  private DataSource unusableDataSource() {
    return portalData.dataSource(b -> b.datapoolScopeType(DatapoolScopeType.NONE));
  }

  private DataSource dataSourceConfinedTo(DataPool pool) {
    return portalData.dataSource(
        b ->
            b.datapoolScopeType(DatapoolScopeType.SPECIFIC)
                .scopedDataPools(new HashSet<>(Set.of(pool))));
  }

  private RestPage<DataSourceOutputDTO> getDataSources(
      String scopeHeaderValue, String poolHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    headers.set(AllowedScopesFilter.HEADER_NAME, scopeHeaderValue);
    if (poolHeaderValue != null) {
      headers.set(POOL_HEADER, poolHeaderValue);
    }

    ResponseEntity<RestPage<DataSourceOutputDTO>> response =
        restTemplate.exchange(
            DATASOURCES_ENDPOINT,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            getPageTypeReference());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }
}
