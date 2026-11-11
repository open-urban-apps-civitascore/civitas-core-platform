package de.civitascore.portal.controller;

import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.assembler.PermissionAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.PermissionSpec;
import de.civitascore.portal.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for read-only access to permission resources. */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission management endpoints")
public class PermissionController {

  private final PermissionService permissionService;
  private final PermissionAssembler permissionAssembler;

  @GetMapping
  @Operation(
      summary = "List all permissions",
      description = "Returns a filterable, sorted list of permissions.")
  public List<PermissionOutputDTO> listPermissions(
      @ParameterObject PermissionSpec spec, @SortDefault(sort = "name") Sort sort) {
    return permissionService.findAll(spec, sort).stream()
        .map(permissionAssembler::toOutput)
        .toList();
  }

  @GetMapping("/{permissionId}")
  @Operation(
      summary = "Get permission by ID",
      description = "Returns a single permission identified by its UUID.")
  @Problems(ProblemType.NOT_FOUND)
  public PermissionOutputDTO getPermission(@PathVariable UUID permissionId) {
    return permissionAssembler.toOutput(permissionService.findByIdOrThrow(permissionId));
  }
}
