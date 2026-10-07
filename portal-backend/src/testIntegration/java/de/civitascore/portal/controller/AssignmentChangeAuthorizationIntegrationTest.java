package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTParser;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import de.civitascore.portal.service.AssignmentService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("Assignment changes in data entity bodies")
class AssignmentChangeAuthorizationIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private AssignmentService assignmentService;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataPoolRepository dataPoolRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private DataStructureRepository dataStructureRepository;

  private String accessToken;
  private String callerExternalId;

  /** A write path that accepts {@code assignments} in its body. */
  enum Route {
    DATASET_PATCH(ScopeType.DATASET, "/datasets/%s"),
    DATASET_READY_META(ScopeType.DATASET, "/datasets/%s/ready/meta"),
    DATASET_RELEASED_META(ScopeType.DATASET, "/datasets/%s/released/meta"),
    DATAPOOL_PATCH(ScopeType.DATAPOOL, "/datapools/%s"),
    DATASOURCE_PATCH(ScopeType.DATASOURCE, "/datasources/%s"),
    DATASOURCE_RELEASED_META(ScopeType.DATASOURCE, "/datasources/%s/released/meta"),
    DATASTRUCTURE_PATCH(ScopeType.DATASTRUCTURE, "/datastructures/%s"),
    DATASTRUCTURE_RELEASED_META(ScopeType.DATASTRUCTURE, "/datastructures/%s/released/meta");

    private final ScopeType scopeType;
    private final String pathTemplate;

    Route(ScopeType scopeType, String pathTemplate) {
      this.scopeType = scopeType;
      this.pathTemplate = pathTemplate;
    }
  }

  /** A create endpoint that accepts {@code assignments} in its body, beside the dataset one. */
  enum CreateRoute {
    DATAPOOL(ScopeType.DATAPOOL, "/datapools"),
    DATASTRUCTURE(ScopeType.DATASTRUCTURE, "/datastructures");

    private final ScopeType scopeType;
    private final String path;

    CreateRoute(ScopeType scopeType, String path) {
      this.scopeType = scopeType;
      this.path = path;
    }
  }

  /** An assignment as the body carries it; the scope is always the target entity. */
  record GroupRole(UUID groupId, UUID roleId) {
    GroupRole(Group group, Role role) {
      this(group.getId(), role.getId());
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    accessToken = getValidAccessToken();
    callerExternalId = JWTParser.parse(accessToken).getJWTClaimsSet().getSubject();
  }

  @AfterEach
  void cleanUp() {
    portalData.cleanAll();
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should reject a Data Steward making their own group Data Owner")
  void stewardGrantingOwner_isForbidden(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    Role steward = portalData.role(RoleDefault.DATA_STEWARD);
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(caller, steward, target);
    GroupRole own = new GroupRole(caller, steward);

    ResponseEntity<String> response =
        patch(route, target, "renamed", Set.of(own, new GroupRole(caller, owner)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(stored(route.scopeType, target)).containsExactly(own);
    assertThat(storedName(target)).isEqualTo(target.getName());
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should reject a Data Steward removing a Data Owner")
  void stewardRemovingOwner_isForbidden(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    Role steward = portalData.role(RoleDefault.DATA_STEWARD);
    Group ownerGroup = portalData.group();
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(caller, steward, target);
    assign(ownerGroup, owner, target);
    GroupRole own = new GroupRole(caller, steward);

    ResponseEntity<String> response = patch(route, target, "renamed", Set.of(own));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(stored(route.scopeType, target))
        .containsExactlyInAnyOrder(own, new GroupRole(ownerGroup, owner));
    assertThat(storedName(target)).isEqualTo(target.getName());
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should reject a Data Steward moving the Data Owner role to their own group")
  void stewardMovingOwnerToOwnGroup_isForbidden(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    Role steward = portalData.role(RoleDefault.DATA_STEWARD);
    Group ownerGroup = portalData.group();
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(caller, steward, target);
    assign(ownerGroup, owner, target);
    GroupRole own = new GroupRole(caller, steward);

    ResponseEntity<String> response =
        patch(route, target, "renamed", Set.of(own, new GroupRole(caller, owner)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(stored(route.scopeType, target))
        .containsExactlyInAnyOrder(own, new GroupRole(ownerGroup, owner));
    assertThat(storedName(target)).isEqualTo(target.getName());
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should let a Data Owner remove a role within their own permissions")
  void ownerRemovingSteward_isAllowed(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(caller, owner, target);
    assign(portalData.group(), portalData.role(RoleDefault.DATA_STEWARD), target);
    GroupRole own = new GroupRole(caller, owner);

    ResponseEntity<String> response = patch(route, target, "renamed", Set.of(own));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stored(route.scopeType, target)).containsExactly(own);
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should let a Data Owner grant a role within their own permissions")
  void ownerGrantingSteward_isAllowed(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    Role steward = portalData.role(RoleDefault.DATA_STEWARD);
    assign(caller, owner, target);
    GroupRole own = new GroupRole(caller, owner);
    GroupRole granted = new GroupRole(portalData.group(), steward);

    ResponseEntity<String> response = patch(route, target, "renamed", Set.of(own, granted));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stored(route.scopeType, target)).containsExactlyInAnyOrder(own, granted);
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should let a Tenant Admin add and remove any role in one request")
  void tenantAdminReplacingRoles_isAllowed(Route route) {
    BaseDataEntity target = target(route);
    Group caller = portalData.groupOf(callerExternalId);
    portalData.unscopedAssignment(caller, portalData.role(RoleDefault.TENANT_ADMIN));
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(portalData.group(), owner, target);
    GroupRole replacement = new GroupRole(portalData.group(), owner);

    ResponseEntity<String> response = patch(route, target, "renamed", Set.of(replacement));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stored(route.scopeType, target)).containsExactly(replacement);
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should accept an unchanged assignment list from a caller without any role")
  void unchangedAssignments_needNoPermission(Route route) {
    BaseDataEntity target = target(route);
    Group ownerGroup = portalData.group();
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(ownerGroup, owner, target);
    GroupRole existing = new GroupRole(ownerGroup, owner);

    ResponseEntity<String> response = patch(route, target, "renamed", Set.of(existing));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stored(route.scopeType, target)).containsExactly(existing);
    assertThat(storedName(target)).isEqualTo("renamed");
  }

  @ParameterizedTest
  @EnumSource(Route.class)
  @DisplayName("Should leave assignments untouched when the body omits them")
  void omittedAssignments_needNoPermission(Route route) {
    BaseDataEntity target = target(route);
    Group ownerGroup = portalData.group();
    Role owner = portalData.role(RoleDefault.DATA_OWNER);
    assign(ownerGroup, owner, target);

    ResponseEntity<String> response = patch(route, target, "renamed", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stored(route.scopeType, target)).containsExactly(new GroupRole(ownerGroup, owner));
  }

  @Test
  @DisplayName("Should create a dataset with an empty assignment list without any role")
  void createWithEmptyAssignments_needsNoPermission() {
    ResponseEntity<String> response = createDataSet("created", null, Set.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("Should reject a pool Data Steward creating a dataset with a Data Owner")
  void stewardCreatingDataSetWithOwner_isForbidden() {
    DataPool pool = portalData.dataPool();
    Group caller = portalData.groupOf(callerExternalId);
    portalData.assignment(caller, portalData.role(RoleDefault.DATA_STEWARD), pool);
    GroupRole owner = new GroupRole(caller, portalData.role(RoleDefault.DATA_OWNER));

    ResponseEntity<String> response = createDataSet("escalated", pool.getId(), Set.of(owner));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(dataSetRepository.findByName("escalated")).isEmpty();
  }

  @Test
  @DisplayName("Should let a pool Data Owner create a dataset with a Data Steward")
  void poolOwnerCreatingDataSetWithSteward_isAllowed() {
    DataPool pool = portalData.dataPool();
    Group caller = portalData.groupOf(callerExternalId);
    portalData.assignment(caller, portalData.role(RoleDefault.DATA_OWNER), pool);
    GroupRole steward =
        new GroupRole(portalData.group(), portalData.role(RoleDefault.DATA_STEWARD));

    ResponseEntity<String> response = createDataSet("delegated", pool.getId(), Set.of(steward));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DataSet created = dataSetRepository.findByName("delegated").orElseThrow();
    assertThat(stored(ScopeType.DATASET, created)).containsExactly(steward);
  }

  @ParameterizedTest
  @EnumSource(CreateRoute.class)
  @DisplayName("Should reject a tenant-wide Data Steward creating an entity with a Data Owner")
  void stewardCreatingWithOwner_isForbidden(CreateRoute route) {
    Group caller = portalData.groupOf(callerExternalId);
    portalData.assignment(caller, portalData.role(RoleDefault.DATA_STEWARD));
    GroupRole owner = new GroupRole(caller, portalData.role(RoleDefault.DATA_OWNER));

    ResponseEntity<String> response = create(route, "escalated", Set.of(owner));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(createdNamed(route, "escalated")).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(CreateRoute.class)
  @DisplayName("Should let a tenant-wide Data Owner create an entity with a Data Steward")
  void ownerCreatingWithSteward_isAllowed(CreateRoute route) {
    Group caller = portalData.groupOf(callerExternalId);
    portalData.assignment(caller, portalData.role(RoleDefault.DATA_OWNER));
    GroupRole steward =
        new GroupRole(portalData.group(), portalData.role(RoleDefault.DATA_STEWARD));

    ResponseEntity<String> response = create(route, "delegated", Set.of(steward));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    BaseDataEntity created = createdNamed(route, "delegated").orElseThrow();
    assertThat(stored(route.scopeType, created)).containsExactly(steward);
  }

  private ResponseEntity<String> create(
      CreateRoute route, String name, Set<GroupRole> assignments) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("description", "created in test");
    body.put("assignments", assignments);
    return restTemplate.exchange(
        route.path, HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
  }

  private Optional<? extends BaseDataEntity> createdNamed(CreateRoute route, String name) {
    return switch (route) {
      case DATAPOOL -> dataPoolRepository.findByName(name);
      case DATASTRUCTURE -> dataStructureRepository.findByName(name);
    };
  }

  private BaseDataEntity target(Route route) {
    return switch (route) {
      case DATASET_PATCH -> portalData.dataSet();
      case DATASET_READY_META -> portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.READY));
      case DATASET_RELEASED_META ->
          portalData.dataSet(b -> b.dataSetStatus(DataSetStatus.AVAILABLE));
      case DATAPOOL_PATCH -> portalData.dataPool(b -> b.description("test datapool"));
      case DATASOURCE_PATCH -> portalData.dataSource();
      case DATASOURCE_RELEASED_META ->
          portalData.dataSource(b -> b.dataSourceStatus(DataSourceStatus.AVAILABLE));
      case DATASTRUCTURE_PATCH -> portalData.dataStructure();
      case DATASTRUCTURE_RELEASED_META ->
          portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE));
    };
  }

  private void assign(Group group, Role role, BaseDataEntity target) {
    switch (target) {
      case DataSet dataSet -> portalData.assignment(group, role, dataSet);
      case DataPool dataPool -> portalData.assignment(group, role, dataPool);
      case DataSource dataSource -> portalData.assignment(group, role, dataSource);
      case DataStructure dataStructure -> portalData.assignment(group, role, dataStructure);
      default -> throw new IllegalArgumentException("Unsupported target " + target);
    }
  }

  private Set<GroupRole> stored(ScopeType scopeType, BaseDataEntity target) {
    return assignmentService.findAllByScopeTypeAndScopeId(scopeType, target.getId()).stream()
        .map(a -> new GroupRole(a.getGroup(), a.getRole()))
        .collect(Collectors.toSet());
  }

  private String storedName(BaseDataEntity target) {
    UUID id = target.getId();
    return switch (target) {
      case DataSet ignored -> dataSetRepository.findById(id).orElseThrow().getName();
      case DataPool ignored -> dataPoolRepository.findById(id).orElseThrow().getName();
      case DataSource ignored -> dataSourceRepository.findById(id).orElseThrow().getName();
      case DataStructure ignored -> dataStructureRepository.findById(id).orElseThrow().getName();
      default -> throw new IllegalArgumentException("Unsupported target " + target);
    };
  }

  private ResponseEntity<String> patch(
      Route route, BaseDataEntity target, String name, Set<GroupRole> assignments) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    if (assignments != null) {
      body.put("assignments", assignments);
    }
    return restTemplate.exchange(
        route.pathTemplate.formatted(target.getId()),
        HttpMethod.PATCH,
        new HttpEntity<>(body, headers()),
        String.class);
  }

  private ResponseEntity<String> createDataSet(
      String name, UUID datapoolId, Set<GroupRole> assignments) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("description", "created in test");
    body.put("assignments", assignments);
    if (datapoolId != null) {
      body.put("datapoolId", datapoolId);
    }
    return restTemplate.exchange(
        "/datasets", HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }
}
