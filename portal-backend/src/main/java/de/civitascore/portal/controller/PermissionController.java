package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.assembler.PermissionAssembler;
import de.civitascore.portal.repository.specification.PermissionSpec;
import de.civitascore.portal.service.PermissionService;
import de.civitascore.portal.service.TenantAwareService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission management endpoints")
public class PermissionController
    extends BaseController<
        PermissionInputDTO, PermissionOutputDTO, Permission, PermissionSpec, String> {

  private final PermissionService permissionService;
  private final PermissionAssembler permissionAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "read")),
    @Parameter(
        name = "title",
        description = "Filter by title (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Read Access")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Allows read access")),
    @Parameter(
        name = "q",
        description = "Search in title or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "read"))
  })
  @Override
  public ResponseEntity<Page<PermissionOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") PermissionSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected TenantAwareService<Permission, String, PermissionInputDTO> getService() {
    return permissionService;
  }

  @Override
  protected PermissionAssembler getAssembler() {
    return permissionAssembler;
  }
}
