/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.UserConfig;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

class KeycloakUserIntegrationTest extends KeycloakAdapterIntegrationTestBase {

  @Test
  void shouldCreateUser() throws FatalAdapterException, RetryableAdapterException {
    createRealm("user-realm");
    RoleRepresentation roleRep = new RoleRepresentation();
    roleRep.setName("testrole");
    keycloakClient.realm("user-realm").roles().create(roleRep);

    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("testuser");
    userConfig.setEmail("testuser@example.com");
    userConfig.setFirstName("Test");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);
    userConfig.setRealmRoles(List.of("testrole"));

    adapter.processConfigEvent(
        Topics.USER_CREATED.toString(),
        createConfigEvent("user-realm", "user", Operation.CREATE, userConfig));

    List<UserRepresentation> users = keycloakClient.realm("user-realm").users().search("testuser");
    assertEquals(1, users.size());
    UserRepresentation createdUser = users.getFirst();
    assertEquals("testuser", createdUser.getUsername());
    assertEquals("testuser@example.com", createdUser.getEmail());
    assertTrue(createdUser.isEnabled());
    List<RoleRepresentation> userRoles =
        keycloakClient
            .realm("user-realm")
            .users()
            .get(createdUser.getId())
            .roles()
            .realmLevel()
            .listAll();
    assertTrue(
        userRoles.stream().anyMatch(role -> role.getName().equals("testrole")),
        "Role 'testrole' not assigned");
    assertSingleSuccessResult();
  }

  @Test
  void updateUser_whenRoleMissingInConfig_shouldRemoveRoleFromUser()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("user-sync-realm");

    RoleRepresentation roleA = new RoleRepresentation();
    roleA.setName("role-a");
    RoleRepresentation roleB = new RoleRepresentation();
    roleB.setName("role-b");
    keycloakClient.realm("user-sync-realm").roles().create(roleA);
    keycloakClient.realm("user-sync-realm").roles().create(roleB);

    RealmResource realmResource = keycloakClient.realm("user-sync-realm");
    UsersResource users = realmResource.users();
    RolesResource roles = realmResource.roles();
    UserRepresentation userRep = new UserRepresentation();
    userRep.setUsername("sync-user");
    userRep.setEnabled(true);
    String userId;
    try (Response response = realmResource.users().create(userRep)) {
      userId = CreatedResponseUtil.getCreatedId(response);
      users
          .get(userId)
          .roles()
          .realmLevel()
          .add(
              List.of(
                  roles.get("role-a").toRepresentation(), roles.get("role-b").toRepresentation()));
    }

    UserConfig userUpdateConfig = new UserConfig();
    userUpdateConfig.setId(userId);
    userUpdateConfig.setUsername("sync-user");
    userUpdateConfig.setRealmRoles(List.of("role-a"));

    adapter.processConfigEvent(
        Topics.USER_UPDATED.toString(),
        createConfigEvent("user-sync-realm", "user", Operation.UPDATE, userUpdateConfig));

    String resolvedUserId = users.search("sync-user").getFirst().getId();
    List<RoleRepresentation> currentRoles =
        users.get(resolvedUserId).roles().realmLevel().listAll();
    assertTrue(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-a")),
        "role-a should still be assigned");
    assertFalse(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-b")),
        "role-b should have been removed");
  }

  @Test
  void shouldCreateUserAndSendActionsEmail()
      throws FatalAdapterException, RetryableAdapterException {
    String realmName = "email-realm";
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm(realmName);
    realmRep.setEnabled(true);

    Map<String, String> smtpConfig = new HashMap<>();
    smtpConfig.put("host", "mailpit");
    smtpConfig.put("port", "1025");
    smtpConfig.put("from", "noreply@test.local");
    smtpConfig.put("fromDisplayName", "Test");
    smtpConfig.put("ssl", "false");
    smtpConfig.put("starttls", "false");
    smtpConfig.put("auth", "false");
    realmRep.setSmtpServer(smtpConfig);
    keycloakClient.realms().create(realmRep);

    ClientRepresentation portalClient = new ClientRepresentation();
    portalClient.setClientId("test-portal");
    portalClient.setEnabled(true);
    portalClient.setRedirectUris(List.of("http://localhost:3000/*"));
    keycloakClient.realm(realmName).clients().create(portalClient);

    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("emailuser");
    userConfig.setEmail("emailuser@test.local");
    userConfig.setFirstName("Email");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);
    userConfig.setRequiredActions(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));

    adapter.processConfigEvent(
        Topics.USER_CREATED.toString(),
        createConfigEvent(realmName, "user", Operation.CREATE, userConfig));

    List<UserRepresentation> users = keycloakClient.realm(realmName).users().search("emailuser");
    assertEquals(1, users.size());
    UserRepresentation createdUser = users.getFirst();
    assertTrue(createdUser.getRequiredActions().contains("VERIFY_EMAIL"));
    assertTrue(createdUser.getRequiredActions().contains("UPDATE_PASSWORD"));

    String mailpitApiUrl = getMailpitApiUrl();
    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              var httpClient = HttpClient.newHttpClient();
              var request = HttpRequest.newBuilder().uri(URI.create(mailpitApiUrl)).GET().build();
              var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
              assertTrue(response.body().contains("emailuser@test.local"));
            });
    assertSingleSuccessResult();
  }

  @Test
  void deleteUser_whenUserDoesNotExist_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("idempotent-user-realm");

    UserConfig userConfig = new UserConfig();
    userConfig.setId(UUID.randomUUID().toString());
    userConfig.setUsername("ghost-user");

    adapter.processConfigEvent(
        Topics.USER_DELETED.toString(),
        createConfigEvent("idempotent-user-realm", "user", Operation.DELETE, userConfig));

    assertSingleSuccessResult();
  }

  @Test
  void createUser_whenUserAlreadyExists_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("duplicate-user-realm");

    UserRepresentation existingUser = new UserRepresentation();
    existingUser.setUsername("duplicate-user");
    existingUser.setEnabled(true);
    try (Response response =
        keycloakClient.realm("duplicate-user-realm").users().create(existingUser)) {
      assertEquals(201, response.getStatus());
    }
    eventPublisher.clear();

    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("duplicate-user");
    userConfig.setEnabled(true);

    adapter.processConfigEvent(
        Topics.USER_CREATED.toString(),
        createConfigEvent("duplicate-user-realm", "user", Operation.CREATE, userConfig));

    assertSingleSuccessResult();
  }

  @Test
  void shouldCreateUserAndSendActionsEmailWithoutInvitationRedirect()
      throws FatalAdapterException, RetryableAdapterException {
    // Test fallback: when invitation config is NOT set, the simple executeActionsEmail overload
    // is used and failures are logged as warnings (not propagated).
    String realmName = "email-no-redirect-realm";
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm(realmName);
    realmRep.setEnabled(true);

    Map<String, String> smtpConfig = new HashMap<>();
    smtpConfig.put("host", "mailpit");
    smtpConfig.put("port", "1025");
    smtpConfig.put("from", "noreply@test.local");
    smtpConfig.put("fromDisplayName", "Test");
    smtpConfig.put("ssl", "false");
    smtpConfig.put("starttls", "false");
    smtpConfig.put("auth", "false");
    realmRep.setSmtpServer(smtpConfig);
    keycloakClient.realms().create(realmRep);

    // Create adapter WITHOUT invitation redirect config
    try (KeycloakAdapter noRedirectAdapter = createAdapterWithProps(Map.of())) {
      TestEventPublisher testPublisher = new TestEventPublisher();
      noRedirectAdapter.setEventPublisher(testPublisher);

      UserConfig userConfig = new UserConfig();
      userConfig.setUsername("no-redirect-user");
      userConfig.setEmail("no-redirect-user@test.local");
      userConfig.setFirstName("NoRedirect");
      userConfig.setLastName("User");
      userConfig.setEnabled(true);
      userConfig.setRequiredActions(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));

      noRedirectAdapter.processConfigEvent(
          Topics.USER_CREATED.toString(),
          createConfigEvent(realmName, "user", Operation.CREATE, userConfig));

      // User should be created successfully
      List<UserRepresentation> users =
          keycloakClient.realm(realmName).users().search("no-redirect-user");
      assertEquals(1, users.size());
      assertTrue(users.getFirst().getRequiredActions().contains("VERIFY_EMAIL"));
      assertTrue(users.getFirst().getRequiredActions().contains("UPDATE_PASSWORD"));

      // Email should still be sent via the simple overload
      String mailpitApiUrl = getMailpitApiUrl();
      await()
          .atMost(10, SECONDS)
          .pollInterval(1, SECONDS)
          .untilAsserted(
              () -> {
                var httpClient = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder().uri(URI.create(mailpitApiUrl)).GET().build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                assertTrue(response.body().contains("no-redirect-user@test.local"));
              });

      assertEquals(1, testPublisher.getPublishedEvents().size());
    }
  }

  @Test
  void shouldFailWhenInvitationClientDoesNotExistInRealm() {
    // When invitation redirect is configured but the client doesn't exist in the realm,
    // the error must propagate (not be silently swallowed).
    String realmName = "missing-client-realm";
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm(realmName);
    realmRep.setEnabled(true);

    Map<String, String> smtpConfig = new HashMap<>();
    smtpConfig.put("host", "mailpit");
    smtpConfig.put("port", "1025");
    smtpConfig.put("from", "noreply@test.local");
    smtpConfig.put("fromDisplayName", "Test");
    smtpConfig.put("ssl", "false");
    smtpConfig.put("starttls", "false");
    smtpConfig.put("auth", "false");
    realmRep.setSmtpServer(smtpConfig);
    keycloakClient.realms().create(realmRep);
    // Note: NOT creating the "test-portal" client in this realm

    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("fail-user");
    userConfig.setEmail("fail-user@test.local");
    userConfig.setFirstName("Fail");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);
    userConfig.setRequiredActions(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));

    // The adapter has invitation config set (from setUp), but the client doesn't exist
    // in this realm — the error should propagate as FatalAdapterException
    assertThrows(
        FatalAdapterException.class,
        () ->
            adapter.processConfigEvent(
                Topics.USER_CREATED.toString(),
                createConfigEvent(realmName, "user", Operation.CREATE, userConfig)));
  }

  @Test
  void shouldFailInitializationWithInvalidCredentials() {
    // Verify that the adapter fails fast at startup when Keycloak credentials are invalid
    Map<String, Object> badCredentials = new HashMap<>();
    badCredentials.put("keycloak.username", "wrong-user");
    badCredentials.put("keycloak.password", "wrong-password");

    assertThrows(IllegalStateException.class, () -> createAdapterWithProps(badCredentials));
  }

  @Test
  void shouldFailInitializationWhenOnlyClientIdIsSet() {
    // Verify that setting only invitation.client.id without redirect.uri fails
    Map<String, Object> partialConfig = new HashMap<>();
    partialConfig.put("keycloak.invitation.client.id", "some-client");

    assertThrows(IllegalStateException.class, () -> createAdapterWithProps(partialConfig));
  }

  @Test
  void shouldFailInitializationWhenOnlyRedirectUriIsSet() {
    // Verify that setting only invitation.redirect.uri without client.id fails
    Map<String, Object> partialConfig = new HashMap<>();
    partialConfig.put("keycloak.invitation.redirect.uri", "http://localhost:3000/");

    assertThrows(IllegalStateException.class, () -> createAdapterWithProps(partialConfig));
  }
}
