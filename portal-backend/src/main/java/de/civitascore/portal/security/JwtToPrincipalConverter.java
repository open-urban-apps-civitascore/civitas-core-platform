package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtToPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    PrincipalUserDetails principal =
        PrincipalUserDetails.builder()
            .username(jwt.getClaimAsString("preferred_username"))
            .email(jwt.getClaimAsString("email"))
            .tenantId(extractTenantId(jwt))
            .authorities(extractAuthorities(jwt))
            .build();

    // Create token with username as principal (String)
    JwtAuthenticationToken token =
        new JwtAuthenticationToken(jwt, principal.getAuthorities(), principal.getUsername());

    // Store our custom principal in details
    token.setDetails(principal);

    return token;
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

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    // Extract realm roles
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    List<String> realmRoles = new ArrayList<>();
    if (realmAccess != null && realmAccess.get("roles") instanceof List<?>) {
      realmRoles =
          ((List<?>) realmAccess.get("roles"))
              .stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    // Extract client roles
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    List<String> clientRoles = new ArrayList<>();
    if (resourceAccess != null) {
      clientRoles =
          resourceAccess.values().stream()
              .filter(Map.class::isInstance)
              .map(Map.class::cast)
              .map(m -> (List<String>) ((Map<String, Object>) m).getOrDefault("roles", List.of()))
              .flatMap(Collection::stream)
              .toList();
    }

    // Combine and convert to authorities
    return Stream.concat(realmRoles.stream(), clientRoles.stream())
        .distinct()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        .collect(Collectors.toSet());
  }
}
