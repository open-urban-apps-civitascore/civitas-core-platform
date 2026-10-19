package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AssignmentChangeAuthorizerTest {

  @Mock private AssignmentService assignmentService;

  private AssignmentChangeAuthorizer authorizer;

  private final UUID userId = UUID.randomUUID();
  private final Group callerGroup = group();
  private final Group otherGroup = group();
  private final Role steward = role(RoleDefault.DATA_STEWARD);
  private final Role owner = role(RoleDefault.DATA_OWNER);
  private final Role consumer = role(RoleDefault.DATA_CONSUMER);

  @BeforeEach
  void setUp() {
    authorizer = new AssignmentChangeAuthorizer(assignmentService);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("An unchanged list needs neither a principal nor a lookup")
  void unchangedListIsNoOp() {
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);

    assertThatCode(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, owner))))
        .doesNotThrowAnyException();
    verifyNoInteractions(assignmentService);
  }

  @Test
  @DisplayName("Denies a change without an authenticated user, even for a role without permissions")
  void deniesWithoutPrincipal() {
    DataSet dataSet = dataSet(null);

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, role()))))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(assignmentService);
  }

  @Test
  @DisplayName(
      "Denies a change by a principal without a user id, even for a role without permissions")
  void deniesServiceAccount() {
    authenticateAs(null);
    DataSet dataSet = dataSet(null);

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, role()))))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(assignmentService);
  }

  @Test
  @DisplayName("Denies a change on an unsupported entity type")
  void deniesUnknownTargetType() {
    authenticateAs(userId);
    BaseDataEntity unknown = mock(BaseDataEntity.class);
    when(unknown.getAssignments()).thenReturn(new HashSet<>());

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(unknown, List.of(requested(otherGroup, consumer))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Owner on the dataset may add Consumer, Steward and Owner")
  void ownerMayAddCoveredRoles() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(owner, dataSet));

    for (Role granted : List.of(consumer, steward, owner)) {
      assertThatCode(
              () ->
                  authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, granted))))
          .doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("A Data Steward on the dataset may add a Consumer")
  void stewardMayAddConsumer() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(steward, dataSet));

    assertThatCode(
            () ->
                authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, consumer))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("A Data Steward on the dataset may not add a Data Owner")
  void stewardMayNotAddOwner() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(steward, dataSet));

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Steward on the dataset may not remove a Data Owner")
  void stewardMayNotRemoveOwner() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);
    callerHolds(onEntity(steward, dataSet));

    assertThatThrownBy(() -> authorizer.authorizeReplacement(dataSet, List.of()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Steward may not move a Data Owner role to their own group")
  void stewardMayNotMoveOwnerToOwnGroup() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);
    callerHolds(onEntity(steward, dataSet));

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Steward may not add a Data Owner alongside a Consumer")
  void stewardMayNotAddOwnerAlongsideConsumer() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(steward, dataSet));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(
                    dataSet,
                    List.of(requested(otherGroup, consumer), requested(callerGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Steward may not remove a Data Owner while adding a Consumer")
  void stewardMayNotRemoveOwnerWhileAddingConsumer() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);
    callerHolds(onEntity(steward, dataSet));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, consumer))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Owner on the dataset may remove a Data Steward")
  void ownerMayRemoveSteward() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, steward);
    callerHolds(onEntity(owner, dataSet));

    assertThatCode(() -> authorizer.authorizeReplacement(dataSet, List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("A tenant-wide Data Owner may add a Data Owner")
  void tenantWideOwnerCounts() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(tenantWide(owner));

    assertThatCode(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, owner))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("An assignment on another dataset does not count")
  void otherDatasetDoesNotCount() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(owner, dataSet(null)));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, consumer))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("An assignment of another scope type with the same id does not count")
  void otherScopeTypeWithSameIdDoesNotCount() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    DataSource sameId = new DataSource();
    sameId.setId(dataSet.getId());
    callerHolds(onEntity(owner, sameId));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, consumer))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Owner on the dataset's pool may add a Data Owner, also on a new dataset")
  void poolOwnerCountsOnDataset() {
    authenticateAs(userId);
    DataPool pool = dataPool();
    DataSet existing = dataSet(pool);
    DataSet created = dataSet(pool);
    created.setId(null);
    callerHolds(onEntity(owner, pool));

    assertThatCode(
            () -> authorizer.authorizeReplacement(existing, List.of(requested(otherGroup, owner))))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> authorizer.authorizeReplacement(created, List.of(requested(otherGroup, owner))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("A pool assignment does not count for a dataset in another pool or in none")
  void poolOwnerDoesNotCountOutsidePool() {
    authenticateAs(userId);
    DataPool pool = dataPool();
    callerHolds(onEntity(owner, pool));

    for (DataSet dataSet : List.of(dataSet(dataPool()), dataSet(null))) {
      assertThatThrownBy(
              () ->
                  authorizer.authorizeReplacement(
                      dataSet, List.of(requested(otherGroup, consumer))))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  @DisplayName("A pool assignment does not count for a data source")
  void poolOwnerDoesNotCountOnDataSource() {
    authenticateAs(userId);
    DataSource dataSource = new DataSource();
    dataSource.setId(UUID.randomUUID());
    callerHolds(onEntity(owner, dataPool()));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(dataSource, List.of(requested(otherGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A Data Steward on a pool may not add a Data Owner on that pool")
  void poolStewardMayNotAddPoolOwner() {
    authenticateAs(userId);
    DataPool pool = dataPool();
    callerHolds(onEntity(steward, pool));

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(pool, List.of(requested(callerGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Tenant-wide ASSIGNMENT_CREATE allows adding any role, but not removing one")
  void assignmentCreateCoversAddingOnly() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(unscoped(role(PermissionName.ASSIGNMENT_CREATE)));

    assertThatCode(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, owner))))
        .doesNotThrowAnyException();

    stored(dataSet, otherGroup, owner);
    assertThatThrownBy(() -> authorizer.authorizeReplacement(dataSet, List.of()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Tenant-wide ASSIGNMENT_DELETE allows removing any role, but not adding one")
  void assignmentDeleteCoversRemovingOnly() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);
    callerHolds(unscoped(role(PermissionName.ASSIGNMENT_DELETE)));

    assertThatCode(() -> authorizer.authorizeReplacement(dataSet, List.of()))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("Tenant-wide ASSIGNMENT_CREATE does not cover a removal in the same change")
  void assignmentCreateDoesNotCoverRemovalInSameChange() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    stored(dataSet, otherGroup, owner);
    callerHolds(unscoped(role(PermissionName.ASSIGNMENT_CREATE)));

    assertThatThrownBy(
            () ->
                authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, consumer))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("ASSIGNMENT_CREATE on a scoped assignment grants no override")
  void scopedAssignmentPermissionIsNoOverride() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(role(PermissionName.ASSIGNMENT_CREATE), dataSet));

    assertThatThrownBy(
            () -> authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, owner))))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("A role without permissions that apply to the entity needs none")
  void roleWithoutApplicablePermissionsIsAllowed() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds();

    assertThatCode(
            () ->
                authorizer.authorizeReplacement(
                    dataSet,
                    List.of(
                        requested(otherGroup, role(PermissionName.DATASOURCE_RELEASE)),
                        requested(callerGroup, role()))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Logs an authorized change as info and a refused one as a warning, with the user")
  void logsEveryDecision() {
    authenticateAs(userId);
    DataSet dataSet = dataSet(null);
    callerHolds(onEntity(steward, dataSet));
    Logger logger = (Logger) LoggerFactory.getLogger(AssignmentChangeAuthorizer.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      authorizer.authorizeReplacement(dataSet, List.of(requested(otherGroup, consumer)));
      assertThatThrownBy(
              () ->
                  authorizer.authorizeReplacement(dataSet, List.of(requested(callerGroup, owner))))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(
            ILoggingEvent::getLevel,
            event -> event.getFormattedMessage().contains(userId.toString()))
        .containsExactly(tuple(Level.INFO, true), tuple(Level.WARN, true));
  }

  @ParameterizedTest
  @MethodSource("dataPermissions")
  @DisplayName("Checks every data permission on at least one entity type")
  void everyDataPermissionIsChecked(PermissionName permission) {
    authenticateAs(userId);
    callerHolds();
    DataSource dataSource = new DataSource();
    dataSource.setId(UUID.randomUUID());
    DataStructure dataStructure = new DataStructure();
    dataStructure.setId(UUID.randomUUID());
    Role role = role(permission);

    assertThat(List.<BaseDataEntity>of(dataSet(null), dataSource, dataStructure, dataPool()))
        .anySatisfy(
            target ->
                assertThatThrownBy(
                        () ->
                            authorizer.authorizeReplacement(
                                target, List.of(requested(otherGroup, role))))
                    .isInstanceOf(AccessDeniedException.class));
  }

  static Stream<PermissionName> dataPermissions() {
    return Arrays.stream(PermissionName.values())
        .filter(permission -> permission.getPermissionType() == PermissionType.DATA);
  }

  // --- fixtures ---

  private void authenticateAs(UUID id) {
    PrincipalUserDetails principal = PrincipalUserDetails.builder().userId(id).build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private void callerHolds(Assignment... assignments) {
    when(assignmentService.findAllByUserExternalId(userId.toString()))
        .thenReturn(List.of(assignments));
  }

  private static Group group() {
    Group group = new Group();
    group.setId(UUID.randomUUID());
    return group;
  }

  private static Role role(RoleDefault template) {
    return role(template.getPermissions().toArray(PermissionName[]::new));
  }

  private static Role role(PermissionName... permissions) {
    Role role = new Role();
    role.setId(UUID.randomUUID());
    role.setPermissions(
        Arrays.stream(permissions)
            .map(
                name -> {
                  Permission permission = new Permission();
                  permission.setName(name.name());
                  return permission;
                })
            .collect(Collectors.toSet()));
    return role;
  }

  private static DataPool dataPool() {
    DataPool pool = new DataPool();
    pool.setId(UUID.randomUUID());
    return pool;
  }

  private static DataSet dataSet(DataPool pool) {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setDataPool(pool);
    return dataSet;
  }

  /** A body assignment: group and role only, scope is set when it is stored. */
  private static Assignment requested(Group group, Role role) {
    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    return assignment;
  }

  private static void stored(DataSet dataSet, Group group, Role role) {
    Assignment assignment = requested(group, role);
    assignment.setScope(dataSet);
    dataSet.getAssignments().add(assignment);
  }

  private Assignment onEntity(Role role, DataSet scope) {
    Assignment assignment = requested(callerGroup, role);
    assignment.setScope(scope);
    return assignment;
  }

  private Assignment onEntity(Role role, DataSource scope) {
    Assignment assignment = requested(callerGroup, role);
    assignment.setScope(scope);
    return assignment;
  }

  private Assignment onEntity(Role role, DataPool scope) {
    Assignment assignment = requested(callerGroup, role);
    assignment.setScope(scope);
    return assignment;
  }

  private Assignment tenantWide(Role role) {
    Assignment assignment = requested(callerGroup, role);
    assignment.setScopeType(ScopeType.TENANT);
    return assignment;
  }

  private Assignment unscoped(Role role) {
    return requested(callerGroup, role);
  }
}
