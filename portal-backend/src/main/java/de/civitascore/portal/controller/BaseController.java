package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.model.output.assembler.BaseAssembler;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.service.BaseService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Validated
@RequestMapping
@FieldDefaults(level = lombok.AccessLevel.PROTECTED)
public abstract class BaseController<
    I extends BaseInputDTO, O extends BaseOutputDTO, E extends BaseEntity, S extends BaseSpec<E>> {

  abstract BaseService<E, I> getService();

  protected abstract BaseAssembler<E, O, UUID> getAssembler();

  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected Validator validator;

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

  @GetMapping("/{id}")
  public ResponseEntity<O> getById(@PathVariable UUID id) {
    E entity = getService().findByIdOrThrow(id);
    O output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

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

  @PutMapping("/{id}")
  public ResponseEntity<O> update(@PathVariable UUID id, @Valid @RequestBody I input) {
    I preProcessedInput = preProcessInput(input);
    E updated = getService().update(id, preProcessedInput);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<O> patch(@PathVariable UUID id, @RequestBody JsonNode updates)
      throws IOException {
    E current = getService().findByIdOrThrow(id);
    I currentDto = getAssembler().toInput(current);
    I patchedDto = patchInput(currentDto, current, updates);
    patchedDto = preProcessInput(patchedDto);

    Set<ConstraintViolation<I>> violations = validator.validate(patchedDto);
    if (!violations.isEmpty()) {
      throw new InvalidInputException(
          patchedDto.getClass().getSimpleName(), id, violations.iterator().next().getMessage());
    }
    E updated = getService().update(id, patchedDto);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    getService().deleteById(id);
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
