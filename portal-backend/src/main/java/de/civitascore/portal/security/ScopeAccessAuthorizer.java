package de.civitascore.portal.security;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.specification.ScopeFilteringSpecification;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Authorizes that the current caller may reference scope-bearing entities by ID in a request body.
 *
 * <p>OPA authorizes the endpoint and emits a scope header, but it never sees the request body — and
 * that header is typed to the route's own scope (derived from the first path segment). A body that
 * references a <em>different</em> scope type (e.g. {@code dataSourceIds} on the DATASET-rooted
 * {@code POST /datasets/{id}/pipelines}, or {@code datapoolIds} on the DATASOURCE-rooted {@code
 * /datasources}) is therefore not covered by the header. This authorizer closes that gap.
 *
 * <p>Grant rule for a requested set of {@code scopeType} IDs:
 *
 * <ul>
 *   <li>A TENANT caller (header wildcard {@code *}) may reference anything — OPA only emits the
 *       wildcard for a tenant-wide grant, which cascades to every resource type.
 *   <li>Otherwise the caller's assignments are resolved from the database: an unscoped/TENANT
 *       assignment carrying the type's READ permission grants all; failing that, every referenced
 *       ID must be covered by an assignment of the matching scope type carrying that permission.
 *   <li>Referenced DATASOURCE IDs are additionally covered by a DATAPOOL-scoped assignment carrying
 *       DATASOURCE_READ when the data source is usable in that pool — the inheritance that lets a
 *       pool-scoped steward build pipelines from the pool's data sources. A data source usable in
 *       no pool needs a DATASOURCE-scoped or tenant-wide grant.
 *   <li>A missing scope header (direct backend access bypassing APISIX/OPA) denies.
 * </ul>
 *
 * <p>Every decision — grant and deny — is logged for audit.
 */
@Component
@Slf4j
public class ScopeAccessAuthorizer {

  private static final Map<ScopeType, PermissionName> READ_PERMISSIONS =
      Map.of(
          ScopeType.DATASOURCE, PermissionName.DATASOURCE_READ,
          ScopeType.DATAPOOL, PermissionName.DATAPOOL_READ,
          ScopeType.DATASTRUCTURE, PermissionName.DATASTRUCTURE_READ,
          ScopeType.DATASET, PermissionName.DATASET_READ);

  private final AssignmentService assignmentService;
  private final DataSourceRepository dataSourceRepository;
  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  public ScopeAccessAuthorizer(
      AssignmentService assignmentService,
      DataSourceRepository dataSourceRepository,
      ObjectProvider<AllowedScopes> allowedScopesProvider) {
    this.assignmentService = assignmentService;
    this.dataSourceRepository = dataSourceRepository;
    this.allowedScopesProvider = allowedScopesProvider;
  }

