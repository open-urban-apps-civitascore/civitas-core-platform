package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
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
import org.springframework.http.ResponseEntity;

@DisplayName("/users/me Integration Tests")
class UserMeIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final String ME_ENDPOINT = "/users/me";

  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private ObjectMapper objectMapper;

  private String accessToken;
  private String externalId;

  @BeforeEach
  void setUp() throws Exception {
    accessToken = getValidAccessToken();
    externalId = JWTParser.parse(accessToken).getJWTClaimsSet().getSubject();
  }

  @AfterEach
  void cleanup() {
    assignmentRepository.deleteAll();
    groupRepository.deleteAll();
    roleRepository.deleteAll();
    dataSetRepository.deleteAll();
    userRepository.deleteAll();
  }

  private HttpHeaders createAuthHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return headers;
  }

  private User createUser() {
    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail("testme." + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    user.setExternalId(externalId);
    return userRepository.save(user);
  }

  @Test
  @DisplayName("Should return assignments with permissions and scope")
  void shouldReturnAssignmentsWithPermissionsAndScope() throws Exception {
    // Create user matching JWT sub
    User user = createUser();

    // Create group and add user as member
    Group group = new Group();
    group.setName("Test Group " + UUID.randomUUID().toString().substring(0, 8));
    group.getMembers().add(user);
    group = groupRepository.save(group);

    // Fetch seeded DATASET_READ permission
    Permission datasetReadPermission =
        permissionRepository.findByName("DATASET_READ").orElseThrow();

    // Create DATA role with permission
    Role role = new Role();
    role.setName("Data Role " + UUID.randomUUID().toString().substring(0, 8));
    role.setRoleType(RoleType.DATA);
    role.setPermissions(Set.of(datasetReadPermission));
    role = roleRepository.save(role);

    // Create DataSet for scope
    DataSet dataSet = new DataSet();
    dataSet.setName("Test DataSet " + UUID.randomUUID().toString().substring(0, 8));
    dataSet = dataSetRepository.save(dataSet);

    // Create assignment with dataset scope
    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScope(dataSet);
    assignmentRepository.save(assignment);

    // Call GET /users/me
    ResponseEntity<String> response =
        restTemplate.exchange(
            ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode assignments = body.path("assignments");
    assertThat(assignments.isArray()).isTrue();
    assertThat(assignments.size()).isEqualTo(1);

    JsonNode assignment0 = assignments.get(0);
    assertThat(assignment0.path("scopeType").asText()).isEqualTo("DATASET");
    assertThat(assignment0.path("scopeId").asText()).isEqualTo(dataSet.getId().toString());

    JsonNode permissions = assignment0.path("permissions");
    assertThat(permissions.isArray()).isTrue();
    assertThat(permissions.size()).isGreaterThanOrEqualTo(1);
    boolean hasDatasetRead = false;
    for (JsonNode perm : permissions) {
      if ("DATASET_READ".equals(perm.asText())) {
        hasDatasetRead = true;
        break;
      }
    }
    assertThat(hasDatasetRead).as("permissions should contain DATASET_READ").isTrue();
  }

  @Test
  @DisplayName("Should return null scope for tenant assignment")
  void shouldReturnNullScopeForTenantAssignment() throws Exception {
    // Create user matching JWT sub
    User user = createUser();

    // Create group and add user as member
    Group group = new Group();
    group.setName("System Group " + UUID.randomUUID().toString().substring(0, 8));
    group.getMembers().add(user);
    group = groupRepository.save(group);

    // Create SYSTEM role (no scope required)
    Role role = new Role();
    role.setName("System Role " + UUID.randomUUID().toString().substring(0, 8));
    role.setRoleType(RoleType.SYSTEM);
    role = roleRepository.save(role);

    // Create assignment with no scope (SYSTEM / TENANT)
    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    // Do NOT call setScope — scopeType stays null for SYSTEM roles
    assignmentRepository.save(assignment);

    // Call GET /users/me
    ResponseEntity<String> response =
        restTemplate.exchange(
            ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode assignments = body.path("assignments");
    assertThat(assignments.isArray()).isTrue();
    assertThat(assignments.size()).isEqualTo(1);

    JsonNode assignment0 = assignments.get(0);
    assertThat(assignment0.path("scopeType").isNull()).isTrue();
    assertThat(assignment0.path("scopeId").isNull()).isTrue();
  }

  @Test
  @DisplayName("Should return empty assignments when no groups")
  void shouldReturnEmptyAssignmentsWhenNoGroups() throws Exception {
    // Create user with externalId but no group membership
    createUser();

    ResponseEntity<String> response =
        restTemplate.exchange(
            ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode assignments = body.path("assignments");
    assertThat(assignments.isArray()).isTrue();
    assertThat(assignments.size()).isEqualTo(0);
  }

  @Test
  @DisplayName("Should return empty assignments when user not in DB")
  void shouldReturnEmptyAssignmentsWhenUserNotInDb() throws Exception {
    // Do NOT create any User entity — JWT is valid but no User record exists

    ResponseEntity<String> response =
        restTemplate.exchange(
            ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(createAuthHeaders()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode assignments = body.path("assignments");
    assertThat(assignments.isArray()).isTrue();
    assertThat(assignments.size()).isEqualTo(0);
  }
}
