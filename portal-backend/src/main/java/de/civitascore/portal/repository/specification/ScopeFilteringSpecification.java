package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSpace;
import jakarta.persistence.criteria.JoinType;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specifications for scope-based collection filtering (M5.5).
 *
 * <p>These specifications filter collection queries based on user's authorized scope IDs from OPA.
 * Used by services to implement collection-level access control.
 *
 * <p>Usage: Services override {@code preProcessQuery} to apply these specifications when
 * AllowedScopes is active and not wildcard.
 */
public final class ScopeFilteringSpecification {

  private ScopeFilteringSpecification() {}

  /**
   * Filter DataSets to only those belonging to allowed dataspaces.
   *
   * <p>Creates a JOIN on the dataset_dataspaces table and filters by dataspace IDs.
   *
   * @param allowedDataSpaceIds the dataspace IDs the user can access
   * @return specification that filters datasets by their dataspaces
   */
  public static Specification<DataSet> dataSetInDataSpaces(Set<UUID> allowedDataSpaceIds) {
    return (root, query, cb) -> {
      if (allowedDataSpaceIds == null || allowedDataSpaceIds.isEmpty()) {
        // No scopes = no results (return always-false predicate)
        return cb.disjunction();
      }
      // Use distinct to avoid duplicate results from the join
      query.distinct(true);
      var dataSpacesJoin = root.join("dataSpaces", JoinType.INNER);
      return dataSpacesJoin.get("id").in(allowedDataSpaceIds);
    };
  }

  /**
   * Filter DataSpaces to only those with allowed IDs.
   *
   * @param allowedDataSpaceIds the dataspace IDs the user can access
   * @return specification that filters dataspaces by their IDs
   */
  public static Specification<DataSpace> dataSpaceById(Set<UUID> allowedDataSpaceIds) {
    return (root, query, cb) -> {
      if (allowedDataSpaceIds == null || allowedDataSpaceIds.isEmpty()) {
        // No scopes = no results (return always-false predicate)
        return cb.disjunction();
      }
      return root.get("id").in(allowedDataSpaceIds);
    };
  }
}
