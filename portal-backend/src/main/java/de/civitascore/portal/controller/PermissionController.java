package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import de.civitascore.portal.model.output.assembler.PermissionAssembler;
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
import org.springframework.http.ResponseEntity;
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

  /**
   * Retrieves a sorted, filterable list of all permissions.
   *
   * @param spec the permission search/filter specification
   * @param sort the sort parameters (defaults to sorting by name)
   * @return a list of permission output DTOs with HTTP 200 status
   */
  @Operation(
      summary = "List all permissions",
      description = "Returns a filterable, sorted list of permissions.")
  @GetMapping
  public ResponseEntity<List<PermissionOutputDTO>> getAll(
      @ParameterObject PermissionSpec spec, @SortDefault(sort = "name") Sort sort) {
    List<Permission> entities = getService().findAll(spec, sort);
    List<PermissionOutputDTO> output =
        entities.stream().map(entity -> getAssembler().toOutput(entity)).toList();
    return ResponseEntity.ok(output);
  }

  /**
   * Retrieves a single permission by its unique identifier.
   *
   * @param id the UUID of the permission to retrieve
   * @return the permission output DTO with HTTP 200 status
   */
  @Operation(
      summary = "Get permission by ID",
      description = "Returns a single permission identified by its UUID.")
  @GetMapping("/{id}")
  public ResponseEntity<PermissionOutputDTO> getById(@PathVariable UUID id) {
    Permission entity = getService().findByIdOrThrow(id);
    PermissionOutputDTO output = getAssembler().toOutput(entity);
    return ResponseEntity.ok(output);
  }

  /**
   * Returns the permission service for business logic operations.
   *
   * @return the permission service instance
   */
  protected PermissionService getService() {
    return permissionService;
  }

  /**
   * Returns the assembler that converts between permission entities and output DTOs.
   *
   * @return the permission assembler instance
   */
  protected PermissionAssembler getAssembler() {
    return permissionAssembler;
  }
}
