package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ScopeAccessAuthorizerTest {

  @Mock private AssignmentService assignmentService;
  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  private final AllowedScopes allowedScopes = new AllowedScopes();

  private ScopeAccessAuthorizer authorizer;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    authorizer = new ScopeAccessAuthorizer(assignmentService, allowedScopesProvider);
    lenient().when(allowedScopesProvider.getObject()).thenReturn(allowedScopes);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(UUID id) {
    PrincipalUserDetails principal = PrincipalUserDetails.builder().userId(id).build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private Role roleWith(PermissionName... permissions) {
    Role role = new Role();
    Set<Permission> perms =
        java.util.Arrays.stream(permissions)
            .map(
                pn -> {
                  Permission p = new Permission();
                  p.setName(pn.name());
                  return p;
                })
            .collect(java.util.stream.Collectors.toSet());
    role.setPermissions(perms);
    return role;
  }

  private Assignment tenantAssignment(Role role) {
    Assignment a = new Assignment();
    a.setScopeType(ScopeType.TENANT);
    a.setRole(role);
    return a;
  }

  private Assignment dataSourceAssignment(UUID dataSourceId, Role role) {
    DataSource ds = new DataSource();
    ds.setId(dataSourceId);
    Assignment a = new Assignment();
    a.setScopeType(ScopeType.DATASOURCE);
    a.setDataSource(ds);
    a.setRole(role);
    return a;
  }

  @Test
  @DisplayName("Denies when no scope header is present (direct backend access)")
  void deniesWithoutHeader() {
    // AllowedScopes defaults to headerPresent = false.
    assertThatThrownBy(
            () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Empty reference set is a no-op")
  void emptySetIsNoOp() {
    assertThatCode(() -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Wildcard header grants any reference — it is a decision the PDP already made")
  void wildcardGrantsAny() {
    // A wildcard header means OPA already authorized tenant-wide access; the backend does not
    // re-adjudicate it. No DB assignment lookup is needed on this path.
    allowedScopes.setWildcard();
    authenticateAs(userId);

    assertThatCode(
            () ->
                authorizer.authorizeReferences(
                    ScopeType.DATASOURCE, Set.of(UUID.randomUUID(), UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Denies when the scope header is present but there is no principal")
  void deniesWithoutPrincipalWhenScoped() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    assertThatThrownBy(
            () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(UUID.randomUUID())))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Tenant assignment with the READ permission grants any reference")
  void tenantAssignmentCascades() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    authenticateAs(userId);
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(List.of(tenantAssignment(roleWith(PermissionName.DATASOURCE_READ))));

    assertThatCode(
            () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(UUID.randomUUID())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Scoped assignment covering all referenced ids grants access")
  void scopedCoversAllGrants() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    authenticateAs(userId);
    UUID ds1 = UUID.randomUUID();
    UUID ds2 = UUID.randomUUID();
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(
            List.of(
                dataSourceAssignment(ds1, roleWith(PermissionName.DATASOURCE_READ)),
                dataSourceAssignment(ds2, roleWith(PermissionName.DATASOURCE_READ))));

    assertThatCode(() -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(ds1, ds2)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Partial overlap denies and names only the unauthorized id")
  void partialOverlapDenies() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    authenticateAs(userId);
    UUID inScope = UUID.randomUUID();
    UUID outOfScope = UUID.randomUUID();
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(
            List.of(dataSourceAssignment(inScope, roleWith(PermissionName.DATASOURCE_READ))));

    assertThatThrownBy(
            () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(inScope, outOfScope)))
        .isInstanceOf(AccessDeniedException.class)
        .satisfies(
            ex -> {
              assertThat(ex.getMessage()).contains(outOfScope.toString());
              assertThat(ex.getMessage()).doesNotContain(inScope.toString());
            });
  }

  @Test
  @DisplayName("Scoped assignment without the READ permission does not grant")
  void scopedWithoutReadPermissionDenies() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    authenticateAs(userId);
    UUID ds = UUID.randomUUID();
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(List.of(dataSourceAssignment(ds, roleWith(PermissionName.DATASOURCE_UPDATE))));

    assertThatThrownBy(() -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(ds)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("An assignment of a different scope type does not grant")
  void differentScopeTypeDoesNotGrant() {
    allowedScopes.setScopeIds(Set.of(UUID.randomUUID()));
    authenticateAs(userId);
    UUID id = UUID.randomUUID();
    // A DATASOURCE-scoped assignment must not authorize a DATAPOOL reference for the same id.
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(List.of(dataSourceAssignment(id, roleWith(PermissionName.DATASOURCE_READ))));

    assertThatThrownBy(() -> authorizer.authorizeReferences(ScopeType.DATAPOOL, Set.of(id)))
        .isInstanceOf(AccessDeniedException.class);
  }
}
