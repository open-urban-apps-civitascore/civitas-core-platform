package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Catalog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogRepository extends NamedEntityRepository<Catalog, UUID> {

  /**
   * Find a catalog by ID with related entities eagerly fetched. This prevents N+1 query problems
   * when loading catalogs with their relationships.
   *
   * @param id the catalog ID
   * @return the catalog with eagerly fetched parentCatalog
   */
  @EntityGraph(attributePaths = {"parentCatalog"})
  @Query("SELECT c FROM Catalog c WHERE c.id = :id")
  Optional<Catalog> findByIdWithRelations(@Param("id") UUID id);
}
