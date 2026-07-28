package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.DataSourceRepository;
import de.civitascore.authz.repository.model.dto.DataSourcePoolsResponse;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service resolving which datapools a data source is assigned to.
 *
 * <p>Consumed by OPA (via the datasource-pools endpoint) to apply datapool inheritance to data
 * sources: a DATAPOOL-scoped grant conveys read access to the data sources assigned to that pool.
 * Only {@code SPECIFIC} assigns a data source to pools; {@code ALL} and {@code NONE} assign it to
 * none, so neither conveys read through a pool grant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourcePoolsService {

  private final DataSourceRepository dataSourceRepository;

  /**
   * Returns the datapools a data source is assigned to.
   *
   * @param dataSourceId the data source id
   * @return present with the assigned pool ids if the data source exists, empty if it does not
   */
  @Transactional(readOnly = true)
  public Optional<DataSourcePoolsResponse> getDataSourcePools(UUID dataSourceId) {
    log.debug("Fetching datapool assignments for data source: {}", dataSourceId);
    return dataSourceRepository
        .findById(dataSourceId)
        .map(
            dataSource -> {
              List<UUID> poolIds =
                  dataSource.getDatapoolScopeType() == DatapoolScopeType.SPECIFIC
                      ? dataSource.getScopedDataPools().stream().map(DataPool::getId).toList()
                      : List.of();
              return new DataSourcePoolsResponse(poolIds);
            });
  }
}
