package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.model.output.assembler.EntityAssembler;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.service.TenantAwareService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RequestMapping
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PROTECTED)
public abstract class BaseController<
    I extends BaseInputDTO,
    O extends BaseOutputDTO,
    E extends BaseEntity<ID>,
    S extends BaseSpec<E>,
    ID extends Serializable> {

  abstract TenantAwareService<E, ID, I> getService();

  protected abstract EntityAssembler<E, O, ID> getAssembler();

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
    Page<E> entities = getService().findAll(spec, pageable);
    Page<O> outputs = getAssembler().toOutput(entities);
    return ResponseEntity.ok(outputs);
  }

  @GetMapping("/{id}")
  public ResponseEntity<O> getById(@PathVariable ID id) {
    E entity = getService().findById(id);
    O output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<O> create(@Valid @RequestBody I input) {
    E created = getService().create(input);
    O output = getAssembler().toOutput(created);
    ID createdId = getAssembler().getIdFromOutput(output);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(createdId)
            .toUri();
    return ResponseEntity.created(location).body(output);
  }

  @PutMapping("/{id}")
  public ResponseEntity<O> update(@PathVariable ID id, @Valid @RequestBody I input) {
    E updated = getService().update(id, input);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<O> patch(@PathVariable ID id, @RequestBody I input) {
    E updated = getService().update(id, input);
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable ID id) {
    getService().deleteById(id);
  }
}
