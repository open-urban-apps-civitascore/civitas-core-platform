package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specifications for scope-based collection filtering (M5.5).
 *
 * <p>These specifications filter collection queries based on user's authorized scope IDs from OPA.
 * Used by controllers to implement collection-level access control.
 *
 * <p>Usage: Controllers call {@code BaseController.applyScopeFilter()} with these specifications to
 * decorate the query spec before passing it to the service layer.
 */
public final class ScopeFilteringSpecification {

  private ScopeFilteringSpecification() {}

  /**
   * Filter any BaseEntity by allowed IDs.
   *
   * @param allowedIds the entity IDs the user can access
   * @return specification that filters entities by their IDs
   */
  public static <E extends BaseEntity> Specification<E> baseEntityById(Set<UUID> allowedIds) {
    return (root, query, cb) -> {
      if (allowedIds == null || allowedIds.isEmpty()) {
        return cb.disjunction();
      }
      return root.get("id").in(allowedIds);
    };
  }

  /**
   * Filter datasets by directly scoped IDs OR by datapool membership (Epic 1 union).
   *
   * <p>Produces {@code id IN (scopeIds) OR datapool_id IN (poolIds)}. The behaviour that matters
   * (and is locked by the {@code nullPoolDatasetSurvivesScopeBranch} integration test): a dataset
   * without a pool is excluded only from the pool branch, NOT from the whole query. On current
   * Hibernate, dereferencing {@code dataPool.id} of a {@code @ManyToOne} reads the FK column
   * without a join — do not rely on that mechanism for correctness; the test guards the behaviour.
   *
   * <p>When neither set has entries the user can see nothing, so a match-nothing disjunction is
   * returned (fail-secure, consistent with {@link #baseEntityById}).
   *
   * @param scopeIds directly authorized dataset IDs (may be empty)
   * @param poolIds authorized datapool IDs whose datasets are visible (may be empty)
   * @return specification combining both access paths
   */
  public static Specification<DataSet> dataSetByScopeOrPool(Set<UUID> scopeIds, Set<UUID> poolIds) {
    return scopeOrPool(
        scopeIds, poolIds, (root, query, cb) -> root.get("dataPool").get("id").in(poolIds));
  }

  /**
   * Filter data sources by directly scoped IDs OR by assignment to an authorized datapool.
   *
   * <p>The pool branch matches the data sources <em>assigned</em> to an authorized pool. An
   * unrestricted data source is assigned to no pool in particular and is therefore not reached this
   * way — only through a direct scope id.
   *
   * <p>The scoped-pool membership test is a correlated EXISTS subquery rather than a join, so a
   * data source confined to several authorized pools still yields a single row in paginated
   * results.
   *
   * <p>When neither set has entries the caller can see nothing, so a match-nothing disjunction is
   * returned (fail-secure, consistent with {@link #baseEntityById}).
   *
   * @param scopeIds directly authorized data source IDs (may be empty)
   * @param poolIds authorized datapool IDs whose data sources are visible (may be empty)
   * @return specification combining both access paths
   */
  public static Specification<DataSource> dataSourceByScopeOrPool(
      Set<UUID> scopeIds, Set<UUID> poolIds) {
    return scopeOrPool(
        scopeIds,
        poolIds,
        (root, query, cb) ->
            DataSourceDatapoolUsability.confinedToAnyPool(root, query, cb, poolIds));
  }

  /**
   * Combines the direct-scope branch with an entity-specific pool branch, ORed.
   *
   * <p>Holds the fail-secure default in one place: a caller with neither scope IDs nor pool IDs can
   * see nothing, so an empty match is returned rather than an unfiltered query.
   *
   * @param poolBranch how this entity type relates to an authorized datapool
   */
  private static <E extends BaseEntity> Specification<E> scopeOrPool(
      Set<UUID> scopeIds, Set<UUID> poolIds, Specification<E> poolBranch) {
    return (root, query, cb) -> {
      List<Predicate> orPredicates = new ArrayList<>();
      if (scopeIds != null && !scopeIds.isEmpty()) {
        orPredicates.add(root.get("id").in(scopeIds));
      }
      if (poolIds != null && !poolIds.isEmpty()) {
        orPredicates.add(poolBranch.toPredicate(root, query, cb));
      }
      if (orPredicates.isEmpty()) {
        return cb.disjunction();
      }
      return cb.or(orPredicates.toArray(new Predicate[0]));
    };
  }
}
