package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Restricts a list query of data elements to the scopes that the {@link
 * AllowedScopesFilter#HEADER_NAME} header of the current request allows. A request without the
 * header is denied, so a list never answers unfiltered when the authorization layer did not resolve
 * the scopes. A wildcard header leaves the query unchanged.
 */
@Component
class ScopeFilter {

  private final ObjectProvider<AllowedScopes> allowedScopes;

  ScopeFilter(ObjectProvider<AllowedScopes> allowedScopes) {
    this.allowedScopes = allowedScopes;
  }

  /**
   * Restricts {@code spec} to the entities whose id is an allowed scope.
   *
   * @param spec the query, or {@code null} for all entities
   * @throws AccessDeniedException if the request has no scope header
   */
  <E extends BaseDataEntity> Specification<E> restrict(Specification<E> spec) {
    return restrict(
        spec, scopes -> ScopeFilteringSpecification.baseEntityById(scopes.getScopeIds()));
  }

  /**
   * Restricts {@code spec} to the entities that {@code scopeSpecification} allows for the scopes of
   * the request. {@code scopeSpecification} is not called for a wildcard header.
   *
   * @param spec the query, or {@code null} for all entities
   * @throws AccessDeniedException if the request has no scope header
   */
  <E> Specification<E> restrict(
      Specification<E> spec, Function<AllowedScopes, Specification<E>> scopeSpecification) {
    AllowedScopes scopes = allowedScopes.getObject();
    if (!scopes.isHeaderPresent()) {
      throw new AccessDeniedException(
          "Missing required " + AllowedScopesFilter.HEADER_NAME + " header");
    }
    if (scopes.isWildcard()) {
      return spec;
    }
    Specification<E> filter = scopeSpecification.apply(scopes);
    return spec == null ? filter : spec.and(filter);
  }
}
