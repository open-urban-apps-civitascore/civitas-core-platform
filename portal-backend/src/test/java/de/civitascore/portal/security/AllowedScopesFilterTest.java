package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AllowedScopesFilterTest {

  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain filterChain;

  private AllowedScopesFilter filter;
  private AllowedScopes allowedScopes;

  @BeforeEach
  void setUp() {
    filter = new AllowedScopesFilter(allowedScopesProvider);
    allowedScopes = new AllowedScopes();
    // Note: allowedScopesProvider.getObject() stubbing is done per-test to avoid
    // UnnecessaryStubbingException when the header is missing/blank
  }

  private void setupScopesProvider() {
    when(allowedScopesProvider.getObject()).thenReturn(allowedScopes);
  }

  @Test
  @DisplayName("Should set wildcard when header is *")
  void doFilterInternal_headerIsStar_setsWildcard() throws Exception {
    setupScopesProvider();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn("*");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.isWildcard()).isTrue();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Should parse comma-separated UUIDs")
  void doFilterInternal_commaSeparatedUuids_parsesAll() throws Exception {
    setupScopesProvider();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn(id1 + "," + id2);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.isWildcard()).isFalse();
    assertThat(allowedScopes.getScopeIds()).containsExactlyInAnyOrder(id1, id2);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Should handle single UUID")
  void doFilterInternal_singleUuid_parsesSingleId() throws Exception {
    setupScopesProvider();
    UUID id = UUID.randomUUID();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn(id.toString());

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.isWildcard()).isFalse();
    assertThat(allowedScopes.getScopeIds()).containsExactly(id);
  }

  @Test
  @DisplayName("Should trim whitespace around UUIDs")
  void doFilterInternal_whitespaceAroundUuids_trimsAndParses() throws Exception {
    setupScopesProvider();
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME))
        .thenReturn("  " + id1 + " , " + id2 + "  ");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.getScopeIds()).containsExactlyInAnyOrder(id1, id2);
  }

  @Test
  @DisplayName("Should skip invalid UUIDs and keep valid ones")
  void doFilterInternal_mixedValidAndInvalidUuids_skipsInvalidKeepsValid() throws Exception {
    setupScopesProvider();
    UUID validId = UUID.randomUUID();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME))
        .thenReturn(validId + ",not-a-uuid,also-invalid");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.getScopeIds()).containsExactly(validId);
  }

  @Test
  @DisplayName("Should handle empty string between commas")
  void doFilterInternal_emptyStringBetweenCommas_skipsEmpty() throws Exception {
    setupScopesProvider();
    UUID id = UUID.randomUUID();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn(id + ",,");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.getScopeIds()).containsExactly(id);
  }

  @Test
  @DisplayName("Should not activate scopes when header is missing")
  void doFilterInternal_headerMissing_doesNotActivateScopes() throws Exception {
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isFalse();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Should activate with empty scopes when header is blank (fail-secure)")
  void doFilterInternal_headerBlank_activatesWithEmptyScopes() throws Exception {
    setupScopesProvider();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn("   ");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.isWildcard()).isFalse();
    assertThat(allowedScopes.getScopeIds()).isEmpty();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Should set empty scope IDs when all UUIDs are invalid")
  void doFilterInternal_allUuidsInvalid_setsEmptyScopeIds() throws Exception {
    setupScopesProvider();
    when(request.getHeader(AllowedScopesFilter.HEADER_NAME)).thenReturn("invalid,also-invalid");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(allowedScopes.isActive()).isTrue();
    assertThat(allowedScopes.isWildcard()).isFalse();
    assertThat(allowedScopes.getScopeIds()).isEmpty();
  }
}
