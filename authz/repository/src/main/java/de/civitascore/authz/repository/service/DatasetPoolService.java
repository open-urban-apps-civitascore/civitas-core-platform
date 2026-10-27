package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.DataSetRepository;
import de.civitascore.authz.repository.model.dto.DatapoolDatasetSchema;
import de.civitascore.authz.repository.model.dto.DatapoolDatasetsResponse;
import de.civitascore.authz.repository.model.dto.DatasetPoolResponse;
import de.civitascore.portal.model.dataset.DatasetSchemaNames;
import de.civitascore.portal.model.embedded.DataSetStatus;
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
   * Returns the authorization attributes of a dataset: its datapool membership, open-data flag,
   * and availability state. All values come from the CORE dataset record.
   *
   * @param datasetId the dataset id
   * @return present with the dataset's authorization attributes if it exists; empty otherwise
   */
  @Transactional(readOnly = true)
  public Optional<DatasetPoolResponse> getDatasetPool(UUID datasetId) {
    return dataSetRepository
        .findById(datasetId)
        .map(
            dataSet -> {
              DataPool pool = dataSet.getDataPool();
              return new DatasetPoolResponse(
                  dataSet.getId(),
                  pool != null ? pool.getId() : null,
                  DatasetSchemaNames.fromDataset(dataSet.getName(), dataSet.getId()),
                  Boolean.TRUE.equals(dataSet.getOpenDataAccess()),
                  dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE);
            });
  }

  /**
   * Resolves a FROST schema name to its CORE dataset. The UUID suffix narrows the lookup; the
   * complete canonical name is then checked to reject collisions and forged schema names.
   */
  @Transactional(readOnly = true)
  public Optional<DatasetPoolResponse> getDatasetBySchema(String schemaName) {
    if (schemaName == null || !schemaName.matches("[a-z0-9_]{1,63}")) {
      return Optional.empty();
    }
    String suffix = schemaName.substring(Math.max(0, schemaName.length() - 8));
    if (!suffix.matches("[0-9a-f]{8}")) {
      return Optional.empty();
    }
    List<UUID> matches = dataSetRepository.findIdsByUuidSuffix(suffix);
    List<DatasetPoolResponse> exactMatches =
        matches.stream()
            .map(this::getDatasetPool)
            .flatMap(Optional::stream)
            .filter(dataset -> schemaName.equals(dataset.getSchemaName()))
            .toList();
    return exactMatches.size() == 1 ? Optional.of(exactMatches.getFirst()) : Optional.empty();
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

  @Transactional(readOnly = true)
  public List<DatapoolDatasetSchema> getDatasetSchemas() {
    return dataSetRepository.findAll().stream()
        .map(
            dataset ->
                new DatapoolDatasetSchema(
                    dataset.getId(),
                    DatasetSchemaNames.fromDataset(dataset.getName(), dataset.getId())))
        .toList();
  }
}
