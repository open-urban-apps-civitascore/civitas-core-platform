package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.model.output.assembler.BaseAssembler;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.service.BaseService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Abstract base controller providing standard CRUD operations for all entity types.
 *
 * <p>Subclasses must supply a {@link BaseService} and {@link BaseAssembler} via the template
 * methods {@link #getService()} and {@link #getAssembler()}. Override {@link
 * #preProcessInput(BaseInputDTO)} to transform the input DTO before create/update, or {@link
 * #patchInput} to customize JSON-merge patch behaviour.
 *
 * @param <I> the input DTO type
 * @param <O> the output DTO type
 * @param <E> the JPA entity type
 * @param <S> the specification type used for filtering
 */
@Validated
@RequestMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@FieldDefaults(level = lombok.AccessLevel.PROTECTED)
public abstract class BaseController<
    I extends BaseInputDTO, O extends BaseOutputDTO, E extends BaseEntity, S extends BaseSpec<E>> {

  /**
   * Returns the service responsible for business logic on the managed entity.
   *
   * @return the service instance
   */
  abstract BaseService<E, I> getService();

  /**
   * Returns the assembler that converts between entity and output DTO representations.
   *
   * @return the assembler instance
   */
  protected abstract BaseAssembler<E, O, UUID> getAssembler();

  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected Validator validator;

  @Operation(
      summary = "List all {entities}",
      description =
          "Returns a paginated, filterable list of {entities}. Supports sorting and specification-based filtering.")
  @ApiResponse(responseCode = "200", description = "Page of {entities} returned successfully")
  @Parameters({
    @Parameter(
        name = "id",
        description = "Filter by one or more IDs (comma-separated).",
        example = "1,2,3",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "1,2,3")),
    @Parameter(
        name = "createdAtFrom",
        description = "Filter by creation date greater than or equal to this value (ISO 8601).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z")),
    @Parameter(
        name = "createdAtTo",
        description = "Filter by creation date less than or equal to this value (ISO 8601).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z")),
    @Parameter(
        name = "modifiedAtFrom",
        description = "Filter by modification date greater than or equal to this value (ISO 8601).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z")),
    @Parameter(
        name = "modifiedAtTo",
        description = "Filter by modification date less than or equal to this value (ISO 8601).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z"))
  })
  /**
   * Retrieves a paginated, filterable list of all entities matching the given specification.
   *
   * @param spec the specification used for filtering results
   * @param pageable pagination and sorting parameters
   * @return a page of output DTOs with HTTP 200 status
   */
  @GetMapping
  public ResponseEntity<Page<O>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") S spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return getAll((Specification<E>) spec, pageable);
  }

  /**
   * Internal implementation of the paginated list operation using a JPA {@link Specification}.
   *
   * <p>Subclasses may override this method to apply additional filtering (e.g. scope-based access
   * control) before delegating to the service layer.
   *
   * @param spec the JPA specification for filtering
   * @param pageable pagination and sorting parameters
   * @return a page of output DTOs with HTTP 200 status
   */
  protected ResponseEntity<Page<O>> getAll(Specification<E> spec, Pageable pageable) {
    Page<E> entities = getService().findAll(spec, pageable);
    Page<O> outputs = getAssembler().toOutput(entities);
    return ResponseEntity.ok(outputs);
  }

  @Operation(
      summary = "Get {entity} by ID",
      description = "Returns a single {entity} identified by its UUID.")
  @ApiResponse(responseCode = "200", description = "{Entity} returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "{Entity} not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  /**
   * Retrieves a single entity by its unique identifier.
   *
   * @param id the UUID of the entity to retrieve
   * @return the entity output DTO with HTTP 200 status
   */
  @GetMapping("/{id}")
  public ResponseEntity<O> getById(@PathVariable UUID id) {
    E entity = getService().findByIdOrThrow(id);
    O output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

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
  /**
   * Creates a new entity from the provided input DTO.
   *
   * @param input the validated input DTO containing the entity data
   * @return the created entity output DTO with HTTP 201 status and a Location header
   */
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
  /**
   * Fully replaces an existing entity with the provided input DTO.
   *
   * @param id the UUID of the entity to replace
   * @param input the validated input DTO containing the replacement data
   * @return the updated entity output DTO with HTTP 200 status
   */
  @PutMapping("/{id}")
  public ResponseEntity<O> update(@PathVariable UUID id, @Valid @RequestBody I input) {
    I preProcessedInput = preProcessInput(input);
    E updated = getService().update(id, preProcessedInput);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

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
  /**
   * Applies a partial JSON-merge patch to an existing entity.
   *
   * @param id the UUID of the entity to patch
   * @param updates the JSON node containing the fields to update
   * @return the patched entity output DTO with HTTP 200 status
   * @throws IOException if there is an error during JSON processing
   */
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
  /**
   * Permanently deletes an entity by its unique identifier.
   *
   * @param id the UUID of the entity to delete
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    getService().deleteById(id);
  }

  /**
   * Extracts URI path variables from the current HTTP request.
   *
   * @return an unmodifiable map of path variable names to their values, or an empty map if
   *     unavailable
   */
  protected Map<String, String> extractPathVariables() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

    if (Objects.isNull(attributes)) {
      return Map.of();
    }

    @SuppressWarnings("unchecked")
    Map<String, String> pathVariables =
        (Map<String, String>)
            attributes.getRequest().getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return Objects.nonNull(pathVariables) ? pathVariables : Map.of();
  }

  /**
   * Helper method to apply JSON Patch updates to an existing DTO. By default, it uses Jackson's
   * ObjectMapper to read the updates into the current DTO. Subclasses can override this method to
   * implement custom patching logic if needed.
   *
   * @param currentDto the current state of the DTO before applying updates
   * @param current the current state of the entity before applying updates (in case it's needed for
   *     patching logic)
   * @param updates the JSON node containing the updates to be applied
   * @return the patched DTO after applying the updates
   * @throws IOException if there is an error during JSON processing
   */
  protected I patchInput(I currentDto, E current, JsonNode updates) throws IOException {
    return objectMapper.readerForUpdating(currentDto).readValue(updates);
  }

  /**
   * Pre-process the input DTO before creating/updating an entity. This method can be overridden by
   * subclasses to implement custom logic, e.g. when URL parameters need to be set on the input DTO
   * before conversion to entity.
   *
   * @param input the original input DTO
   * @return the processed input DTO to be used for entity creation/updating
   */
  protected I preProcessInput(I input) {
    return input;
  }
}
