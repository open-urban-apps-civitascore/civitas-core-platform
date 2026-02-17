package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.base.BaseEntity;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specifications for scope-based collection filtering (M5.5).
 *
 * <p>These specifications filter collection queries based on user's authorized scope IDs from OPA.
 * Used by controllers to implement collection-level access control.
 *
 * <p>Usage: Controllers call {@code BaseController.applyScopeFilter()} with these specifications to
 * decorate the query spec before passing it to the service layer.
 */
public final class ScopeFilteringSpecification {

  private ScopeFilteringSpecification() {}

  /**
   * Filter any BaseEntity by allowed IDs.
   *
   * @param allowedIds the entity IDs the user can access
   * @return specification that filters entities by their IDs
   */
  public static <E extends BaseEntity> Specification<E> baseEntityById(Set<UUID> allowedIds) {
    return (root, query, cb) -> {
      if (allowedIds == null || allowedIds.isEmpty()) {
        return cb.disjunction();
      }
      return root.get("id").in(allowedIds);
    };
  }
}
