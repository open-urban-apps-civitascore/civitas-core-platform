package de.civitascore.authz.repository.data;

import de.civitascore.portal.model.entity.DataSet;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository for dataset→datapool membership lookups.
 *
 * <p>Used by {@link de.civitascore.authz.repository.service.DatasetPoolService} to resolve the
 * datapool a single dataset belongs to (Epic 1 union inheritance), and the reverse direction (every
 * dataset in a set of pools) for OPA's dataset-listing rules. Reads the same portal database as the
 * portal backend via the shared {@code portal-model} entities.
 */
@Repository
public interface DataSetRepository extends JpaRepository<DataSet, UUID> {

  @Query("select d.id from DataSet d where d.dataPool.id in :poolIds")
  List<UUID> findIdsByDataPoolIdIn(@Param("poolIds") Collection<UUID> poolIds);

  @Query(
      value = "select id from datasets where right(replace(id::text, '-', ''), 8) = :suffix",
      nativeQuery = true)
  List<UUID> findIdsByUuidSuffix(@Param("suffix") String suffix);
}
