package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Layer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Layer} entities. */
@Repository
public interface LayerRepository extends BaseRepository<Layer, UUID> {

  boolean existsByDataSinkId(UUID dataSinkId);

  boolean existsByDataSetId(UUID dataSetId);

  Optional<Layer> findByDataSetIdAndLayerName(UUID dataSetId, String layerName);

  boolean existsByDefaultStyleIdOrAlternativeStylesId(UUID defaultStyleId, UUID alternativeStyleId);

  /**
   * Deletes the dataset's layers, including their {@code layer_alternative_styles} join rows.
   * Callers holding an initialised {@code layers} collection on the DataSet or DataSink must not
   * flush it afterwards — its {@code cascade = ALL} would re-insert the rows.
   */
  int deleteByDataSetId(UUID dataSetId);
}
