package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.model.output.assembler.RoleAssembler;
import de.civitascore.portal.repository.specification.RoleSpec;
import de.civitascore.portal.service.RoleService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleController extends BaseController<RoleInputDTO, RoleOutputDTO, Role, RoleSpec> {

  private final RoleService roleService;
  private final RoleAssembler roleAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "admin")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Full access")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "admin"))
  })
  @Override
  public ResponseEntity<Page<RoleOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") RoleSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected RoleService getService() {
    return roleService;
  }

  @Override
  protected RoleAssembler getAssembler() {
    return roleAssembler;
  }
}
