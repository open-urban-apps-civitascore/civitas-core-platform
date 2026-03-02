package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public abstract class BaseDataEntityController<
        I extends BaseDataEntityInputDTO,
        O extends BaseOutputDTO,
        E extends BaseDataEntity,
        S extends BaseSpec<E>>
    extends BaseController<I, O, E, S> {

  @Autowired private AssignmentService assignmentService;
  @Autowired private AssignmentAssembler assignmentAssembler;
  @Autowired private ObjectProvider<AllowedScopes> allowedScopesProvider;

  protected abstract ScopeType getScopeType();

  /**
   * Applies scope filtering to all data entity collection queries. Subclasses inherit this
   * automatically — no need to call {@code applyScopeFilter()} in their own {@code getAll()}
   * overrides.
   */
  @Override
  protected ResponseEntity<Page<O>> getAll(Specification<E> spec, Pageable pageable) {
    return super.getAll(applyScopeFilter(spec), pageable);
  }

  /**
   * Apply scope-based filtering to a specification.
   *
   * <p>Throws {@link AccessDeniedException} if the {@code X-Allowed-Scope-Ids} header is missing.
   * If wildcard, returns the spec unchanged. Otherwise combines the spec with an IN-clause filter
   * for the authorized scope IDs.
   */
  protected Specification<E> applyScopeFilter(Specification<E> spec) {
    AllowedScopes scopes = allowedScopesProvider.getObject();
    if (!scopes.isHeaderPresent()) {
      throw new AccessDeniedException(
          "Missing required " + AllowedScopesFilter.HEADER_NAME + " header");
    }
    if (scopes.isWildcard()) {
      return spec;
    }
    Specification<E> scopeFilter = ScopeFilteringSpecification.baseEntityById(scopes.getScopeIds());
    return spec == null ? scopeFilter : spec.and(scopeFilter);
  }

  @GetMapping("/{id}/assignments")
  @Operation(
      summary = "Get assignments",
      description = "Returns all role assignments scoped to this resource.")
  public ResponseEntity<List<AssignmentOutputDTO>> getAssignments(@PathVariable UUID id) {
    getService().findByIdOrThrow(id);
    List<Assignment> assignments =
        assignmentService.findAllByScopeTypeAndScopeId(getScopeType(), id);
    List<AssignmentOutputDTO> output =
        assignments.stream().map(assignmentAssembler::toOutput).toList();
    return ResponseEntity.ok(output);
  }
}
