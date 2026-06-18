package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSource;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSource} entities. */
@Repository
public interface DataSourceRepository extends NamedEntityRepository<DataSource, UUID> {

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
}
