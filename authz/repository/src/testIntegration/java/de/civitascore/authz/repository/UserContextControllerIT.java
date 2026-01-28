package de.civitascore.authz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.model.dto.UserContextResponse.AssignmentContext;
import de.civitascore.authz.repository.model.dto.UserContextResponse.GroupContext;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the UserContext API endpoint.
 *
 * <p>Uses Testcontainers to spin up an isolated PostgreSQL instance for each test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserContextControllerIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private DataSource dataSource;

  @BeforeAll
  void initDatabase() {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ClassPathResource("schema.sql"));
    populator.addScript(new ClassPathResource("seed-test-data.sql"));
    populator.execute(dataSource);
  }

  @Test
  void getUserContext_withValidExternalId_returnsUserContext() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-001", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getUserId())
        .isEqualTo(UUID.fromString("d1111111-1111-1111-1111-111111111111"));
    assertThat(body.getExternalId()).isEqualTo("test-user-001");
    assertThat(body.getGroups()).hasSize(1);

    GroupContext group = body.getGroups().get(0);
    assertThat(group.getName()).isEqualTo("Readers Group");
    assertThat(group.getAssignments()).hasSize(1);

    AssignmentContext assignment = group.getAssignments().get(0);
    assertThat(assignment.getRoleName()).isEqualTo("DataReader");
    assertThat(assignment.getRoleType()).isEqualTo("STANDARD");
    assertThat(assignment.getScopeType()).isEqualTo("TENANT");
    assertThat(assignment.getScopeId()).isEqualTo("tenant-001");
    assertThat(assignment.getPermissions()).containsExactly("dataset:read");
  }

  @Test
  void getUserContext_withMultipleGroups_returnsAllGroupsAndAssignments() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-002", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-002");
    assertThat(body.getGroups()).hasSize(2);

    List<String> groupNames = body.getGroups().stream().map(GroupContext::getName).toList();
    assertThat(groupNames).containsExactlyInAnyOrder("Readers Group", "Editors Group");

    GroupContext editorsGroup =
        body.getGroups().stream()
            .filter(g -> g.getName().equals("Editors Group"))
            .findFirst()
            .orElseThrow();
    assertThat(editorsGroup.getAssignments()).hasSize(2);

    List<String> roleNames =
        editorsGroup.getAssignments().stream().map(AssignmentContext::getRoleName).toList();
    assertThat(roleNames).containsExactlyInAnyOrder("DataEditor", "TenantAdmin");
  }

  @Test
  void getUserContext_withNoGroups_returnsEmptyGroups() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-003", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-003");
    assertThat(body.getGroups()).isEmpty();
  }

  @Test
  void getUserContext_withNonExistentExternalId_returns404() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity(
            "/api/v1/user-context/non-existent-user", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void healthEndpoint_returnsUp() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
  }

  // ============================================
  // Edge case tests
  // ============================================

  @Test
  void getUserContext_withGroupButNoAssignments_returnsEmptyAssignmentsList() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-004", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-004");
    assertThat(body.getGroups()).hasSize(1);

    GroupContext group = body.getGroups().get(0);
    assertThat(group.getName()).isEqualTo("Empty Assignments Group");
    assertThat(group.getAssignments()).isEmpty();
  }

  @Test
  void getUserContext_withRoleHavingNoPermissions_returnsEmptyPermissionsList() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-005", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-005");
    assertThat(body.getGroups()).hasSize(1);

    GroupContext group = body.getGroups().get(0);
    assertThat(group.getAssignments()).hasSize(1);

    AssignmentContext assignment = group.getAssignments().get(0);
    assertThat(assignment.getRoleName()).isEqualTo("EmptyRole");
    assertThat(assignment.getPermissions()).isEmpty();
  }

  @Test
  void getUserContext_withManyPermissions_returnsAllPermissionsSorted() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-006", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-006");

    GroupContext group = body.getGroups().get(0);
    AssignmentContext assignment = group.getAssignments().get(0);

    // Verify all 10 permissions are returned
    assertThat(assignment.getPermissions()).hasSize(10);

    // Verify they are sorted alphabetically
    assertThat(assignment.getPermissions())
        .containsExactly(
            "alpha:write",
            "beta:delete",
            "delta:update",
            "epsilon:list",
            "gamma:create",
            "iota:import",
            "kappa:admin",
            "lambda:view",
            "theta:export",
            "zebra:read");
  }

  @Test
  void getUserContext_withSameRoleAtDifferentScopes_returnsBothAssignments() {
    ResponseEntity<UserContextResponse> response =
        restTemplate.getForEntity("/api/v1/user-context/test-user-007", UserContextResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    UserContextResponse body = response.getBody();
    assertThat(body.getExternalId()).isEqualTo("test-user-007");

    GroupContext group = body.getGroups().get(0);
    assertThat(group.getName()).isEqualTo("Multi Scope Group");
    assertThat(group.getAssignments()).hasSize(2);

    // Both assignments have the same role (DataReader) but different scopes
    List<String> roleNames =
        group.getAssignments().stream().map(AssignmentContext::getRoleName).toList();
    assertThat(roleNames).containsOnly("DataReader");

    // Different scope types
    List<String> scopeTypes =
        group.getAssignments().stream().map(AssignmentContext::getScopeType).toList();
    assertThat(scopeTypes).containsExactlyInAnyOrder("TENANT", "DATASPACE");

    // Different scope IDs
    List<String> scopeIds =
        group.getAssignments().stream().map(AssignmentContext::getScopeId).toList();
    assertThat(scopeIds).containsExactlyInAnyOrder("tenant-A", "dataspace-B");
  }
}
