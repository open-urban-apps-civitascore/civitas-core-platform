package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.model.output.assembler.RoleAssembler;
import de.civitascore.portal.repository.specification.RoleSpec;
import de.civitascore.portal.service.RoleService;
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

/** REST controller for managing role resources. */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleController extends BaseController<RoleInputDTO, RoleOutputDTO, Role, RoleSpec> {

  private final RoleService roleService;
  private final RoleAssembler roleAssembler;

  /**
   * Retrieves a paginated list of roles with optional filtering by name, description, role type, or
   * free-text search.
   *
   * @param spec the role search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of role output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<RoleOutputDTO>> getAll(
      @ParameterObject RoleSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /** {@inheritDoc} */
  @Override
  protected RoleService getService() {
    return roleService;
  }

  /** {@inheritDoc} */
  @Override
  protected RoleAssembler getAssembler() {
    return roleAssembler;
  }
}
