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
  private boolean active = false;

  /** Whether user has wildcard (TENANT) access - skip filtering. */
  private boolean wildcard = false;

  /** Specific scope IDs the user is authorized to access. */
  private Set<UUID> scopeIds = Set.of();

  /** Set wildcard access (user has TENANT scope). */
  public void setWildcard() {
    this.active = true;
    this.wildcard = true;
  }

  /**
   * Set specific scope IDs the user can access.
   *
   * @param ids the authorized scope IDs, or empty set for no access
   */
  public void setScopeIds(Set<UUID> ids) {
    this.active = true;
    this.wildcard = false;
    this.scopeIds = ids != null ? ids : Set.of();
  }
}
