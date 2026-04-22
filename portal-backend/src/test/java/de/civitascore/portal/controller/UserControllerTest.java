package de.civitascore.portal.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.output.MeAssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.model.output.assembler.UserAssembler;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import de.civitascore.portal.service.UserService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {OAuth2ResourceServerAutoConfiguration.class})
@ContextConfiguration(classes = {UserController.class})
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private UserAssembler userAssembler;
  @MockitoBean private AllowedScopes allowedScopes;
  @MockitoBean private AssignmentService assignmentService;
  @MockitoBean private AssignmentAssembler assignmentAssembler;

  @Test
  @DisplayName("Authenticated user should get profile with empty assignments when no groups")
  void testGetCurrentUserReturnsProfileWithEmptyAssignments() throws Exception {
    UUID userId = UUID.randomUUID();
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .givenName("Test")
            .familyName("User")
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
            .build();

    when(assignmentService.findAllByUserExternalId(userId.toString())).thenReturn(List.of());
    when(assignmentAssembler.toMeAssignments(anyList())).thenReturn(List.of());

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("testuser"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.firstName").value("Test"))
        .andExpect(jsonPath("$.lastName").value("User"))
        .andExpect(jsonPath("$.title").value("OTHER"))
        .andExpect(jsonPath("$.assignments").isArray())
        .andExpect(jsonPath("$.assignments").isEmpty());
  }

  @Test
  @DisplayName("Authenticated user should get assignments with permissions and scope")
  void testGetCurrentUserReturnsAssignments() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID scopeId = UUID.randomUUID();
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .userId(userId)
            .username("testuser")
            .email("test@example.com")
            .givenName("Test")
            .familyName("User")
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
            .build();

    when(assignmentService.findAllByUserExternalId(userId.toString())).thenReturn(List.of());

    MeAssignmentOutputDTO meAssignment = new MeAssignmentOutputDTO();
    meAssignment.setScopeType(ScopeType.DATASET);
    meAssignment.setScopeId(scopeId);
    meAssignment.setPermissions(Set.of("DATASET_READ", "DATASET_CREATE"));

    when(assignmentAssembler.toMeAssignments(anyList())).thenReturn(List.of(meAssignment));

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments").isArray())
        .andExpect(jsonPath("$.assignments.length()").value(1))
        .andExpect(jsonPath("$.assignments[0].scopeType").value("DATASET"))
        .andExpect(jsonPath("$.assignments[0].scopeId").value(scopeId.toString()))
        .andExpect(jsonPath("$.assignments[0].permissions").isArray())
        .andExpect(jsonPath("$.assignments[0].permissions.length()").value(2));
  }

  @Test
  @DisplayName("TENANT-scoped assignment should have null scopeId")
  void testTenantScopedAssignmentHasNullScopeId() throws Exception {
    UUID userId = UUID.randomUUID();
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .userId(userId)
            .username("admin")
            .email("admin@example.com")
            .givenName("Admin")
            .familyName("User")
            .authorities(List.of())
            .build();

    when(assignmentService.findAllByUserExternalId(userId.toString())).thenReturn(List.of());

    MeAssignmentOutputDTO meAssignment = new MeAssignmentOutputDTO();
    meAssignment.setScopeType(ScopeType.TENANT);
    meAssignment.setScopeId(null);
    meAssignment.setPermissions(Set.of("USER_READ", "USER_CREATE"));

    when(assignmentAssembler.toMeAssignments(anyList())).thenReturn(List.of(meAssignment));

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments[0].scopeType").value("TENANT"))
        .andExpect(jsonPath("$.assignments[0].scopeId").doesNotExist());
  }

  @Test
  @DisplayName("User with null userId should get empty assignments")
  void testNullUserIdReturnsEmptyAssignments() throws Exception {
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .userId(null)
            .username("service-account")
            .email("sa@example.com")
            .givenName("Service")
            .familyName("Account")
            .authorities(List.of())
            .build();

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments").isArray())
        .andExpect(jsonPath("$.assignments").isEmpty());
  }

  @Test
  @DisplayName("Unauthenticated user should get 401 Unauthorized")
  void testUnauthenticatedUserReturns401() throws Exception {
    mockMvc
        .perform(get("/users/me").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
