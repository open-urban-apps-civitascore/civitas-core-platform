package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Thread-local context holder for tenant information.
 *
 * <p>This class provides thread-safe storage for the current tenant ID. The tenant ID is set by the
 * {@link TenantInterceptor} on each request and should be cleared after request completion.
 *
 * <p><b>Important:</b> Always use ThreadLocal (not InheritableThreadLocal) to prevent tenant
 * context leakage in async operations or thread pools.
 */
@Slf4j
public final class TenantContext {

  private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

  /**
   * Sets the tenant ID for the current thread.
   *
   * @param tenantId the tenant ID to set
   */
  public static void setTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      log.warn("Attempting to set null or blank tenant ID");
      return;
    }
    TENANT_ID.set(tenantId);
    log.debug("Tenant ID set to: {}", tenantId);
  }

  /**
   * Gets the tenant ID for the current thread.
   *
   * <p>This method only returns the value set via {@link #setTenantId(String)}. It does NOT fall
   * back to the SecurityContext to avoid confusion and ensure consistency.
   *
   * @return the tenant ID, or null if not set
   */
  public static String getTenantId() {
    return TENANT_ID.get();
  }

  /**
   * Gets the tenant ID for the current thread, throwing an exception if not set.
   *
   * <p>This method first checks the ThreadLocal, and if not set, falls back to extracting the
   * tenant ID from the SecurityContext. This fallback is particularly useful in test scenarios
   * where the TenantInterceptor might not run.
   *
   * @return the tenant ID
   * @throws IllegalStateException if no tenant context is available
   */
  public static String requireTenantId() {
    String tenantId = TENANT_ID.get();

    // If not set in ThreadLocal, try to get from SecurityContext as fallback
    if (tenantId == null) {
      log.debug("Tenant ID not found in ThreadLocal, checking SecurityContext");
      PrincipalUserDetails principal = getCurrentUser();

      if (principal != null && principal.getTenantId() != null) {
        tenantId = principal.getTenantId();
        // Also set it in ThreadLocal for consistency
        setTenantId(tenantId);
        log.debug(
            "Retrieved tenant ID '{}' from SecurityContext (user: {}) and set in ThreadLocal",
            tenantId,
            principal.getUsername());

        return tenantId;
      }

      // Log detailed information for debugging
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null) {
        log.error("No tenant context available: No authentication in SecurityContext");
      } else if (!auth.isAuthenticated()) {
        log.error("No tenant context available: User not authenticated");
      } else if (principal == null) {
        log.error(
            "No tenant context available: Principal is null or not of type PrincipalUserDetails (actual type: {})",
            auth.getPrincipal() != null ? auth.getPrincipal().getClass().getSimpleName() : "null");
      } else {
        log.error(
            "No tenant context available: Principal exists but tenantId is null (user: {})",
            principal.getUsername());
      }

      throw new IllegalStateException("No tenant context available");
    }
    return tenantId;
  }

  /**
   * Gets the current user ID from the security context.
   *
   * @return the user ID, or null if not authenticated
   */
  public static String getCurrentUserId() {
    PrincipalUserDetails principal = getCurrentUser();
    if (principal != null) {
      return principal.getUsername();
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() ? auth.getName() : null;
  }

  /**
   * Gets the current user principal from the security context.
   *
   * @return the PrincipalUserDetails, or null if not authenticated or not the correct type
   */
  public static PrincipalUserDetails getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof PrincipalUserDetails) {
      return (PrincipalUserDetails) auth.getPrincipal();
    }
    return null;
  }

  /**
   * Clears the tenant context for the current thread.
   *
   * <p>This should be called after request completion to prevent memory leaks and context
   * pollution.
   */
  public static void clear() {
    TENANT_ID.remove();
    log.debug("Tenant context cleared");
  }
}
