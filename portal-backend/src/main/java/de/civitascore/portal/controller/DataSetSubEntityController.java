package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSetOwned;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.util.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;

/**
 * Abstract base controller for entities nested under a parent dataset at {@code
 * /datasets/{dataSetId}/...}.
 *
 * <p>Every id-addressed operation answers {@link ResourceNotFoundException} when the addressed
 * entity belongs to a different dataset than the path names, so a foreign id is indistinguishable
 * from an unknown one and cannot reveal the other dataset's state.
 *
 * @param <I> the input DTO type
 * @param <O> the output DTO type
 * @param <E> the JPA entity type
 * @param <S> the specification type used for filtering
 */
public abstract class DataSetSubEntityController<
        I extends DataSetOwnedInputDTO,
        O extends BaseOutputDTO,
        E extends BaseEntity & DataSetOwned,
        S extends BaseSpec<E>>
    extends BaseController<I, O, E, S> {

  /**
   * The entity class, for the {@code dataSetId} path-variable error messages.
   *
   * @return the entity class this controller manages
   */
  protected abstract Class<E> getEntityClass();

  @Override
  public ResponseEntity<O> getById(@PathVariable UUID id) {
    E entity = requireOwnedByPathDataSet(id);
    return ResponseEntity.ok(getAssembler().toOutput(entity));
  }

  @Override
  public ResponseEntity<O> update(@PathVariable UUID id, @Valid @RequestBody I input) {
    requireOwnedByPathDataSet(id);
    return super.update(id, input);
  }

  @Override
  public ResponseEntity<O> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    requireOwnedByPathDataSet(id);
    return super.patch(id, updates);
  }

  @Override
  public void delete(@PathVariable UUID id) {
    requireOwnedByPathDataSet(id);
    super.delete(id);
  }

  @Override
  protected I preProcessInput(I input) {
    input.setDataSetId(extractDataSetId());
    return super.preProcessInput(input);
  }

  /**
   * Verifies the addressed entity belongs to the dataset named in the path.
   *
   * @return the addressed entity
   * @throws ResourceNotFoundException if the entity does not exist or belongs to another dataset
   */
  private E requireOwnedByPathDataSet(UUID id) {
    UUID dataSetId = extractDataSetId();
    E entity = getService().findByIdOrThrow(id);
    if (!dataSetId.equals(entity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityClass().getSimpleName(), id);
    }
    return entity;
  }

  private UUID extractDataSetId() {
    return extractUUIDFromPathVariable("dataSetId", getEntityClass());
  }
}
