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
   * Deletes the dataset's layers. Safe against the {@code cascade = ALL} layers collections on
   * {@link de.civitascore.portal.model.entity.DataSet DataSet} and {@link
   * de.civitascore.portal.model.entity.DataSink DataSink} only because no fetch graph on {@code
   * DataSetRepository} initialises {@code layers} — an uninitialised collection cannot re-insert
   * the deleted rows on flush. Adding {@code layers} to one of those graphs breaks that.
   *
   * <p>Derived rather than a bulk {@code @Query}: load-then-delete goes through per-entity removal,
   * so JPA clears the {@code layer_alternative_styles} join rows itself and the persistence context
   * stays in step with the database instead of silently diverging from an out-of-band cascade.
   */
  int deleteByDataSetId(UUID dataSetId);
}
