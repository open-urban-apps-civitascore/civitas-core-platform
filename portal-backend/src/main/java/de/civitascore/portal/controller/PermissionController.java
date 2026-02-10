package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionSource;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.assembler.PermissionAssembler;
import de.civitascore.portal.repository.specification.PermissionSpec;
import de.civitascore.portal.service.PermissionService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission management endpoints")
public class PermissionController {

  private final PermissionService permissionService;
  private final PermissionAssembler permissionAssembler;

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
        schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z")),
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "read")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Allows read access")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "read")),
    @Parameter(
        name = "permissionType",
        description = "Filter by permission type (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = PermissionType.class)),
    @Parameter(
        name = "category",
        description = "Filter by category (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = PermissionCategory.class)),
    @Parameter(
        name = "source",
        description = "Filter by source (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(implementation = PermissionSource.class))
  })
  @GetMapping
  public ResponseEntity<List<PermissionOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") PermissionSpec spec,
      @SortDefault(sort = "name") Sort sort) {
    List<Permission> entities = getService().findAll(spec, sort);
    List<PermissionOutputDTO> output =
        entities.stream().map(entity -> getAssembler().toOutput(entity)).toList();
    return ResponseEntity.ok(output);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PermissionOutputDTO> getById(@PathVariable UUID id) {
    Permission entity = getService().findByIdOrThrow(id);
    PermissionOutputDTO output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

  protected PermissionService getService() {
    return permissionService;
  }

  protected PermissionAssembler getAssembler() {
    return permissionAssembler;
  }
}
