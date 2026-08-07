package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.DataSetRepository;
import de.civitascore.authz.repository.model.dto.DatapoolDatasetsResponse;
import de.civitascore.authz.repository.model.dto.DatasetPoolResponse;
import de.civitascore.portal.model.entity.DataPool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service resolving the datapool a dataset belongs to.
 *
 * <p>Consumed by OPA (via the dataset-pool endpoint) to apply Epic 1 union inheritance: a
 * DATAPOOL-scoped grant applies to every dataset in that pool. A dataset belongs to at most one
 * pool, so the response is a single id (or none), never a list — the OPA-side decision is therefore
 * O(1) in pool size. (The lookup itself loads the DataSet row via {@code findById}; a projection
 * could trim that if this becomes a hot path — see review proposals.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetPoolService {

  private final DataSetRepository dataSetRepository;

  /**
   * Returns the authorization attributes of a dataset: its datapool membership (Epic 1 union
   * inheritance), its open-data flag (ABAC), and its FROST project id (row-level scoping for
   * FROST-backed sensor data, see the Trino row-filter policy). All resolved from the single
   * {@code DataSet} row, so the lookup stays one query.
   *
   * @param datasetId the dataset id
   * @return present with the pool id (possibly {@code null} when the dataset has no pool), the
   *     open-data flag, and the FROST project id (possibly {@code null} when the dataset has no
   *     FROST sink) if the dataset exists; empty if the dataset does not exist
   */
  @Transactional(readOnly = true)
  public Optional<DatasetPoolResponse> getDatasetPool(UUID datasetId) {
    return dataSetRepository
        .findById(datasetId)
        .map(
            dataSet -> {
              DataPool pool = dataSet.getDataPool();
              return new DatasetPoolResponse(
                  pool != null ? pool.getId() : null,
                  Boolean.TRUE.equals(dataSet.getOpenDataAccess()),
                  dataSet.getProjectId());
            });
  }

  /**
   * Reverse of {@link #getDatasetPool(UUID)}: resolves every dataset belonging to any of the given
   * pools, deduplicated. Consumed by OPA to enumerate the datasets a DATAPOOL-scoped grant covers
   * for listing purposes (e.g. Superset's schema list), where per-dataset {@code pool_of()} lookups
   * don't help since the caller doesn't yet know which dataset ids exist in the pool.
   *
   * @param poolIds the datapool ids to resolve
   * @return the deduplicated union of dataset ids across all given pools
   */
  @Transactional(readOnly = true)
  public DatapoolDatasetsResponse getDatasetsInPools(List<UUID> poolIds) {
    return new DatapoolDatasetsResponse(dataSetRepository.findIdsByDataPoolIdIn(poolIds));
  }
}
