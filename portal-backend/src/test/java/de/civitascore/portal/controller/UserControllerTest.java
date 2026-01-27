package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.model.output.PrincipalUserOutput;
import de.civitascore.portal.model.output.assembler.UserAssembler;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.UserService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

  @Test
  @DisplayName("Authenticated user should get current user profile with roles")
  void testGetCurrentUserReturnsUserProfile() throws Exception {
    // given
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .username("testuser")
            .email("test@example.com")
            .givenName("Test")
            .familyName("User")
            .authorities(
                List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")))
            .build();

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    // when & then
    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("testuser"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.firstName").value("Test"))
        .andExpect(jsonPath("$.lastName").value("User"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.roles[1]").value("USER"))
        .andExpect(jsonPath("$.title").value("OTHER"));
  }

  @Test
  @DisplayName("User roles should be sorted alphabetically without ROLE_ prefix")
  void testRolesSortedWithoutRolePrefix() throws Exception {
    // given
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .username("admin")
            .email("admin@example.com")
            .givenName("Admin")
            .familyName("User")
            .authorities(
                List.of(
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_USER")))
            .build();

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    // when & then
    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles.length()").value(3))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
        .andExpect(jsonPath("$.roles[1]").value("SUPER_ADMIN"))
        .andExpect(jsonPath("$.roles[2]").value("USER"));
  }

  @Test
  @DisplayName("User with no roles should return empty roles list")
  void testUserWithNoRoles() throws Exception {
    // given
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .username("noroles")
            .email("noroles@example.com")
            .givenName("No")
            .familyName("Roles")
            .authorities(List.of())
            .build();

    Authentication auth =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    // when & then
    mockMvc
        .perform(
            get("/users/me").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("noroles"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles").isEmpty());
  }

  @Test
  @DisplayName("Unauthenticated user should get 401 Unauthorized")
  void testUnauthenticatedUserReturns401() throws Exception {
    // when & then
    mockMvc
        .perform(get("/users/me").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PrincipalUserOutput factory method should correctly map user details")
  void testPrincipalUserOutputFromPrincipal() {
    // given
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .title(UserTitleType.MS)
            .username("factorytest")
            .email("factory@test.com")
            .givenName("Factory")
            .familyName("Test")
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_TESTER")))
            .build();

    // when
    PrincipalUserOutput output = PrincipalUserOutput.fromPrincipal(userDetails);

    // then
    assertThat(output.username()).isEqualTo("factorytest");
    assertThat(output.email()).isEqualTo("factory@test.com");
    assertThat(output.firstName()).isEqualTo("Factory");
    assertThat(output.lastName()).isEqualTo("Test");
    assertThat(output.roles()).containsExactly("TESTER");
    assertThat(output.title()).isEqualTo(UserTitleType.MS);
  }
}
