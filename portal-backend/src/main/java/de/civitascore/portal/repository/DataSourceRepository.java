package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSource} entities. */
@Repository
public interface DataSourceRepository extends NamedEntityRepository<DataSource, UUID> {

  /**
   * Find the IDs of all data sources linked to the given pipeline. Queried directly so the result
   * is available outside the pipeline's persistence session (the {@code Pipeline.dataSources}
   * collection is lazy and would otherwise fail when accessed on a detached entity).
   *
   * @param pipelineId the pipeline ID
   * @return the IDs of the data sources associated with the pipeline
   */
  @Query("SELECT ds.id FROM Pipeline p JOIN p.dataSources ds WHERE p.id = :pipelineId")
  List<UUID> findIdsByPipelineId(@Param("pipelineId") UUID pipelineId);

  /**
   * Check if any data source references the given data structure version.
   *
   * @param dataStructureVersionId the data structure version ID to check
   * @return true if at least one data source references this version
   */
  boolean existsByDataStructureVersionId(UUID dataStructureVersionId);

  /**
   * Check if any data source references any of the given data structure version IDs.
   *
   * @param versionIds the collection of data structure version IDs to check
   * @return true if at least one data source references any of the given versions
   */
  @Query(
      "SELECT CASE WHEN EXISTS("
          + "SELECT 1 FROM DataSource ds WHERE ds.dataStructureVersion.id IN :versionIds"
          + ") THEN true ELSE false END")
  boolean existsByDataStructureVersionIdIn(@Param("versionIds") Collection<UUID> versionIds);

  boolean existsByScopedDataPools_Id(UUID datapoolId);

  /** The data sources with the given status that are pinned to any of the given versions. */
  @Query(
      "SELECT ds.id FROM DataSource ds"
          + " WHERE ds.dataStructureVersion.id IN :versionIds AND ds.dataSourceStatus = :status")
  List<UUID> findIdsByDataStructureVersionIdInAndStatus(
      @Param("versionIds") Collection<UUID> versionIds, @Param("status") DataSourceStatus status);

  /** Whether each data source has the given status, keyed by its configuration's logical URN. */
  @Query(
      "SELECT new de.civitascore.portal.repository.ReferrerReleaseState(ds.configurationLogicalUrn,"
          + " CASE WHEN ds.dataSourceStatus = :status THEN true ELSE false END)"
          + " FROM DataSource ds WHERE ds.configurationLogicalUrn IN :urns")
  List<ReferrerReleaseState> findReleaseStatesByConfigurationLogicalUrnIn(
      @Param("urns") Collection<String> urns, @Param("status") DataSourceStatus status);
}
