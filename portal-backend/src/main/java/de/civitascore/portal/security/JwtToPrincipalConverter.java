package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtToPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";
  private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
  private static final String ISSUER_REALMS_MARKER = "/realms/";
  private static final String ROLE_PREFIX = "ROLE_";
  private static final String DEFAULT_TENANT = "default";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    PrincipalUserDetails principal =
        PrincipalUserDetails.builder()
            .username(jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME))
            .email(jwt.getClaimAsString(CLAIM_EMAIL))
            .tenantId(extractTenantId(jwt))
            .authorities(extractAuthorities(jwt))
            .build();

    JwtAuthenticationToken token =
        new JwtAuthenticationToken(jwt, principal.getAuthorities(), principal.getUsername());

    token.setDetails(principal);

    return token;
  }

  private String extractTenantId(Jwt jwt) {
    String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
    if (tenantId != null) {
      return tenantId;
    }

    if (jwt.getIssuer() != null) {
      String iss = jwt.getIssuer().toString();
      int idx = iss.indexOf(ISSUER_REALMS_MARKER);
      if (idx >= 0) {
        return iss.substring(idx + ISSUER_REALMS_MARKER.length());
      }
    }

    return DEFAULT_TENANT;
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
    List<String> realmRoles = new ArrayList<>();
    if (realmAccess != null && realmAccess.get(CLAIM_ROLES) instanceof List<?>) {
      realmRoles =
          ((List<?>) realmAccess.get(CLAIM_ROLES))
              .stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    // Optional: Client-specific roles can be extracted here if needed

    return realmRoles.stream()
        .distinct()
        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
        .collect(Collectors.toSet());
  }
}
