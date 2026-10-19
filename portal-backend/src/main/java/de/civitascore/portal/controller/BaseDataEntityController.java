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
import de.civitascore.portal.service.BaseDataEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.JsonNode;

/**
 * Abstract base controller for data-entity resources that support scope-based access control.
 *
 * <p>Extends {@link BaseController} with automatic scope filtering on collection queries and an
 * endpoint for retrieving {@link Assignment}s scoped to the entity. Subclasses must implement
 * {@link #getScopeType()} to declare their scope category.
 *
 * @param <I> the input DTO type
 * @param <M> the metadata input DTO type: the part of {@code I} that stays changeable after release
 * @param <O> the output DTO type
 * @param <E> the JPA data-entity type
 * @param <S> the specification type used for filtering
 */
public abstract class BaseDataEntityController<
        I extends M,
        M extends BaseDataEntityInputDTO,
        O extends BaseOutputDTO,
        E extends BaseDataEntity,
        S extends BaseSpec<E>>
    extends BaseController<I, O, E, S> {

  @Autowired private AssignmentService assignmentService;
  @Autowired private AssignmentAssembler assignmentAssembler;
  @Autowired private ObjectProvider<AllowedScopes> allowedScopesProvider;

  /**
   * Returns the scope type that identifies this data entity category for authorization filtering.
   *
   * @return the {@link ScopeType} for this controller's entity
   */
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
    Specification<E> scopeFilter = scopeSpecification(scopes);
    return spec == null ? scopeFilter : spec.and(scopeFilter);
  }

  /**
   * Builds the scope-filter specification for this entity type. The default filters by directly
   * authorized scope IDs; subclasses may widen it (e.g. {@code DataSetController} ORs in datapool
   * membership for Epic 1 union inheritance). Called only for non-wildcard, scoped requests.
   *
   * @param scopes the resolved per-request scope information
   * @return the specification restricting the collection to authorized entities
   */
  protected Specification<E> scopeSpecification(AllowedScopes scopes) {
    return ScopeFilteringSpecification.baseEntityById(scopes.getScopeIds());
  }

  /**
   * Retrieves all role assignments scoped to the specified entity.
   *
   * @param id the UUID of the entity whose assignments to retrieve
   * @return a list of assignment output DTOs with HTTP 200 status
   */
  @GetMapping("/{id}/assignments")
  @Operation(
      operationId = "get{Entity}Assignments",
      summary = "Get assignments",
      description = "Returns all role assignments scoped to this {entity}.")
  @ApiResponse(responseCode = "200", description = "Assignments returned successfully")
  @ApiResponse(
      responseCode = "404",
      description = "{Entity} not found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<List<AssignmentOutputDTO>> getAssignments(@PathVariable UUID id) {
    getService().findByIdOrThrow(id);
    List<Assignment> assignments =
        assignmentService.findAllByScopeTypeAndScopeId(getScopeType(), id);
    List<AssignmentOutputDTO> output =
        assignments.stream().map(assignmentAssembler::toOutput).toList();
    return ResponseEntity.ok(output);
  }

  @Override
  protected abstract BaseDataEntityService<E, I, M> getService();

  @PostMapping("/{id}/release")
  @Operation(
      operationId = "release{Entity}",
      summary = "Release",
      description = "Transitions the entity from DRAFT to AVAILABLE status.")
  public ResponseEntity<O> release(@PathVariable UUID id) {
    E released = getService().release(id);
    O output = getAssembler().toOutput(released);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unrelease")
  @Operation(
      operationId = "unrelease{Entity}",
      summary = "Unrelease",
      description = "Transitions the entity from AVAILABLE back to DRAFT status.")
  public ResponseEntity<O> unrelease(@PathVariable UUID id) {
    E unreleased = getService().unrelease(id);
    O output = getAssembler().toOutput(unreleased);
    return ResponseEntity.ok(output);
  }

  @PatchMapping("/{id}/released/meta")
  @Operation(
      operationId = "updateReleased{Entity}Meta",
      summary = "Update released metadata",
      description =
          "Applies a JSON merge patch to the metadata of a released entity. An omitted field keeps"
              + " its value. A field that is not metadata answers 400, and so does null for"
              + " assignments or datapoolScope. Only works on entities that are not in DRAFT"
              + " status.")
  public ResponseEntity<O> updateReleasedMeta(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    E updated = getService().updateReleasedMeta(id, patchMeta(id, updates));
    O output = getAssembler().toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /**
   * Applies a metadata patch to the current metadata of the entity.
   *
   * @param id the UUID of the entity
   * @param updates the JSON node containing the metadata fields to update
   * @return the patched and validated metadata
   */
  protected M patchMeta(UUID id, JsonNode updates) {
    M current = getService().toMetaInput(getService().findByIdOrThrow(id));
    return mergePatch(id, current, updates);
  }
}
