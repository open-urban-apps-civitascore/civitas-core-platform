package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.util.RestPage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Integration tests for {@code GET /datasets/{id}/usable-datasources}, the input behind the data
 * source picker in the pipeline editor.
 *
 * <p>Two things are under test. Which data sources the endpoint returns — released for every
 * datapool, or for this dataset's datapool, and AVAILABLE — and that the caller is authorized on
 * the <em>dataset</em>: a steward whose only grant is a datapool reaches it through {@code
 * X-Allowed-Pool-Ids} without holding any data source permission.
 */
@DisplayName("Dataset Usable DataSources Integration Tests")
class DataSetUsableDataSourcesIntegrationTest
    extends BaseControllerIntegrationTest<DataSetInputDTO, DataSetOutputDTO> {

  private static final String SCOPE_HEADER = "X-Allowed-Scope-Ids";
  private static final String POOL_HEADER = "X-Allowed-Pool-Ids";
  private static final String DATASETS_ENDPOINT = "/datasets";

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataPoolRepository dataPoolRepository;
  @Autowired private DataSourceRepository dataSourceRepository;

  @Override
  protected String getEndpointPath() {
    return DATASETS_ENDPOINT;
  }

  @Override
  protected DataSetInputDTO createValidInput() {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName("usable_ds_dataset_" + System.nanoTime());
    input.setDescription("Dataset for usable-datasources test");
    input.setOpenDataAccess(false);
    return input;
  }

  @Override
  protected DataSetInputDTO createInvalidInput() {
    return new DataSetInputDTO();
  }

  @Override
  protected DataSetInputDTO createUpdateInput() {
    return createValidInput();
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
    // Datasets reference datapools, data sources reference them via a join table — datasets and
    // data sources first, pools last.
    dataSetRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataPoolRepository.deleteAll();
  }

  @AfterEach
  void cleanup() {
    performAdditionalCleanup();
  }

  @Nested
  @DisplayName("Which data sources are usable")
  class Usability {

    @Test
    @DisplayName("A dataset in a pool sees the unrestricted sources and the ones released for it")
    void poolDataSetSeesUnrestrictedAndOwnPool() {
      UUID poolA = createDataPool("Pool-A");
      UUID poolB = createDataPool("Pool-B");
      UUID unrestricted = createDataSource("aaa-unrestricted", DatapoolScopeType.ALL);
      UUID forPoolA = createDataSource("bbb-for-a", DatapoolScopeType.SPECIFIC, poolA);
      createDataSource("ccc-for-b", DatapoolScopeType.SPECIFIC, poolB);
      createDataSource("ddd-nowhere", DatapoolScopeType.NONE);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", poolA), "*", null);

      assertThat(body)
          .extracting(entry -> entry.get("id"))
          .containsExactlyInAnyOrder(unrestricted.toString(), forPoolA.toString());
    }

    @Test
    @DisplayName("A dataset in no pool sees only the unrestricted sources")
    void poolLessDataSetSeesOnlyUnrestricted() {
      UUID poolA = createDataPool("Pool-A");
      UUID unrestricted = createDataSource("aaa-unrestricted", DatapoolScopeType.ALL);
      createDataSource("bbb-for-a", DatapoolScopeType.SPECIFIC, poolA);
      createDataSource("ccc-nowhere", DatapoolScopeType.NONE);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", null), "*", null);

      assertThat(body)
          .extracting(entry -> entry.get("id"))
          .containsExactly(unrestricted.toString());
    }

    @Test
    @DisplayName("A DRAFT source is not offered, because saving a pipeline would reject it")
    void draftSourceIsNotOffered() {
      UUID available = createDataSource("aaa-available", DatapoolScopeType.ALL);
      createDraftDataSource("bbb-draft");

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", null), "*", null);

      assertThat(body).extracting(entry -> entry.get("id")).containsExactly(available.toString());
    }

    @Test
    @DisplayName("Entries carry id and name only, never connector configuration")
    void entriesCarryIdAndNameOnly() {
      createDataSource("aaa-unrestricted", DatapoolScopeType.ALL);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", null), "*", null);

      assertThat(body).hasSize(1);
      assertThat(body.get(0)).containsOnlyKeys("id", "name");
    }

    @Test
    @DisplayName("A source released for several pools appears once, not once per pool")
    void sourceReleasedForSeveralPoolsAppearsOnce() {
      UUID poolA = createDataPool("Pool-A");
      UUID poolB = createDataPool("Pool-B");
      UUID forBoth = createDataSource("aaa-for-both", DatapoolScopeType.SPECIFIC, poolA, poolB);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", poolA), "*", null);

      assertThat(body).extracting(entry -> entry.get("id")).containsExactly(forBoth.toString());
    }

    @Test
    @DisplayName("An unrestricted source naming no pool at all is still returned")
    void unrestrictedSourceNamingNoPoolIsReturned() {
      // Guards the OR: the SPECIFIC branch must not restrict the outer query, or a source with an
      // empty scopedDataPools set would be dropped from the ALL branch too.
      UUID poolA = createDataPool("Pool-A");
      UUID unrestricted = createDataSource("aaa-unrestricted", DatapoolScopeType.ALL);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", poolA), "*", null);

      assertThat(body)
          .extracting(entry -> entry.get("id"))
          .containsExactly(unrestricted.toString());
    }

    @Test
    @DisplayName("Results are ordered by name")
    void resultsAreOrderedByName() {
      createDataSource("ccc-third", DatapoolScopeType.ALL);
      createDataSource("aaa-first", DatapoolScopeType.ALL);
      createDataSource("bbb-second", DatapoolScopeType.ALL);

      List<Map<String, Object>> body = getUsable(createDataSetInPool("ds", null), "*", null);

      assertThat(body)
          .extracting(entry -> entry.get("name"))
          .containsExactly("aaa-first", "bbb-second", "ccc-third");
    }
  }

  @Nested
  @DisplayName("Authorization on the dataset")
  class Authorization {

    @Test
    @DisplayName("A pool-scoped steward with no data source grant reaches it via the pool header")
    void poolScopedStewardReachesItViaPoolHeader() {
      UUID poolA = createDataPool("Pool-A");
      UUID forPoolA = createDataSource("aaa-for-a", DatapoolScopeType.SPECIFIC, poolA);
      UUID dataSet = createDataSetInPool("ds", poolA);

      // No direct dataset scope id and no data source permission of any kind — the pool grant is
      // the whole authorization, which is the point of routing the picker through the dataset.
      List<Map<String, Object>> body = getUsable(dataSet, "", poolA.toString());

      assertThat(body).extracting(entry -> entry.get("id")).containsExactly(forPoolA.toString());
    }

    @Test
    @DisplayName("A dataset outside the authorized pool is 404, not 403")
    void dataSetOutsideAuthorizedPoolIsNotFound() {
      UUID poolA = createDataPool("Pool-A");
      UUID poolB = createDataPool("Pool-B");
      createDataSource("aaa-unrestricted", DatapoolScopeType.ALL);

      ResponseEntity<String> response =
          exchangeUsable(createDataSetInPool("ds", poolB), "", poolA.toString());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("An unknown dataset is 404")
    void unknownDataSetIsNotFound() {
      ResponseEntity<String> response = exchangeUsable(UUID.randomUUID(), "*", null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("A missing scope header is denied (direct backend access bypassing OPA)")
    void missingScopeHeaderIsDenied() {
      UUID dataSet = createDataSetInPool("ds", null);

      HttpHeaders headers = createAuthHeaders();
      headers.remove(AllowedScopesFilter.HEADER_NAME);
      ResponseEntity<String> response =
          restTemplate.exchange(
              DATASETS_ENDPOINT + "/" + dataSet + "/usable-datasources",
              HttpMethod.GET,
              new HttpEntity<>(headers),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  // --- Helpers ---

  private List<Map<String, Object>> getUsable(
      UUID dataSetId, String scopeHeaderValue, String poolHeaderValue) {
    ResponseEntity<List<Map<String, Object>>> response =
        restTemplate.exchange(
            DATASETS_ENDPOINT + "/" + dataSetId + "/usable-datasources",
            HttpMethod.GET,
            new HttpEntity<>(headersWith(scopeHeaderValue, poolHeaderValue)),
            new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private ResponseEntity<String> exchangeUsable(
      UUID dataSetId, String scopeHeaderValue, String poolHeaderValue) {
    return restTemplate.exchange(
        DATASETS_ENDPOINT + "/" + dataSetId + "/usable-datasources",
        HttpMethod.GET,
        new HttpEntity<>(headersWith(scopeHeaderValue, poolHeaderValue)),
        String.class);
  }

  private HttpHeaders headersWith(String scopeHeaderValue, String poolHeaderValue) {
    HttpHeaders headers = createAuthHeaders();
    headers.set(SCOPE_HEADER, scopeHeaderValue);
    if (poolHeaderValue != null) {
      headers.set(POOL_HEADER, poolHeaderValue);
    }
    return headers;
  }

  private UUID createDataPool(String name) {
    return dataPoolRepository.save(DataPool.builder().name(name).build()).getId();
  }

  private UUID createDataSource(String name, DatapoolScopeType scopeType, UUID... scopedPoolIds) {
    DataSource ds = new DataSource();
    ds.setName(name);
    ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    ds.setDatapoolScopeType(scopeType);
    ds.setScopedDataPools(
        new HashSet<>(
            java.util.Arrays.stream(scopedPoolIds)
                .map(id -> dataPoolRepository.findById(id).orElseThrow())
                .toList()));
    return dataSourceRepository.save(ds).getId();
  }

  private UUID createDraftDataSource(String name) {
    DataSource ds = new DataSource();
    ds.setName(name);
    ds.setDataSourceStatus(DataSourceStatus.DRAFT);
    ds.setDatapoolScopeType(DatapoolScopeType.ALL);
    ds.setScopedDataPools(new HashSet<>(Set.of()));
    return dataSourceRepository.save(ds).getId();
  }

  private UUID createDataSetInPool(String name, UUID datapoolId) {
    DataSetInputDTO input = new DataSetInputDTO();
    input.setName(name + "_" + System.nanoTime());
    input.setDescription("Test dataset");
    input.setOpenDataAccess(false);
    input.setDatapoolId(datapoolId);

    ResponseEntity<DataSetOutputDTO> response = performCreate(input);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().getId();
  }
}
