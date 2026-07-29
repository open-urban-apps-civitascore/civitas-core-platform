package de.civitascore.portal.security;

import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped holder for allowed scope IDs from OPA.
 *
 * <p>OPA returns X-Allowed-Scope-Ids header with the user's authorized scopes for the requested
 * resource type. This bean stores those scopes for use in service-layer filtering.
 *
 * <p>Header values:
 *
 * <ul>
 *   <li>"*" - User has TENANT scope (wildcard, no filtering needed)
 *   <li>"id1,id2,id3" - Comma-separated UUIDs (apply IN filter)
 *   <li>"" or missing - No scopes (return empty results)
 * </ul>
 *
 * <p>OPA additionally returns X-Allowed-Pool-Ids: the datapools that carry every permission the
 * requested route needs — not simply every pool the user holds a grant on. Dataset filtering ORs
 * the direct scope IDs with the datasets in those pools; data source filtering ORs them with the
 * data sources usable in those pools.
 *
 * <p>Spring manages the lifecycle - no manual cleanup needed.
 *
 * @see AllowedScopesFilter
 */
@Component
@RequestScope
@Getter
public class AllowedScopes {

  /** Wildcard value indicating TENANT scope (user can see all resources). */
  public static final String WILDCARD = "*";

  /**
   * Whether scope filtering is active for this request. False means no X-Allowed-Scope-Ids header
   * was present (direct backend access without APISIX/OPA).
   */
  private boolean headerPresent = false;

  /** Whether user has wildcard (TENANT) access - skip filtering. */
  private boolean wildcard = false;

  /** Specific scope IDs the user is authorized to access. */
  private Set<UUID> scopeIds = Set.of();

  /**
   * Datapool IDs the user may access via DATAPOOL-scoped grants. ORed with {@link #scopeIds} during
   * dataset and data source filtering.
   */
  private Set<UUID> poolIds = Set.of();

  /**
   * Set wildcard access (user has TENANT scope). Clears any specific scope/pool IDs so the object
   * cannot represent the contradictory "wildcard AND specific IDs" state.
   */
  public void setWildcard() {
    this.headerPresent = true;
    this.wildcard = true;
    this.scopeIds = Set.of();
    this.poolIds = Set.of();
  }

  /**
   * Set specific scope IDs the user can access.
   *
   * @param ids the authorized scope IDs, or empty set for no access
   */
  public void setScopeIds(Set<UUID> ids) {
    this.headerPresent = true;
    this.wildcard = false;
    this.scopeIds = ids != null ? Set.copyOf(ids) : Set.of();
  }

  /**
   * Set the datapool IDs the user may access (Epic 1 union). Marks the scope header as present so a
   * user whose only grant is pool-based is still scoped (not treated as direct backend access).
   *
   * <p>Ignored for a wildcard caller, who is not filtered at all — this upholds the invariant
   * {@link #setWildcard()} documents, since OPA emits both headers for a tenant-wide reader who
   * also holds a pool grant. Requires the scope header to have been parsed first: a later {@link
   * #setScopeIds} clears the wildcard flag without clearing the pool IDs.
   *
   * @param ids the authorized datapool IDs, or empty set for none
   */
  public void setPoolIds(Set<UUID> ids) {
    this.headerPresent = true;
    if (this.wildcard) {
      return;
    }
    this.poolIds = ids != null ? Set.copyOf(ids) : Set.of();
  }
}
