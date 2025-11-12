package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSetRepository extends NamedEntityRepository<DataSet, String> {

  /**
   * Find a dataset by ID and tenant ID with related entities eagerly fetched. This prevents N+1
   * query problems when loading datasets with their relationships.
   *
   * @param id the dataset ID
   * @param tenantId the tenant ID
   * @return the dataset with eagerly fetched owner
   */
  @EntityGraph(attributePaths = {"owner"})
  @Query("SELECT d FROM DataSet d WHERE d.id = :id AND d.tenantId = :tenantId")
  Optional<DataSet> findByIdAndTenantIdWithRelations(
      @Param("id") String id, @Param("tenantId") String tenantId);
}
