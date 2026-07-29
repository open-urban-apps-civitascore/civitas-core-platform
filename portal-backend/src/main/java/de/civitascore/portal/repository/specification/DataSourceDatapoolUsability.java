package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.UUID;

/**
 * Query-level expressions relating a DataSource to a DataPool. Two different questions,
 * deliberately not the same predicate:
 *
 * <ul>
 *   <li>{@link #usableInAnyPool} — may it be used in a pipeline of that pool? {@code ALL} counts
 *       for every pool, {@code SPECIFIC} for the pools it names, {@code NONE} for none. Mirrors
 *       {@code DataSourceDatapoolScopeValidator}, which enforces the same rule on loaded entities.
 *   <li>{@link #confinedToAnyPool} — does it name that pool explicitly? Only {@code SPECIFIC}
 *       counts.
 * </ul>
 *
 * <p>Read access inherited from a datapool grant uses the first, so what a pool-scoped steward may
 * read matches what they may build a pipeline from.
 */
final class DataSourceDatapoolUsability {

  private DataSourceDatapoolUsability() {}

  /**
   * Builds {@code scopeType = ALL OR (scopeType = SPECIFIC AND EXISTS scopedDataPools ∩ poolIds)}.
   *
   * @param poolIds the datapools to test usability against; an empty set matches nothing
   * @return the usability predicate
   */
  static Predicate usableInAnyPool(
      Root<DataSource> root, CriteriaQuery<?> query, CriteriaBuilder cb, Collection<UUID> poolIds) {
    if (poolIds == null || poolIds.isEmpty()) {
      return cb.disjunction();
    }
    Predicate isUnrestricted = cb.equal(root.get("datapoolScopeType"), DatapoolScopeType.ALL);
    return cb.or(isUnrestricted, confinedToAnyPool(root, query, cb, poolIds));
  }

  /**
   * Builds {@code scopeType = SPECIFIC AND EXISTS scopedDataPools ∩ poolIds}.
   *
   * <p>Uses a correlated EXISTS subquery rather than a join, so a data source naming several of the
   * given pools still yields a single row in paginated results.
   *
   * @param poolIds the datapools to test confinement against; an empty set matches nothing
   * @return the confinement predicate
   */
  static Predicate confinedToAnyPool(
      Root<DataSource> root, CriteriaQuery<?> query, CriteriaBuilder cb, Collection<UUID> poolIds) {
    if (poolIds == null || poolIds.isEmpty()) {
      return cb.disjunction();
    }

    Subquery<UUID> scopedPool = query.subquery(UUID.class);
    Root<DataSource> subRoot = scopedPool.correlate(root);
    Join<DataSource, DataPool> scopedPools = subRoot.join("scopedDataPools");
    scopedPool.select(scopedPools.get("id")).where(scopedPools.get("id").in(poolIds));

    return cb.and(
        cb.equal(root.get("datapoolScopeType"), DatapoolScopeType.SPECIFIC), cb.exists(scopedPool));
  }
}
