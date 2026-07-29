package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.specification.DataSourceScopedDatapoolSpec;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins one rule — may this data source be used in that datapool — across the three places that
 * decide it independently: the entity validator that rejects a pipeline reference, the picker
 * filter behind the {@code datapoolId} query parameter, and the scope filter that grants
 * pool-inherited read. Nothing but this test keeps them from drifting.
 *
 * <p>Pool-inherited read follows the same rule deliberately, so what a pool-scoped steward may read
 * matches what they may build a pipeline from.
 *
 * <p>{@code DataSourcePoolsService} in {@code authz-repository} answers the same question a fourth
 * time, for OPA's per-entity decisions, and is out of this test's reach. Narrowing it would deny
 * single reads that the collection still lists, so the symptom would read as a filter bug rather
 * than a policy divergence.
 */
@DisplayName("DataSource Datapool Usability Contract Integration Tests")
class DataSourceDatapoolUsabilityContractIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataSourceDatapoolScopeValidator validator;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  /**
   * scopeType, whether the data source names the candidate pool, expected usability in that pool.
   */
  private static List<Arguments> truthTable() {
    return List.of(
        Arguments.of(DatapoolScopeType.ALL, false, true),
        Arguments.of(DatapoolScopeType.ALL, true, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, true, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, false, false),
        Arguments.of(DatapoolScopeType.NONE, false, false),
        Arguments.of(DatapoolScopeType.NONE, true, false));
  }

  @ParameterizedTest(name = "{0}, names candidate pool: {1} → usable: {2}")
  @MethodSource("truthTable")
  @DisplayName("All three implementations agree on every case")
  void allImplementationsAgree(
      DatapoolScopeType scopeType, boolean namesCandidate, boolean expectedUsable) {
    DataPool candidate = portalData.dataPool();
    DataPool other = portalData.dataPool();
    DataPool scopedTo = namesCandidate ? candidate : other;
    DataSource dataSource =
        portalData.dataSource(
            b -> b.datapoolScopeType(scopeType).scopedDataPools(new HashSet<>(Set.of(scopedTo))));

    assertThat(validatorSaysUsable(dataSource, candidate))
        .as("entity validator (pipeline reference)")
        .isEqualTo(expectedUsable);
    assertThat(pickerSaysUsable(dataSource.getId(), candidate.getId()))
        .as("datapoolId query filter (picker)")
        .isEqualTo(expectedUsable);
    assertThat(scopeFilterSaysVisible(dataSource.getId(), candidate.getId()))
        .as("scope filter (pool-inherited read)")
        .isEqualTo(expectedUsable);
  }

  /** The user-facing picker filter behind the {@code datapoolId} query parameter. */
  private boolean pickerSaysUsable(UUID dataSourceId, UUID poolId) {
    return dataSourceRepository
        .findAll(
            new DataSourceScopedDatapoolSpec(
                null, "datapoolId", new String[] {poolId.toString()}, null))
        .stream()
        .anyMatch(found -> found.getId().equals(dataSourceId));
  }

  /**
   * The scope filter with no direct scope ids, so only the pool branch can match. {@link
   * ScopeAccessAuthorizer} reuses this specification, so this column also covers what a pool-scoped
   * caller may reference in a request body.
   */
  private boolean scopeFilterSaysVisible(UUID dataSourceId, UUID poolId) {
    return dataSourceRepository
        .findAll(ScopeFilteringSpecification.dataSourceByScopeOrPool(Set.of(), Set.of(poolId)))
        .stream()
        .anyMatch(found -> found.getId().equals(dataSourceId));
  }

  private boolean validatorSaysUsable(DataSource dataSource, DataPool pool) {
    try {
      validator.validate(List.of(dataSource), pool);
      return true;
    } catch (DataSourceScopeViolationException e) {
      return false;
    }
  }
}
