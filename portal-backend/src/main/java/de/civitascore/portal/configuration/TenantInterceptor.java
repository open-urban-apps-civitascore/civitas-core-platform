package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

  private static final String TENANT_HEADER = "X-Tenant-ID";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof PrincipalUserDetails) {
      PrincipalUserDetails principal = (PrincipalUserDetails) auth.getPrincipal();
      String tenantId = principal.getTenantId();
      if (tenantId != null && !tenantId.isBlank()) {
        TenantContext.setTenantId(tenantId);
        log.debug("Tenant ID extracted from JWT: {}", tenantId);
        return true;
      }
    }

    log.warn("No tenant ID found in request");
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clear();
  }
}
