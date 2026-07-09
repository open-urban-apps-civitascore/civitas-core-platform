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
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

/**
 * Abstract base controller exposing only GET (read) operations. Subclasses that require write
 * operations should extend {@link BaseController} instead.
 *
 * @param <I> the input DTO type
 * @param <O> the output DTO type
 * @param <E> the JPA entity type
 * @param <S> the specification type used for filtering
 */
@Validated
@RequestMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@FieldDefaults(level = lombok.AccessLevel.PROTECTED)
public abstract class BaseReadOnlyController<
    I extends BaseInputDTO, O extends BaseOutputDTO, E extends BaseEntity, S extends BaseSpec<E>> {

  abstract BaseService<E, I> getService();

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
  @GetMapping
  public ResponseEntity<Page<O>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") S spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return getAll((Specification<E>) spec, pageable);
  }

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
  @GetMapping("/{id}")
  public ResponseEntity<O> getById(@PathVariable UUID id) {
    E entity = getService().findByIdOrThrow(id);
    O output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

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

  protected UUID extractUUIDFromPathVariable(
      @NotBlank String parameterName, @NotNull Class<E> clazz) {
    String rawId =
        Optional.ofNullable(extractPathVariables().get(parameterName))
            .orElseThrow(
                () ->
                    new InvalidInputException(
                        clazz.getSimpleName(),
                        parameterName,
                        String.format("Missing or invalid %s in path variables", parameterName)));

    try {
      return UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      throw new InvalidInputException(
          clazz.getSimpleName(),
          parameterName,
          String.format("Missing or invalid %s in path variables", parameterName));
    }
  }

  protected I preProcessInput(I input) {
    return input;
  }
}
