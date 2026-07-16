package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Enforces the DataSource→DataPool scope rule: a DataSource may only feed a dataset whose datapool
 * it is scoped for. This is the single source of truth for the rule, shared by every path that can
 * establish or invalidate the DataSource↔dataset relationship — a pipeline write, a dataset's
 * datapool switch, and a narrowing of a DataSource's own scope.
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

  private boolean isPermitted(DataSource dataSource, DataPool dataPool) {
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
