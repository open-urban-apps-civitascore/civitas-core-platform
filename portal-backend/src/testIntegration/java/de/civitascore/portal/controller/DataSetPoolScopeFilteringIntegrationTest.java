package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.RestPage;
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
 * Integration tests for Epic 1 datapool-union collection filtering.
 *
 * <p>Verifies that OPA's X-Allowed-Pool-Ids header (in addition to X-Allowed-Scope-Ids) correctly
 * filters the dataset collection: a user sees datasets directly scoped to them OR datasets in an
 * authorized datapool ({@code id IN (scopeIds) OR datapool_id IN (poolIds)}).
 *
 * <p>Exercises the full path against the real database: AllowedScopesFilter → AllowedScopes bean →
 * DataSetController.scopeSpecification() → ScopeFilteringSpecification.dataSetByScopeOrPool() →
 * SQL. In particular it confirms that accessing {@code dataPool.id} resolves to the foreign-key
 * column (no inner join), so datasets WITHOUT a pool are not wrongly dropped from the scope-id
 * branch of the OR.
 */
@DisplayName("Dataset Pool-Union Scope Filtering Integration Tests")
class DataSetPoolScopeFilteringIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String POOL_HEADER = "X-Allowed-Pool-Ids";
  private static final String DATASETS_ENDPOINT = "/datasets";

  @Autowired private DataSetRepository dataSetRepository;

  @Autowired private DataPoolRepository dataPoolRepository;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("pool_scope_dataset_" + System.currentTimeMillis());
    input.setDescription("Dataset for pool scope filtering test");
    input.setOpenDataAccess(false);
    return input;
  }

  @Override
  protected DataSetInputDTO createInvalidInput() {
    return new DataSetInputDTO();
  }

  @Override
  protected DataSetInputDTO createUpdateInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("updated_pool_scope_dataset");
    input.setDescription("Updated");
    input.setOpenDataAccess(false);
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
    // Datasets reference datapools (FK) — delete datasets first.
    dataSetRepository.deleteAll();
    dataPoolRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    performAdditionalCleanup();
  }

  @Nested
  @DisplayName("Datapool Union Collection Filtering")
  class PoolUnionFiltering {

    @Test
    @DisplayName("Pool-only access returns only datasets in the authorized pool")
    void poolOnlyFiltersToPoolDatasets() {
      UUID pool1 = createDataPool("Pool-1");
      UUID pool2 = createDataPool("Pool-2");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);
      createDataSetInPool("DS-in-2", pool2);
      createDataSet("DS-no-pool");

      RestPage<DataSetOutputDTO> page = getDataSets("", pool1.toString());

      assertThat(page.getContent()).extracting(DataSetOutputDTO::getId).containsExactly(dsInPool1);
    }

    @Test
    @DisplayName("Union: direct scope ID OR pool membership are both returned")
    void unionOfDirectScopeAndPool() {
      UUID pool1 = createDataPool("Pool-1");
      UUID pool2 = createDataPool("Pool-2");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);
      UUID dsDirect = createDataSet("DS-direct");
      createDataSetInPool("DS-in-2", pool2);

      RestPage<DataSetOutputDTO> page = getDataSets(dsDirect.toString(), pool1.toString());

      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(dsInPool1, dsDirect);
    }

    @Test
    @DisplayName("Null-pool dataset matched by scope ID is NOT dropped by the pool OR-branch")
    void nullPoolDatasetSurvivesScopeBranch() {
      // Critical: dataPool.id resolves to the FK column (no inner join), so a dataset
      // without a pool must still match via its direct scope ID when a pool header is present.
      UUID pool1 = createDataPool("Pool-1");
      UUID dsNoPool = createDataSet("DS-no-pool");

      RestPage<DataSetOutputDTO> page = getDataSets(dsNoPool.toString(), pool1.toString());

      assertThat(page.getContent()).extracting(DataSetOutputDTO::getId).containsExactly(dsNoPool);
    }

    @Test
    @DisplayName("Multiple authorized pools return the union of their datasets")
    void multiplePoolsUnion() {
      UUID pool1 = createDataPool("Pool-1");
      UUID pool2 = createDataPool("Pool-2");
      UUID pool3 = createDataPool("Pool-3");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);
      UUID dsInPool2 = createDataSetInPool("DS-in-2", pool2);
      createDataSetInPool("DS-in-3", pool3);

      RestPage<DataSetOutputDTO> page = getDataSets("", pool1 + "," + pool2);

      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(dsInPool1, dsInPool2);
    }

    @Test
    @DisplayName("Authorized pool with no datasets returns empty result")
    void nonMatchingPoolReturnsEmpty() {
      UUID pool1 = createDataPool("Pool-1");
      UUID pool2 = createDataPool("Pool-2");
      createDataSetInPool("DS-in-1", pool1);

      RestPage<DataSetOutputDTO> page = getDataSets("", pool2.toString());

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Wildcard scope returns all datasets regardless of pool")
    void wildcardReturnsAllRegardlessOfPool() {
      UUID pool1 = createDataPool("Pool-1");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);
      UUID dsNoPool = createDataSet("DS-no-pool");

      RestPage<DataSetOutputDTO> page = getDataSets("*", null);

      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(dsInPool1, dsNoPool);
    }

    @Test
    @DisplayName("Wildcard dominates an incidental pool header: unscoped+pool user sees all")
    void wildcardDominatesIncidentalPoolHeader() {
      // P1: a tenant-wide (unscoped) reader with an incidental pool grant gets
      // X-Allowed-Scope-Ids:"*" AND X-Allowed-Pool-Ids; the wildcard must dominate so the
      // user sees ALL datasets — not just the pool's, and not a 403 from a missing header.
      UUID pool1 = createDataPool("Pool-1");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);
      UUID dsNoPool = createDataSet("DS-no-pool");

      RestPage<DataSetOutputDTO> page = getDataSets("*", pool1.toString());

      assertThat(page.getContent())
          .extracting(DataSetOutputDTO::getId)
          .containsExactlyInAnyOrder(dsInPool1, dsNoPool);
    }
  }

  @Nested
  @DisplayName("Datapool Union Resource Endpoints (P2)")
  class PoolUnionResourceEndpoints {

    @Test
    @DisplayName("Pool-inherited access to a dataset resource endpoint returns 200, not 404")
    void poolInheritedResourceAccessAllowed() {
      UUID pool1 = createDataPool("Pool-1");
      UUID dsInPool1 = createDataSetInPool("DS-in-1", pool1);

      // Pool-only header (no direct scope id): OPA now emits X-Allowed-Pool-Ids for resource
      // endpoints reached via DATAPOOL inheritance, and the backend must honor it (P2 fix) —
      // otherwise a legitimately-readable dataset 404s.
      ResponseEntity<String> response = getNamedApis(dsInPool1, "", pool1.toString());

      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Resource access to a dataset outside the authorized pool is 404")
    void resourceOutsidePoolReturns404() {
      UUID pool1 = createDataPool("Pool-1");
      UUID pool2 = createDataPool("Pool-2");
      UUID dsInPool2 = createDataSetInPool("DS-in-2", pool2);

      ResponseEntity<String> response = getNamedApis(dsInPool2, "", pool1.toString());

      assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
  }

  // --- Helpers ---

  private ResponseEntity<String> getNamedApis(
      UUID id, String scopeHeaderValue, String poolHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    if (scopeHeaderValue != null) {
      headers.set(SCOPE_HEADER, scopeHeaderValue);
    }
    if (poolHeaderValue != null) {
      headers.set(POOL_HEADER, poolHeaderValue);
    }
    return restTemplate.exchange(
        DATASETS_ENDPOINT + "/" + id + "/apis",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private UUID createDataPool(String name) {
    return dataPoolRepository.save(DataPool.builder().name(name).build()).getId();
  }

  private UUID createDataSet(String name) {
    return createDataSetInPool(name, null);
  }

  private UUID createDataSetInPool(String name, UUID datapoolId) {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name);
    input.setDescription("Test dataset");
    input.setOpenDataAccess(false);
    input.setDatapoolId(datapoolId);

    ResponseEntity<DataSetOutputDTO> response = performCreate(input);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().getId();
  }

  private RestPage<DataSetOutputDTO> getDataSets(String scopeHeaderValue, String poolHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    if (scopeHeaderValue != null) {
      headers.set(SCOPE_HEADER, scopeHeaderValue);
    }
    if (poolHeaderValue != null) {
      headers.set(POOL_HEADER, poolHeaderValue);
    }
    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<RestPage<DataSetOutputDTO>> response =
        restTemplate.exchange(DATASETS_ENDPOINT, HttpMethod.GET, request, getPageTypeReference());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }
}
