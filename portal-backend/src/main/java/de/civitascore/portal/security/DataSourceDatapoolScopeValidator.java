package de.civitascore.portal.security;

import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Enforces the DataSource→DataPool confinement invariant: a DataSource may only feed a dataset
 * whose datapool it is scoped for. This is an access-control guard, not mere data hygiene — a
 * DataPool is an assignment scope ({@code ScopeType.DATAPOOL}), so the pool a dataset sits in
 * decides which users' grants can reach the data flowing through it. Letting a DataSource enter a
 * pool it is not scoped for would widen that data's audience to grants it was never meant for. The
 * guard applies to the data relationship itself, regardless of who establishes it — it is not an
 * authorization check on the caller of a request.
 *
 * <p>It is the single source of truth for the rule, shared by every path that can establish or
 * invalidate the relationship.
 */
@Component
public class DataSourceDatapoolScopeValidator {

  /**
   * Validates that every DataSource is in scope for {@code dataPool} (or, when {@code dataPool} is
   * {@code null}, for a pool-less dataset).
   *
   * @param dataSources the DataSources to check
   * @param dataPool the datapool the DataSources would feed, or {@code null} for a pool-less
   *     dataset
   * @throws DataSourceScopeViolationException if any DataSource is out of scope, carrying the
   *     offending ids
   */
  public void validate(Collection<DataSource> dataSources, DataPool dataPool) {
    List<UUID> offending =
        dataSources.stream()
            .filter(ds -> !isPermitted(ds, dataPool))
            .map(DataSource::getId)
            .distinct()
            .toList();

    if (!offending.isEmpty()) {
      throw new DataSourceScopeViolationException(offending);
    }
  }

  /**
   * Whether a single DataSource is in scope for {@code dataPool} ({@code null} for a pool-less
   * dataset). The predicate form of {@link #validate}, for callers that need to collect the
   * offending ids rather than fail on the first one.
   *
   * @param dataSource the DataSource to check
   * @param dataPool the datapool it would feed, or {@code null} for a pool-less dataset
   * @return true if the DataSource may feed that datapool
   */
  public boolean isPermitted(DataSource dataSource, DataPool dataPool) {
    return switch (dataSource.getDatapoolScopeType()) {
      case ALL -> true;
      case NONE -> false;
      // A pool-less dataset (dataPool == null) may not carry a SPECIFIC-scoped source: its
      // pool-confined data must not leak into a dataset bound to no pool.
      case SPECIFIC ->
          dataPool != null
              && dataSource.getScopedDataPools().stream()
                  .anyMatch(scopedPool -> scopedPool.getId().equals(dataPool.getId()));
    };
  }
}
