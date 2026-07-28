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
 * Pins the two questions the DataSource→DataPool relation answers, against one shared truth table:
 * <em>linkability</em> (may it be used in a pipeline of that pool) and <em>pool-inherited
 * visibility</em> (does a grant on that pool convey read on it).
 *
 * <p>They agree except for an unrestricted data source, which is linkable everywhere but assigned
 * to no pool. Each question has two independent implementations — the entity validator and the
 * picker filter for linkability, the scope filter for visibility — and nothing but this test keeps
 * them from drifting.
 *
 * <p>{@code DataSourcePoolsService} in {@code authz-repository} implements the visibility rule a
 * third time, for OPA's per-entity decisions, and is out of this test's reach. Widening it to
 * report a pool for an unrestricted data source would grant pool-inherited read on every unscoped
 * data source while the scope filter kept hiding those rows, so the symptom would read as a filter
 * bug rather than a privilege widening.
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
   * scopeType, whether the data source names the candidate pool, expected linkability, expected
   * visibility through a grant on that pool.
   *
   * <p>The two columns differ for {@code ALL}: an unrestricted data source may be used in every
   * pipeline, but it is assigned to no pool and therefore conveys no read through a pool grant.
   */
  private static List<Arguments> truthTable() {
    return List.of(
        Arguments.of(DatapoolScopeType.ALL, false, true, false),
        Arguments.of(DatapoolScopeType.ALL, true, true, false),
        Arguments.of(DatapoolScopeType.SPECIFIC, true, true, true),
        Arguments.of(DatapoolScopeType.SPECIFIC, false, false, false),
        Arguments.of(DatapoolScopeType.NONE, false, false, false),
        Arguments.of(DatapoolScopeType.NONE, true, false, false));
  }

  @ParameterizedTest(name = "{0}, names candidate pool: {1} → linkable: {2}, visible: {3}")
  @MethodSource("truthTable")
  @DisplayName("Linkability and pool-inherited visibility each hold on every case")
  void bothImplementationsAgree(
      DatapoolScopeType scopeType,
      boolean namesCandidate,
      boolean expectedLinkable,
      boolean expectedVisible) {
    DataPool candidate = portalData.dataPool();
    DataPool other = portalData.dataPool();
    DataPool scopedTo = namesCandidate ? candidate : other;
    DataSource dataSource =
        portalData.dataSource(
            b -> b.datapoolScopeType(scopeType).scopedDataPools(new HashSet<>(Set.of(scopedTo))));

    assertThat(validatorSaysUsable(dataSource, candidate))
        .as("entity validator (linkability)")
        .isEqualTo(expectedLinkable);
    assertThat(pickerSaysUsable(dataSource.getId(), candidate.getId()))
        .as("datapoolId query filter (linkability)")
        .isEqualTo(expectedLinkable);
    assertThat(scopeFilterSaysVisible(dataSource.getId(), candidate.getId()))
        .as("scope filter (pool-inherited visibility)")
        .isEqualTo(expectedVisible);
  }

  /** The user-facing picker filter, which answers linkability. */
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
   * ScopeAccessAuthorizer} reuses this specification, so the visibility column is also what a
   * pool-scoped caller may reference in a request body — an unrestricted data source is linkable
   * but not referenceable, and that divergence is intentional.
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
