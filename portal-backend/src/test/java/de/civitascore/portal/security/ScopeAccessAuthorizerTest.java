package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ScopeAccessAuthorizerTest {

  @Mock private AssignmentService assignmentService;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  private final AllowedScopes allowedScopes = new AllowedScopes();

  private ScopeAccessAuthorizer authorizer;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    authorizer =
        new ScopeAccessAuthorizer(assignmentService, dataSourceRepository, allowedScopesProvider);
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

  @Nested
  @DisplayName("Datapool inheritance for referenced data sources")
  class DatapoolInheritance {

    private final UUID poolId = UUID.randomUUID();
    private final UUID dataSourceId = UUID.randomUUID();

    /** A pool-scoped steward: no DATASOURCE assignment, only DATAPOOL. */
    private void poolScopedStewardWith(PermissionName... permissions) {
      allowedScopes.setScopeIds(Set.of());
      allowedScopes.setPoolIds(Set.of(poolId));
      authenticateAs(userId);
      when(assignmentService.findAllByUserExternalId(userId.toString()))
          .thenReturn(List.of(dataPoolAssignment(poolId, roleWith(permissions))));
    }

    @Test
    @DisplayName("A pool grant covers a data source usable in that pool")
    void poolGrantCoversUsableDataSource() {
      poolScopedStewardWith(PermissionName.DATASOURCE_READ);
      DataSource usable = new DataSource();
      usable.setId(dataSourceId);
      when(dataSourceRepository.findAll(ArgumentMatchers.<Specification<DataSource>>any()))
          .thenReturn(List.of(usable));

      assertThatCode(
              () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A pool grant does not cover a data source unusable in that pool")
    void poolGrantDeniesUnusableDataSource() {
      poolScopedStewardWith(PermissionName.DATASOURCE_READ);
      when(dataSourceRepository.findAll(ArgumentMatchers.<Specification<DataSource>>any()))
          .thenReturn(List.of());

      assertThatThrownBy(
              () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining(dataSourceId.toString());
    }

    @Test
    @DisplayName("A pool grant without DATASOURCE_READ is never consulted for usability")
    void poolGrantWithoutReadPermissionDenies() {
      poolScopedStewardWith(PermissionName.DATASET_UPDATE, PermissionName.DATAPOOL_READ);

      assertThatThrownBy(
              () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
          .isInstanceOf(AccessDeniedException.class);
      verify(dataSourceRepository, never())
          .findAll(ArgumentMatchers.<Specification<DataSource>>any());
    }

    @Test
    @DisplayName("Directly scoped references need no usability lookup")
    void directGrantSkipsUsabilityLookup() {
      allowedScopes.setScopeIds(Set.of(dataSourceId));
      authenticateAs(userId);
      when(assignmentService.findAllByUserExternalId(userId.toString()))
          .thenReturn(
              List.of(
                  dataSourceAssignment(dataSourceId, roleWith(PermissionName.DATASOURCE_READ))));

      assertThatCode(
              () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
          .doesNotThrowAnyException();
      verify(dataSourceRepository, never())
          .findAll(ArgumentMatchers.<Specification<DataSource>>any());
    }

    @Test
    @DisplayName("A mixed reference set is covered by direct and inherited grants together")
    void directAndInheritedCombine() {
      UUID directId = UUID.randomUUID();
      allowedScopes.setScopeIds(Set.of(directId));
      allowedScopes.setPoolIds(Set.of(poolId));
      authenticateAs(userId);
      when(assignmentService.findAllByUserExternalId(userId.toString()))
          .thenReturn(
              List.of(
                  dataSourceAssignment(directId, roleWith(PermissionName.DATASOURCE_READ)),
                  dataPoolAssignment(poolId, roleWith(PermissionName.DATASOURCE_READ))));
      DataSource usable = new DataSource();
      usable.setId(dataSourceId);
      when(dataSourceRepository.findAll(ArgumentMatchers.<Specification<DataSource>>any()))
          .thenReturn(List.of(usable));

      assertThatCode(
              () ->
                  authorizer.authorizeReferences(
                      ScopeType.DATASOURCE, Set.of(directId, dataSourceId)))
          .doesNotThrowAnyException();
    }

    /**
     * Pins the inheritance against the real role catalogue: a DATA role held at datapool scope may
     * reference the pool's data sources in a pipeline exactly when it carries DATASOURCE_READ. Data
     * Consumer is the case that matters — its pool grant must not become data source access.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = RoleDefault.class,
        names = {
          "DATA_ARCHITECT",
          "DATA_CONSUMER",
          "DATA_STEWARD",
          "DATA_OWNER",
          "DATA_GATEKEEPER"
        })
    @DisplayName("A pool-scoped DATA role may reference pool data sources iff it can read them")
    void poolScopedRoleMatchesReadPermission(RoleDefault roleDefault) {
      boolean expectedGrant = roleDefault.getPermissions().contains(PermissionName.DATASOURCE_READ);
      allowedScopes.setScopeIds(Set.of());
      allowedScopes.setPoolIds(Set.of(poolId));
      authenticateAs(userId);
      when(assignmentService.findAllByUserExternalId(userId.toString()))
          .thenReturn(
              List.of(
                  dataPoolAssignment(
                      poolId,
                      roleWith(roleDefault.getPermissions().toArray(new PermissionName[0])))));
      DataSource usable = new DataSource();
      usable.setId(dataSourceId);
      lenient()
          .when(dataSourceRepository.findAll(ArgumentMatchers.<Specification<DataSource>>any()))
          .thenReturn(List.of(usable));

      if (expectedGrant) {
        assertThatCode(
                () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
            .doesNotThrowAnyException();
      } else {
        assertThatThrownBy(
                () -> authorizer.authorizeReferences(ScopeType.DATASOURCE, Set.of(dataSourceId)))
            .isInstanceOf(AccessDeniedException.class);
      }
    }

    @Test
    @DisplayName("Inheritance applies to data sources only, not to other referenced scope types")
    void inheritanceDoesNotLeakToOtherScopeTypes() {
      allowedScopes.setScopeIds(Set.of());
      allowedScopes.setPoolIds(Set.of(poolId));
      authenticateAs(userId);
      when(assignmentService.findAllByUserExternalId(userId.toString()))
          .thenReturn(
              List.of(
                  dataPoolAssignment(
                      poolId,
                      roleWith(
                          PermissionName.DATASOURCE_READ, PermissionName.DATASTRUCTURE_READ))));

      assertThatThrownBy(
              () ->
                  authorizer.authorizeReferences(
                      ScopeType.DATASTRUCTURE, Set.of(UUID.randomUUID())))
          .isInstanceOf(AccessDeniedException.class);
      verify(dataSourceRepository, never())
          .findAll(ArgumentMatchers.<Specification<DataSource>>any());
    }
  }

  private Assignment dataPoolAssignment(UUID dataPoolId, Role role) {
    DataPool pool = new DataPool();
    pool.setId(dataPoolId);
    Assignment a = new Assignment();
    a.setScopeType(ScopeType.DATAPOOL);
    a.setDataPool(pool);
    a.setRole(role);
    return a;
  }
}
