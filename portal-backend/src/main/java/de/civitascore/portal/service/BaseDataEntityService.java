package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.security.AssignmentChangeAuthorizer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

/**
 * Base service for entities that carry scoped {@link Assignment} collections. Extends {@link
 * BaseService} by converting assignment input DTOs to entities via the {@link AssignmentFactory}
 * during the post-conversion lifecycle hook, authorized by the {@link AssignmentChangeAuthorizer}.
 *
 * <p>{@link #findAll} and {@link #findOne} answer only what the Scopes of the current request allow
 * and need an active request. Use the repository for an unrestricted query.
 */
public abstract class BaseDataEntityService<
        E extends BaseDataEntity, I extends BaseDataEntityInputDTO>
    extends BaseService<E, I> {

  /**
   * Returns the assignment factory used to build assignment entities from input DTOs.
   *
   * @return the assignment factory
   */
  protected abstract AssignmentFactory getAssignmentFactory();

  protected abstract AssignmentChangeAuthorizer getAssignmentChangeAuthorizer();

  protected abstract ObjectProvider<AllowedScopes> getAllowedScopes();

  /**
   * Returns the entities that a caller with the given, non-wildcard Scopes may see.
   *
   * @param scopes the Scopes of the current request
   * @return the specification of the visible entities; by default the entities whose id is an
   *     allowed Scope
   */
  protected Specification<E> scopeSpecification(AllowedScopes scopes) {
    return ScopeFilteringSpecification.baseEntityById(scopes.getScopeIds());
  }

  @Override
  protected final Specification<E> preProcessQuery(Specification<E> spec, Pageable pageable) {
    return restrictToAllowedScopes(spec);
  }

  @Override
  public Optional<E> findOne(Specification<E> spec) {
    return super.findOne(restrictToAllowedScopes(spec));
  }

  private Specification<E> restrictToAllowedScopes(Specification<E> spec) {
    AllowedScopes scopes = getAllowedScopes().getObject();
    if (!scopes.isHeaderPresent()) {
      throw new AccessDeniedException(
          "Missing required " + AllowedScopesFilter.HEADER_NAME + " header");
    }
    if (scopes.isWildcard()) {
      return spec;
    }
    Specification<E> filter = scopeSpecification(scopes);
    return spec == null ? filter : spec.and(filter);
  }

  /**
   * Replaces the data entity's assignments with those of the input after the base DTO-to-entity
   * conversion.
   *
   * @param entity the data entity
   * @param input the input DTO containing assignment definitions
   * @return the entity with resolved assignments
   */
  @Override
  protected E postConvertToEntity(E entity, I input) {
    replaceAssignments(entity, input.getAssignments());
    return super.postConvertToEntity(entity, input);
  }

  /**
   * Replaces the entity's assignments with the requested ones, once the caller is authorized for
   * every assignment added or removed. The only path by which a data entity's request body changes
   * its assignments.
   *
   * @param entity the data entity
   * @param requested the requested assignments; {@code null} leaves them untouched
   * @throws org.springframework.security.access.AccessDeniedException if the caller may not add or
   *     remove one of the changed assignments
   */
  protected final void replaceAssignments(E entity, Set<AssignmentScopedInputDTO> requested) {
    if (requested == null) {
      return;
    }
    Set<Assignment> assignments =
        requested.stream()
            .map(dto -> getAssignmentFactory().build(dto))
            .collect(Collectors.toSet());
    getAssignmentChangeAuthorizer().authorizeReplacement(entity, assignments);
    entity.setAssignments(assignments);
  }
}
