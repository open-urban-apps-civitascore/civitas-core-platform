package de.civitascore.portal.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.security.CustomJwtAuthenticationConverter;
import de.civitascore.portal.security.CustomJwtAuthenticationToken;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CustomJwtAuthenticationConverterTest {

  private final CustomJwtAuthenticationConverter converter = new CustomJwtAuthenticationConverter();

  @Test
  void givenValidJwt_whenConvert_thenReturnCustomAuthenticationToken() {
    // Given
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("preferred_username", "testuser")
            .claim("email", "test@example.com")
            .claim("given_name", "Test")
            .claim("family_name", "User")
            .claim("tenantId", "test-tenant")
            .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    // When
    CustomJwtAuthenticationToken result = (CustomJwtAuthenticationToken) converter.convert(jwt);

    // Then
    assertThat(result).isNotNull();

    PrincipalUserDetails principal = result.getPrincipal();
    assertThat(principal.getUsername()).isEqualTo("testuser");
    assertThat(principal.getEmail()).isEqualTo("test@example.com");
    assertThat(principal.getTenantId()).isEqualTo("test-tenant");
    assertThat(principal.getGivenName()).isEqualTo("Test");
    assertThat(principal.getFamilyName()).isEqualTo("User");

    assertThat(result.getAuthorities())
        .containsExactlyInAnyOrder(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
