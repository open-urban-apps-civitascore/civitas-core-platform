package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.specification.DataSourceDatapoolUsability;
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
 * Pins one rule — may this data source be used in that datapool — across the two places that decide
 * it independently: the entity validator that rejects a pipeline reference, and the SQL predicate
 * behind both the {@code datapoolId} query filter and {@code GET
 * /datasets/{id}/usable-datasources}. Nothing but this test keeps them from drifting, and a
 * divergence would let the picker offer a source that saving then rejects.
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
   * scopeType, whether the data source names the candidate pool, whether the candidate is a real
   * pool at all (false = pool-less dataset), expected usability.
   */
  private static List<Arguments> truthTable() {
    return List.of(
        Arguments.of(DatapoolScopeType.ALL, false, true, true),
        Arguments.of(DatapoolScopeType.ALL, true, true, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, true, true, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, false, true, false),
        Arguments.of(DatapoolScopeType.NONE, false, true, false),
        Arguments.of(DatapoolScopeType.NONE, true, true, false),
        // A dataset in no datapool: only a source released for every pool may feed it, so
        // pool-confined data cannot leak into a dataset bound to no pool.
        Arguments.of(DatapoolScopeType.ALL, false, false, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, false, false, false),
        Arguments.of(DatapoolScopeType.NONE, false, false, false));
  }

  @ParameterizedTest(name = "{0}, names candidate: {1}, candidate is a pool: {2} → usable: {3}")
  @MethodSource("truthTable")
  @DisplayName("Both implementations agree on every case")
  void bothImplementationsAgree(
      DatapoolScopeType scopeType,
      boolean namesCandidate,
      boolean candidateIsAPool,
      boolean expectedUsable) {
    DataPool candidate = candidateIsAPool ? portalData.dataPool() : null;
    DataPool other = portalData.dataPool();
    DataPool scopedTo = namesCandidate && candidate != null ? candidate : other;
    DataSource dataSource =
        portalData.dataSource(
            b -> b.datapoolScopeType(scopeType).scopedDataPools(new HashSet<>(Set.of(scopedTo))));

    UUID candidateId = candidate == null ? null : candidate.getId();

    assertThat(validatorSaysUsable(dataSource, candidate))
        .as("entity validator (pipeline reference)")
        .isEqualTo(expectedUsable);
    assertThat(queryFilterSaysUsable(dataSource.getId(), candidateId))
        .as("SQL predicate (picker filter and usable-datasources endpoint)")
        .isEqualTo(expectedUsable);
  }

  /** The SQL predicate shared by the {@code datapoolId} filter and the endpoint. */
  private boolean queryFilterSaysUsable(UUID dataSourceId, UUID poolId) {
    return dataSourceRepository.findAll(DataSourceDatapoolUsability.usableInPool(poolId)).stream()
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
