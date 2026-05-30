package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Abstract base controller providing standard CRUD operations for all entity types. For read-only
 * endpoints extend {@link BaseReadOnlyController} directly.
 *
 * @param <I> the input DTO type
 * @param <O> the output DTO type
 * @param <E> the JPA entity type
 * @param <S> the specification type used for filtering
 */
public abstract class BaseController<
        I extends BaseInputDTO,
        O extends BaseOutputDTO,
        E extends BaseEntity,
        S extends BaseSpec<E>>
    extends BaseReadOnlyController<I, O, E, S> {

  /**
   * Creates a new entity from the provided input DTO.
   *
   * @param input the validated input DTO containing the entity data
   * @return the created entity output DTO with HTTP 201 status and a Location header
   */
  @Operation(
      summary = "Create a new {entity}",
      description =
          "Creates a new {entity} and returns it with a Location header pointing to the new {entity} URI.")
  @ApiResponse(responseCode = "201", description = "{Entity} created successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (e.g. unique constraint violation)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<O> create(@Valid @RequestBody I input) {
    I preProcessedInput = preProcessInput(input);
    E created = getService().create(preProcessedInput);
    O output = getAssembler().toOutput(created);
    UUID createdId = getAssembler().getIdFromOutput(output);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(createdId)
            .toUri();
    return ResponseEntity.created(location).body(output);
  }

  /**
   * Fully replaces an existing entity with the provided input DTO.
   *
   * @param id the UUID of the entity to replace
   * @param input the validated input DTO containing the replacement data
   * @return the updated entity output DTO with HTTP 200 status
   */
  @Operation(
      summary = "Replace a {entity}",
      description = "Fully replaces an existing {entity} with the provided input.")
  @ApiResponse(responseCode = "200", description = "{Entity} updated successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "{Entity} not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (e.g. unique constraint violation)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping("/{id}")
  public ResponseEntity<O> update(@PathVariable UUID id, @Valid @RequestBody I input) {
    I preProcessedInput = preProcessInput(input);
    E updated = getService().update(id, preProcessedInput);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Applies a partial JSON-merge patch to an existing entity.
   *
   * @param id the UUID of the entity to patch
   * @param updates the JSON node containing the fields to update
   * @return the patched entity output DTO with HTTP 200 status
   * @throws IOException if there is an error during JSON processing
   */
  @Operation(
      summary = "Partially update a {entity}",
      description =
          "Applies a partial JSON update to an existing {entity}. Only provided fields are modified.")
  @ApiResponse(responseCode = "200", description = "{Entity} patched successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid input",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "{Entity} not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict (e.g. unique constraint violation)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PatchMapping("/{id}")
  public ResponseEntity<O> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    E current = getService().findByIdOrThrow(id);
    I currentDto = getAssembler().toInput(current);
    I patchedDto = patchInput(currentDto, current, updates);

    Set<ConstraintViolation<I>> violations = validator.validate(patchedDto);
    if (!violations.isEmpty()) {
      String message =
          violations.stream()
              .map(ConstraintViolation::getMessage)
              .reduce((a, b) -> a + ";\n" + b)
              .orElse("");
      throw new InvalidInputException(patchedDto.getClass().getSimpleName(), id, message);
    }

    patchedDto = preProcessInput(patchedDto);
    E updated = getService().update(id, patchedDto);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Permanently deletes an entity by its unique identifier.
   *
   * @param id the UUID of the entity to delete
   */
  @Operation(
      summary = "Delete a {entity}",
      description = "Permanently deletes a {entity} by its UUID.")
  @ApiResponse(responseCode = "204", description = "{Entity} deleted successfully")
  @ApiResponse(
      responseCode = "404",
      description = "{Entity} not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Conflict ({entity} still in use)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    getService().deleteById(id);
  }

  protected I patchInput(I currentDto, E current, JsonNode updates) throws IOException {
    return objectMapper.readerForUpdating(currentDto).readValue(updates);
  }
}
