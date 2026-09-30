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
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.InvalidNullException;

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
    I patchedDto;
    try {
      patchedDto = patchInput(currentDto, current, updates);
    } catch (InvalidNullException e) {
      throw nullNotAllowed(id, currentDto, e);
    }
    patchedDto = validated(id, patchedDto);

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

  /**
   * Applies a JSON merge patch to the current state and validates the result. A field that the
   * patch omits keeps its current value. The type of {@code current} defines which fields a patch
   * may contain.
   *
   * @param id the UUID of the patched entity, used in the error
   * @param current the current state, updated in place
   * @param updates the JSON node containing the fields to update
   * @return the patched and validated state
   * @throws InvalidInputException if the patch contains a field that {@code current} does not have,
   *     even with a {@code null} value, sets a field to {@code null} that rejects it, sets a field
   *     to a value of the wrong type, or the patched state violates a Bean Validation constraint
   */
  protected <T> T mergePatch(UUID id, T current, JsonNode updates) {
    UnknownFieldCollector unknownFields = new UnknownFieldCollector();
    T patched;
    try {
      patched =
          objectMapper.readerForUpdating(current).withHandler(unknownFields).readValue(updates);
    } catch (InvalidNullException e) {
      throw nullNotAllowed(id, current, e);
    } catch (JacksonException e) {
      throw invalidValue(id, current, e);
    }
    if (!unknownFields.pointers.isEmpty()) {
      throw new InvalidInputException(
          current.getClass().getSimpleName(),
          id,
          "Cannot change after release: " + String.join(", ", unknownFields.pointers));
    }
    return validated(id, patched);
  }

  private static InvalidInputException nullNotAllowed(
      UUID id, Object current, InvalidNullException e) {
    return new InvalidInputException(
        current.getClass().getSimpleName(), id, "Must not be null: " + jsonPointer(e));
  }

  private static InvalidInputException invalidValue(UUID id, Object current, JacksonException e) {
    return new InvalidInputException(
        current.getClass().getSimpleName(), id, "Invalid value: " + jsonPointer(e));
  }

  private static String jsonPointer(JacksonException e) {
    StringBuilder pointer = new StringBuilder();
    for (JacksonException.Reference reference : e.getPath()) {
      pointer.append('/');
      if (reference.getPropertyName() != null) {
        pointer.append(reference.getPropertyName());
      } else {
        pointer.append(reference.getIndex());
      }
    }
    return pointer.toString();
  }

  private static final class UnknownFieldCollector extends DeserializationProblemHandler {
    private final List<String> pointers = new ArrayList<>();

    @Override
    public boolean handleUnknownProperty(
        DeserializationContext context,
        JsonParser parser,
        ValueDeserializer<?> deserializer,
        Object beanOrClass,
        String propertyName) {
      pointers.add(parser.streamReadContext().pathAsPointer().toString());
      parser.skipChildren();
      return true;
    }
  }

  private <T> T validated(UUID id, T dto) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      String message =
          violations.stream()
              .map(ConstraintViolation::getMessage)
              .reduce((a, b) -> a + ";\n" + b)
              .orElse("");
      throw new InvalidInputException(dto.getClass().getSimpleName(), id, message);
    }
    return dto;
  }
}
