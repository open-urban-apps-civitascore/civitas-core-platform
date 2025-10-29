package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class TenantContext {

  private static final ThreadLocal<String> TENANT_ID = new InheritableThreadLocal<>();

  public static void setTenantId(String tenantId) {
    TENANT_ID.set(tenantId);
  }

  public static String getTenantId() {
    String tenantId = TENANT_ID.get();
    if (tenantId != null) {
      return tenantId;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof PrincipalUserDetails) {
      PrincipalUserDetails principal = (PrincipalUserDetails) auth.getPrincipal();
      return principal.getTenantId();
    }

    return null;
  }

  public static String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof PrincipalUserDetails) {
      PrincipalUserDetails principal = (PrincipalUserDetails) auth.getPrincipal();
      return principal.getUsername();
    }
    return auth != null ? auth.getName() : null;
  }

  public static PrincipalUserDetails getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof PrincipalUserDetails) {
      return (PrincipalUserDetails) auth.getPrincipal();
    }
    return null;
  }

  public static void clear() {
    TENANT_ID.remove();
  }
}
