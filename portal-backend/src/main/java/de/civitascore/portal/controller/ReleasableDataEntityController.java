package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.service.ReleasableDataEntityService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;

/**
 * Abstract base controller for data-entity resources with a release lifecycle. Adds the release,
 * unrelease and released-metadata endpoints to {@link BaseDataEntityController}.
 *
 * @param <I> the input DTO type
 * @param <M> the metadata input DTO type: the part of {@code I} that stays changeable after release
 * @param <O> the output DTO type
 * @param <E> the JPA data-entity type
 * @param <S> the specification type used for filtering
 */
public abstract class ReleasableDataEntityController<
        I extends M,
        M extends BaseDataEntityInputDTO,
        O extends BaseOutputDTO,
        E extends BaseDataEntity,
        S extends BaseSpec<E>>
    extends BaseDataEntityController<I, O, E, S> {

  @Override
  protected abstract ReleasableDataEntityService<E, I, M> getService();

  @PostMapping("/{id}/release")
  @Operation(
      operationId = "release{Entity}",
      summary = "Release",
      description = "Transitions the entity from DRAFT to AVAILABLE status.")
  public ResponseEntity<O> release(@PathVariable UUID id) {
    E released = getService().release(id);
    O output = getAssembler().toOutput(released);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unrelease")
  @Operation(
      operationId = "unrelease{Entity}",
      summary = "Unrelease",
      description = "Transitions the entity from AVAILABLE back to DRAFT status.")
  public ResponseEntity<O> unrelease(@PathVariable UUID id) {
    E unreleased = getService().unrelease(id);
    O output = getAssembler().toOutput(unreleased);
    return ResponseEntity.ok(output);
  }

  @PatchMapping("/{id}/released/meta")
  @Operation(
      operationId = "updateReleased{Entity}Meta",
      summary = "Update released metadata",
      description =
          "Applies a JSON merge patch to the metadata of a released entity. An omitted field keeps"
              + " its value. A field that is not metadata answers 400, and so does null for"
              + " assignments or datapoolScope. Only works on entities that are not in DRAFT"
              + " status.")
  public ResponseEntity<O> updateReleasedMeta(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    E updated = getService().updateReleasedMeta(id, patchMeta(id, updates));
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Applies a metadata patch to the current metadata of the entity.
   *
   * @param id the UUID of the entity
   * @param updates the JSON node containing the metadata fields to update
   * @return the patched and validated metadata
   */
  protected M patchMeta(UUID id, JsonNode updates) {
    M current = getService().toMetaInput(getService().findByIdOrThrow(id));
    return mergePatch(id, current, updates);
  }
}
