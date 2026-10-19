package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.util.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
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
        E extends DataSetOwnedEntity,
        S extends BaseSpec<E>>
    extends BaseController<I, O, E, S> {

  /**
   * Names this controller's entity in the {@code dataSetId} path-variable error and in the
   * not-found answer for an entity owned by another dataset.
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
  @ApiResponse(
      responseCode = "400",
      description = "Parent dataset is not in DRAFT",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "A saga is in flight on the parent dataset",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<O> create(@Valid @RequestBody I input) {
    return super.create(input);
  }

  @Override
  @ApiResponse(
      responseCode = "400",
      description = "Parent dataset is not in DRAFT",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "A saga is in flight on the parent dataset",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<O> update(@PathVariable UUID id, @Valid @RequestBody I input) {
    requireOwnedByPathDataSet(id);
    return super.update(id, input);
  }

  @Override
  @ApiResponse(
      responseCode = "400",
      description = "Parent dataset is not in DRAFT",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "A saga is in flight on the parent dataset",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<O> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    requireOwnedByPathDataSet(id);
    return super.patch(id, updates);
  }

  @Override
  @ApiResponse(
      responseCode = "400",
      description = "Parent dataset is not in DRAFT",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "A saga is in flight on the parent dataset",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
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
