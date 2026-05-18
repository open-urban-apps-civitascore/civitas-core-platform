package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Layer;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Layer} entities. */
@Repository
public interface LayerRepository extends BaseRepository<Layer, UUID> {

  boolean existsByDataSinkId(UUID dataSinkId);

  boolean existsByDataSetIdAndLayerName(UUID dataSetId, String layerName);

  boolean existsByDefaultStyleId(UUID styleId);

  boolean existsByDefaultStyleIdOrAlternativeStylesId(UUID defaultStyleId, UUID alternativeStyleId);
}
