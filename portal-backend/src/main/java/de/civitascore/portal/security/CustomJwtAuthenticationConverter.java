package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CustomJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String username = jwt.getClaimAsString("preferred_username");
    String email = jwt.getClaimAsString("email");
    String tenant = extractTenantId(jwt);
    String givenName = jwt.getClaimAsString("given_name");
    String familyName = jwt.getClaimAsString("family_name");

    Set<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);

    PrincipalUserDetails dto =
        PrincipalUserDetails.builder()
            .username(username)
            .email(email)
            .tenantId(tenant)
            .givenName(givenName)
            .familyName(familyName)
            .authorities(authorities)
            .build();

    return new CustomJwtAuthenticationToken(jwt, authorities, dto);
  }

  private String extractTenantId(Jwt jwt) {
    // First try explicit tenantId claim
    String tenantId = jwt.getClaimAsString("tenantId");
    if (tenantId != null) {
      return tenantId;
    }

    // Fallback: extract from issuer
    if (jwt.getIssuer() != null) {
      String iss = jwt.getIssuer().toString();
      String marker = "/realms/";
      int idx = iss.indexOf(marker);
      if (idx >= 0) {
        return iss.substring(idx + marker.length());
      }
    }
    return "default";
  }

  private Set<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
    Set<SimpleGrantedAuthority> authorities = new HashSet<>();

    // Extract realm roles
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess != null && realmAccess.get("roles") instanceof List<?>) {
      List<String> realmRoles =
          ((List<?>) realmAccess.get("roles"))
              .stream().filter(String.class::isInstance).map(String.class::cast).toList();

      realmRoles.forEach(
          role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }

    //    // Extract client roles (optional - if you use client-specific roles)
    //    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    //    if (resourceAccess != null) {
    //      resourceAccess.values().stream()
    //          .filter(Map.class::isInstance)
    //          .map(Map.class::cast)
    //          .forEach(
    //              clientAccess -> {
    //                Object roles = ((Map<String, Object>) clientAccess).get("roles");
    //                if (roles instanceof List<?>) {
    //                  ((List<?>) roles)
    //                      .stream()
    //                          .filter(String.class::isInstance)
    //                          .map(String.class::cast)
    //                          .forEach(
    //                              role ->
    //                                  authorities.add(
    //                                      new SimpleGrantedAuthority("ROLE_" +
    // role.toUpperCase())));
    //                }
    //              });
    //    }

    return authorities;
  }
}