  /**
   * Authorizes that the current caller may reference every given entity of the given scope type.
   *
   * @param scopeType the scope type of the referenced entities (e.g. {@link ScopeType#DATASOURCE})
   * @param requestedIds the entity IDs referenced by the request body
   * @throws AccessDeniedException if the caller is unauthenticated, the scope header is absent, or
   *     the caller is not authorized for one of the referenced entities
   */
  public void authorizeReferences(ScopeType scopeType, Collection<UUID> requestedIds) {
    if (requestedIds == null || requestedIds.isEmpty()) {
      return;
    }

    AllowedScopes scopes = allowedScopesProvider.getObject();
    if (!scopes.isHeaderPresent()) {
      log.warn("{} access denied: no scope header present (direct backend access)", scopeType);
      throw new AccessDeniedException("Missing scope information for " + scopeType + " access");
    }

    String externalId = currentUserExternalId();

    // A wildcard header is a decision the PDP (OPA) has already made: the caller has tenant-wide
    // access, which cascades to every resource type. The backend only fills the gap where OPA
    // cannot see the body-referenced ids — it does not re-adjudicate a decision OPA already took.
    if (scopes.isWildcard()) {
      log.info(
          "{} access granted (tenant) for user {} referencing {}",
          scopeType,
          Encode.forJava(externalId),
          requestedIds);
      return;
    }

    PermissionName readPermission = READ_PERMISSIONS.get(scopeType);
    if (readPermission == null) {
      // Fail closed: an unmapped scope type is a wiring error, but a security guard must never
      // grant on an unrecognized input.
      log.error("{} access denied: no read permission mapped for scope type", scopeType);
      throw new AccessDeniedException("No read permission mapping for scope type " + scopeType);
    }
    List<Assignment> assignments = assignmentService.findAllByUserExternalId(externalId);

    // A non-wildcard caller may still hold a tenant/unscoped assignment for the referenced type
    // (OPA scopes its header to the route type, so it would not surface such a grant here).
    if (hasTenantWideRead(assignments, readPermission)) {
      log.info(
          "{} access granted (tenant assignment) for user {} referencing {}",
          scopeType,
          Encode.forJava(externalId),
          requestedIds);
      return;
    }

    Set<UUID> permittedIds = permittedScopeIds(assignments, scopeType, readPermission);
    List<UUID> notDirectlyPermitted =
        requestedIds.stream().filter(id -> !permittedIds.contains(id)).toList();

    // A DATAPOOL-scoped grant conveys read access to the data sources usable in that pool, so a
    // pool-scoped steward can build pipelines from them. Resolved from the assignments rather than
    // the pool header, because the header is typed to the route (a pipeline route carries the pools
    // that grant DATASET_UPDATE, not necessarily DATASOURCE_READ).
    List<UUID> unauthorized =
        scopeType == ScopeType.DATASOURCE
            ? withoutPoolInheritedDataSources(notDirectlyPermitted, assignments, readPermission)
            : notDirectlyPermitted;

    if (!unauthorized.isEmpty()) {
      log.warn(
          "{} access denied (out-of-scope) for user {}: requested={} unauthorized={}",
          scopeType,
          Encode.forJava(externalId),
          requestedIds,
          unauthorized);
      throw new AccessDeniedException(
          "Not authorized to reference " + scopeType + " " + unauthorized);
    }

    log.info(
        "{} access granted ({}) for user {} referencing {}",
        scopeType,
        notDirectlyPermitted.isEmpty() ? "scoped" : "pool-inherited",
        Encode.forJava(externalId),
        requestedIds);
  }

  private String currentUserExternalId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof PrincipalUserDetails principal
        && principal.getUserId() != null) {
      return principal.getUserId().toString();
    }
    log.warn("Scope access denied: no authenticated user principal");
    throw new AccessDeniedException("No authenticated user for scope access");
  }

  private boolean hasTenantWideRead(List<Assignment> assignments, PermissionName readPermission) {
    return assignments.stream()
        .filter(a -> a.getScopeType() == null || a.getScopeType() == ScopeType.TENANT)
        .anyMatch(a -> grantsPermission(a, readPermission));
  }

  /**
   * Narrows the given not-directly-permitted data source IDs to those the caller may still not
   * reference, after applying datapool inheritance. Uses the same specification as the data source
   * collection filter, so what a pool-scoped caller may reference matches what they can see.
   */
  private List<UUID> withoutPoolInheritedDataSources(
      List<UUID> candidateIds, List<Assignment> assignments, PermissionName readPermission) {
    Set<UUID> grantedPoolIds = permittedScopeIds(assignments, ScopeType.DATAPOOL, readPermission);
    if (candidateIds.isEmpty() || grantedPoolIds.isEmpty()) {
      return candidateIds;
    }

    Specification<DataSource> usableInGrantedPool =
        ScopeFilteringSpecification.dataSourceByScopeOrPool(Set.of(), grantedPoolIds)
            .and((root, query, cb) -> root.get("id").in(candidateIds));
    Set<UUID> poolInherited =
        dataSourceRepository.findAll(usableInGrantedPool).stream()
            .map(DataSource::getId)
            .collect(Collectors.toSet());

    return candidateIds.stream().filter(id -> !poolInherited.contains(id)).toList();
  }

  private Set<UUID> permittedScopeIds(
      List<Assignment> assignments, ScopeType scopeType, PermissionName readPermission) {
    return assignments.stream()
        .filter(a -> a.getScopeType() == scopeType)
        .filter(a -> a.getScope() != null)
        .filter(a -> grantsPermission(a, readPermission))
        .map(a -> a.getScope().getId())
        .collect(Collectors.toSet());
  }

  private boolean grantsPermission(Assignment assignment, PermissionName permission) {
    return assignment.getRole() != null
        && assignment.getRole().getPermissions() != null
        && assignment.getRole().getPermissions().stream()
            .anyMatch(p -> permission.name().equals(p.getName()));
  }
}
