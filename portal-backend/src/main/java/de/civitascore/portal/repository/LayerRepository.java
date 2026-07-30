package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Layer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Layer} entities. */
@Repository
public interface LayerRepository extends BaseRepository<Layer, UUID> {

  boolean existsByDataSinkId(UUID dataSinkId);

  Optional<Layer> findByDataSinkIdAndLayerName(UUID dataSinkId, String layerName);

  boolean existsByDefaultStyleIdOrAlternativeStylesId(UUID defaultStyleId, UUID alternativeStyleId);

  // clearAutomatically evicts any Layer already in the persistence context so a collection loaded
  // later in the same transaction is re-read from the DB — otherwise the cascade=ALL layers
  // collection on either owning side could re-insert the just-deleted rows on flush.
  @Modifying(clearAutomatically = true)
  int deleteByDataSetId(UUID dataSetId);
}
