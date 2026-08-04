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
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * The query-level form of one question: may this DataSource be used in that DataPool? {@code ALL}
 * counts for every pool, {@code SPECIFIC} for the pools it names, {@code NONE} for none.
 *
 * <p>{@code DataSourceDatapoolScopeValidator} decides the same question on loaded entities, when a
 * pipeline establishes the relationship. The two must agree, and nothing but {@code
 * DataSourceDatapoolUsabilityContractIntegrationTest} keeps them from drifting.
 */
public final class DataSourceDatapoolUsability {

  private DataSourceDatapoolUsability() {}

  /**
   * Filters DataSources to those usable in the given datapool.
   *
   * @param poolId the datapool to test usability against, or {@code null} for a pool-less dataset
   * @return the usability specification
   */
  public static Specification<DataSource> usableInPool(UUID poolId) {
    return (root, query, cb) -> {
      Predicate isUnrestricted = cb.equal(root.get("datapoolScopeType"), DatapoolScopeType.ALL);
      if (poolId == null) {
        // A pool-less dataset may not carry a SPECIFIC-scoped source: pool-confined data must not
        // leak into a dataset bound to no pool.
        return isUnrestricted;
      }
      return cb.or(isUnrestricted, confinedTo(root, query, cb, poolId));
    };
  }

  /**
   * Builds {@code scopeType = SPECIFIC AND EXISTS scopedDataPools ∩ {poolId}}.
   *
   * <p>A correlated EXISTS subquery rather than a join on the outer query, because this predicate
   * is ORed with the {@code ALL} branch: joining {@code scopedDataPools} on the root would be an
   * inner join and would drop every data source that names no pool at all — which is exactly the
   * unrestricted ones the other branch must still match. It also keeps a source naming several
   * pools to a single row.
   */
  private static Predicate confinedTo(
      Root<DataSource> root, CriteriaQuery<?> query, CriteriaBuilder cb, UUID poolId) {
    Subquery<UUID> scopedPool = query.subquery(UUID.class);
    Root<DataSource> subRoot = scopedPool.correlate(root);
    Join<DataSource, DataPool> scopedPools = subRoot.join("scopedDataPools");
    scopedPool.select(scopedPools.get("id")).where(cb.equal(scopedPools.get("id"), poolId));

    return cb.and(
        cb.equal(root.get("datapoolScopeType"), DatapoolScopeType.SPECIFIC), cb.exists(scopedPool));
  }
}
