package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.security.permission.AuthorizationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

  private final AuthorizationStrategy authorizationStrategy;

  public boolean hasPermission(String userId, String permission, String tenantId) {
    return authorizationStrategy.hasPermission(userId, permission, tenantId);
  }

  public boolean hasPermissionForScope(
      String userId, String permission, ScopeType scopeType, String scopeId, String tenantId) {
    return authorizationStrategy.hasPermissionForScope(
        userId, permission, scopeType, scopeId, tenantId);
  }

  public boolean canAccessResource(
      String userId, String resourceType, String resourceId, String action, String tenantId) {
    return authorizationStrategy.canAccessResource(
        userId, resourceType, resourceId, action, tenantId);
  }
}
