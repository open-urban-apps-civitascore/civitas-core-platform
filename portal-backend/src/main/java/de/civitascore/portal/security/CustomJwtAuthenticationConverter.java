package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String CLAIM_SUB = "sub";
  private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String CLAIM_GIVEN_NAME = "given_name";
  private static final String CLAIM_FAMILY_NAME = "family_name";
  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";
  private static final String ISSUER_REALMS_MARKER = "/realms/";
  private static final String ROLE_PREFIX = "ROLE_";
  private static final String DEFAULT_TENANT = "default";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String userId = jwt.getClaimAsString(CLAIM_SUB);
    String username = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
    String email = jwt.getClaimAsString(CLAIM_EMAIL);
    String tenant = extractTenantId(jwt);
    String givenName = jwt.getClaimAsString(CLAIM_GIVEN_NAME);
    String familyName = jwt.getClaimAsString(CLAIM_FAMILY_NAME);

    Set<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);

    PrincipalUserDetails dto =
        PrincipalUserDetails.builder()
            .userId(userId)
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
    // Try to get tenantId as a String claim first
    Object tenantIdClaim = jwt.getClaim(CLAIM_TENANT_ID);

    if (tenantIdClaim instanceof String) {
      return (String) tenantIdClaim;
    }

    // Fallback: extract from issuer
    if (jwt.getIssuer() != null) {
      String iss = jwt.getIssuer().toString();
      int idx = iss.indexOf(ISSUER_REALMS_MARKER);
      if (idx >= 0) {
        return iss.substring(idx + ISSUER_REALMS_MARKER.length());
      }
    }
    return DEFAULT_TENANT;
  }

  private Set<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
    Set<SimpleGrantedAuthority> authorities = new HashSet<>();

    Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
    if (realmAccess != null && realmAccess.get(CLAIM_ROLES) instanceof List<?>) {
      List<String> realmRoles =
          ((List<?>) realmAccess.get(CLAIM_ROLES))
              .stream().filter(String.class::isInstance).map(String.class::cast).toList();

      realmRoles.forEach(
          role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase())));
    }

    // Optional: Client-specific roles can be extracted here if needed

    return authorities;
  }
}
