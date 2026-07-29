package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTParser;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end authorization tests for referencing data sources when creating a pipeline. Unlike the
 * collection-scope tests, these drive real DATASOURCE/TENANT assignments through the database (the
 * source the {@code ScopeAccessAuthorizer} consults), rather than hand-writing a scope header — the
 * header is DATASET-typed on this route and does not carry DataSource authorization.
 */
@DisplayName("Pipeline DataSource Authorization Integration Tests")
class PipelineDataSourceAuthorizationIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private PipelineRepository pipelineRepository;

  private String accessToken;
  private String externalId;
  private Group group;

  @BeforeEach
  void setUp() throws Exception {
    accessToken = getValidAccessToken();
    externalId = JWTParser.parse(accessToken).getJWTClaimsSet().getSubject();

    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail("pipeline-authz." + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
    user.setExternalId(externalId);
    user = userRepository.save(user);

    group = new Group();
    group.setName("authz-group-" + UUID.randomUUID().toString().substring(0, 8));
    group.getMembers().add(user);
    group = groupRepository.save(group);
  }

  @AfterEach
  void cleanup() {
    // Pipelines hold FKs into data_sources; drop them before their referenced data sources.
    pipelineRepository.deleteAll();
    assignmentRepository.deleteAll();
    dataSourceRepository.deleteAll();
    dataSetRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
    userRepository.deleteAll();
  }

  private Role dataRoleWith(String permissionName) {
    Permission permission = permissionRepository.findByName(permissionName).orElseThrow();
    Role role = new Role();
    role.setName("authz-role-" + UUID.randomUUID().toString().substring(0, 8));
    role.setRoleType(RoleType.DATA);
    role.setPermissions(Set.of(permission));
    return roleRepository.save(role);
  }

  private DataSource availableDataSource() {
    DataSource ds = new DataSource();
    ds.setName("authz-source-" + UUID.randomUUID().toString().substring(0, 8));
    ds.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    return dataSourceRepository.save(ds);
  }

  private DataSet draftDataSet() {
    DataSet ds = new DataSet();
    ds.setName("authz-dataset-" + UUID.randomUUID().toString().substring(0, 8));
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    ds.setOpenDataAccess(false);
    return dataSetRepository.save(ds);
  }

  private ResponseEntity<String> createPipelineReferencing(UUID dataSetId, UUID dataSourceId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    // A non-wildcard scope header: the route is DATASET-typed, so a specific dataset scope here
    // does not authorize the referenced data sources — that is exactly what the DB-resolved
    // authorizer must decide. The wildcard path is covered by the unit tests.
    headers.set(AllowedScopesFilter.HEADER_NAME, UUID.randomUUID().toString());

    Map<String, Object> body =
        Map.of(
            "name",
            "authz-pipeline-" + UUID.randomUUID().toString().substring(0, 8),
            "dataSourceIds",
            Set.of(dataSourceId.toString()));

    return restTemplate.exchange(
        "/datasets/" + dataSetId + "/pipelines",
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        String.class);
  }

  @Test
  @DisplayName("Grants when a DATASOURCE assignment covers the referenced source")
  void grantsWithScopedAssignment() {
    DataSource ds = availableDataSource();
    Role role = dataRoleWith("DATASOURCE_READ");
    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScope(ds);
    assignmentRepository.save(assignment);

    ResponseEntity<String> response = createPipelineReferencing(draftDataSet().getId(), ds.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("Denies when the DATASOURCE assignment is for a different source")
  void deniesWhenScopedToDifferentSource() {
    DataSource permitted = availableDataSource();
    DataSource referenced = availableDataSource();
    Role role = dataRoleWith("DATASOURCE_READ");
    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScope(permitted);
    assignmentRepository.save(assignment);

    ResponseEntity<String> response =
        createPipelineReferencing(draftDataSet().getId(), referenced.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("Grants any source via a TENANT assignment with DATASOURCE_READ (cascade)")
  void grantsViaTenantCascade() {
    DataSource ds = availableDataSource();
    Role role = dataRoleWith("DATASOURCE_READ");
    Assignment assignment =
        Assignment.builder()
            .group(group)
            .role(role)
            .scopeType(de.civitascore.portal.model.embedded.ScopeType.TENANT)
            .build();
    assignmentRepository.save(assignment);

    ResponseEntity<String> response = createPipelineReferencing(draftDataSet().getId(), ds.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("Denies when the caller has no assignment covering the source")
  void deniesWithoutAssignment() {
    DataSource ds = availableDataSource();

    ResponseEntity<String> response = createPipelineReferencing(draftDataSet().getId(), ds.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
