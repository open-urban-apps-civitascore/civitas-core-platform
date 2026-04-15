package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Distribution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Distribution} entities. */
@Repository
public interface DistributionRepository extends BaseRepository<Distribution, UUID> {

  /**
   * Find a distribution by ID with related entities eagerly fetched. This prevents N+1 query
   * problems when loading distributions with their relationships.
   *
   * @param id the distribution ID
   * @return the distribution with eagerly fetched dataSet
   */
  @EntityGraph(attributePaths = {"dataSet"})
  @Override
  @NonNull Optional<Distribution> findById(@NonNull UUID id);

  /**
   * Find all distributions for a specific dataset.
   *
   * @param dataSetId the dataset ID
   * @return list of distributions
   */
  @Query("SELECT d FROM Distribution d WHERE d.dataSet.id = :dataSetId")
  List<Distribution> findByDataSetId(@Param("dataSetId") UUID dataSetId);
}
