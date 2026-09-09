package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

  @Mock private ConfigEventPublisherService configEventPublisher;
  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;
  @Mock private GroupRepository groupRepository;

  private static final String TARGET_REALM = "test-realm";
  private static final String AUTH_SERVER_URL = "http://keycloak:8080";
  private static final KeycloakProperties KEYCLOAK_PROPERTIES =
      new KeycloakProperties(TARGET_REALM, AUTH_SERVER_URL, TARGET_REALM, true, false);
  private static final int CONFIG_ADAPTER_TIMEOUT_SECONDS = 10;

  private UserService createService() {
    return createService(KEYCLOAK_PROPERTIES);
  }

  private UserService createService(KeycloakProperties keycloakProperties) {
    return new UserService(
        configEventPublisher,
        userRepository,
        userMapper,
        groupRepository,
        keycloakProperties,
        new EventProperties(CONFIG_ADAPTER_TIMEOUT_SECONDS));
  }

  private User userWithId(UUID id) {
    User user = new User();
    user.setId(id);
    user.setEmail("test@example.com");
    user.setFirstName("John");
    user.setLastName("Doe");
    return user;
  }

  // ---------------------------------------------------------------------------
  // validateUniqueEmail (called via preSave)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("validateUniqueEmail()")
  class ValidateUniqueEmailTests {

    @Test
    @DisplayName("Should pass when no user with same email exists")
    void shouldPassWhenEmailNotFound() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());

      assertThatNoException().isThrownBy(() -> service.preSave(user));
    }

    @Test
    @DisplayName("Should pass when found user is the same entity (update case)")
    void shouldPassWhenSameEntityFound() {
      UserService service = createService();
      UUID id = UUID.randomUUID();
      User user = userWithId(id);
      User existing = userWithId(id);
      when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(existing));

      assertThatNoException().isThrownBy(() -> service.preSave(user));
    }

    @Test
    @DisplayName("Should throw when different user has same email")
    void shouldThrowWhenDifferentEntityHasSameEmail() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      User existing = userWithId(UUID.randomUUID());
      existing.setEmail(user.getEmail());
      when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.preSave(user))
          .isInstanceOf(UniqueConstraintViolationException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // toConfigValuePreSave / toConfigValuePostSave (previously toConfigValue)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("toConfigValuePreSave()")
  class ToConfigValuePreSaveTests {

    @Test
    @DisplayName(
        "Should set VERIFY_EMAIL, UPDATE_PASSWORD and CONFIGURE_TOTP for new user (no externalId)")
    void shouldSetRequiredActionsForNewUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);
      UserInputDTO input = new UserInputDTO();
      input.setEmail(user.getEmail());

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getRequiredActions())
          .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
      assertThat(config.getEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Should omit CONFIGURE_TOTP for new user when OTP enforcement is disabled")
    void shouldOmitConfigureTotpWhenEnforcementDisabled() {
      UserService service =
          createService(
              new KeycloakProperties(TARGET_REALM, AUTH_SERVER_URL, TARGET_REALM, false, false));
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);
      UserInputDTO input = new UserInputDTO();
      input.setEmail(user.getEmail());

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getRequiredActions())
          .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD");
      assertThat(config.getEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Should treat blank externalId as new user")
    void shouldTreatBlankExternalIdAsNewUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("   ");
      UserInputDTO input = new UserInputDTO();
      input.setEmail(user.getEmail());

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getRequiredActions())
          .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
      assertThat(config.getEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Should not set required actions for existing user when email is unchanged")
    void shouldNotSetRequiredActionsForExistingUserWithSameEmail() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("keycloak-uuid-123");
      user.setEmail("same@example.com");
      UserInputDTO input = new UserInputDTO();
      input.setEmail("same@example.com");

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getRequiredActions()).isEmpty();
      assertThat(config.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName(
        "Should add VERIFY_EMAIL and set emailVerified=false when email changes for existing user")
    void shouldResetEmailVerifiedWhenEmailChanges() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("keycloak-uuid-123");
      user.setEmail("old@example.com");
      UserInputDTO input = new UserInputDTO();
      input.setEmail("new@example.com");

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getRequiredActions()).containsExactly("VERIFY_EMAIL");
      assertThat(config.getEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Should preserve emailVerified=false for new user even when email is unchanged")
    void shouldPreserveEmailVerifiedFalseForNewUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);
      user.setEmail("user@example.com");
      UserInputDTO input = new UserInputDTO();
      input.setEmail("user@example.com");

      UserConfig config = (UserConfig) service.toConfigValuePreSave(user, input);

      assertThat(config.getEmailVerified()).isFalse();
    }
  }

  @Nested
  @DisplayName("toConfigValuePostSave()")
  class ToConfigValuePostSaveTests {

    @Test
    @DisplayName("Should use email as username")
    void shouldSetEmailAsUsername() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setEmail("admin@civitas.de");

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getUsername()).isEqualTo("admin@civitas.de");
      assertThat(config.getEmail()).isEqualTo("admin@civitas.de");
    }

    @Test
    @DisplayName("Should set externalId as Keycloak id when present")
    void shouldSetKeycloakIdFromExternalId() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("keycloak-uuid-123");

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getId()).isEqualTo("keycloak-uuid-123");
    }

    @Test
    @DisplayName("Should not set id when externalId is null")
    void shouldNotSetIdWhenExternalIdNull() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getId()).isNull();
    }

    @Test
    @DisplayName("Should set user properties and enabled=true")
    void shouldSetUserProperties() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setFirstName("Max");
      user.setLastName("Mustermann");
      user.setEmail("max@example.com");

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getFirstName()).isEqualTo("Max");
      assertThat(config.getLastName()).isEqualTo("Mustermann");
      assertThat(config.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should populate groups with Keycloak externalIds (not names)")
    void shouldPopulateGroupExternalIds() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());

      Group group1 = new Group();
      group1.setName("Editors");
      group1.setExternalId("kc-uuid-editors");
      Group group2 = new Group();
      group2.setName("Viewers");
      group2.setExternalId("kc-uuid-viewers");
      user.addGroup(group1);
      user.addGroup(group2);

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getGroups())
          .containsExactlyInAnyOrder("kc-uuid-editors", "kc-uuid-viewers");
    }

    @Test
    @DisplayName("Should skip groups without externalId (not yet synced to Keycloak)")
    void shouldSkipGroupsWithoutExternalId() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());

      Group synced = new Group();
      synced.setName("Synced");
      synced.setExternalId("kc-uuid-synced");
      Group unsynced = new Group();
      unsynced.setName("Unsynced");
      unsynced.setExternalId(null);
      Group blank = new Group();
      blank.setName("Blank");
      blank.setExternalId("   ");
      user.addGroup(synced);
      user.addGroup(unsynced);
      user.addGroup(blank);

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getGroups()).containsExactly("kc-uuid-synced");
    }

    @Test
    @DisplayName("Should set empty groups list when user has no groups")
    void shouldSetEmptyGroupsWhenNoMemberships() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());

      UserConfig config =
          (UserConfig) service.toConfigValuePostSave(user, new UserInputDTO(), null);

      assertThat(config.getGroups()).isEmpty();
    }

    @Test
    @DisplayName("Should carry over requiredActions and emailVerified from preSaveConfigValue")
    void shouldCarryOverPreSaveFields() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);
      user.setEmail("user@example.com");
      UserInputDTO input = new UserInputDTO();
      input.setEmail("user@example.com");

      UserConfig preSave = (UserConfig) service.toConfigValuePreSave(user, input);
      UserConfig config = (UserConfig) service.toConfigValuePostSave(user, input, preSave);

      assertThat(config.getRequiredActions())
          .containsExactlyInAnyOrder("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
      assertThat(config.getEmailVerified()).isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // updateExternalId
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("updateExternalId()")
  class UpdateExternalIdTests {

    @Test
    @DisplayName("Should set externalId when value is valid")
    void shouldSetExternalIdWhenValid() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());

      service.updateExternalId(user, "keycloak-id-456");

      assertThat(user.getExternalId()).isEqualTo("keycloak-id-456");
    }

    @Test
    @DisplayName("Should not set externalId when null")
    void shouldSkipWhenNull() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("original");

      service.updateExternalId(user, null);

      assertThat(user.getExternalId()).isEqualTo("original");
    }

    @Test
    @DisplayName("Should not set externalId when blank")
    void shouldSkipWhenBlank() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("original");

      service.updateExternalId(user, "   ");

      assertThat(user.getExternalId()).isEqualTo("original");
    }
  }

  // ---------------------------------------------------------------------------
  // replaceGroups
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("replaceGroups()")
  class ReplaceGroupsTests {

    @Test
    @DisplayName("Should throw when some groups do not exist")
    void shouldThrowWhenGroupsMissing() {
      UserService service = createService();
      UUID userId = UUID.randomUUID();
      User user = userWithId(userId);
      user.setExternalId("keycloak-uuid-123");
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      List<UUID> groupIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
      when(groupRepository.findAllById(groupIds)).thenReturn(List.of(new Group()));

      assertThatThrownBy(() -> service.replaceGroups(userId, groupIds))
          .isInstanceOf(InvalidInputException.class)
          .hasMessage("One or more groups do not exist.");
    }

    @Test
    @DisplayName("Should reject when user has not been synced to Keycloak yet")
    void shouldRejectWhenExternalIdMissing() {
      UserService service = createService();
      UUID userId = UUID.randomUUID();
      User user = userWithId(userId);
      user.setExternalId(null);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.replaceGroups(userId, List.of(UUID.randomUUID())))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("not been synced to Keycloak");
    }

    @Test
    @DisplayName("Should reject when externalId is blank")
    void shouldRejectWhenExternalIdBlank() {
      UserService service = createService();
      UUID userId = UUID.randomUUID();
      User user = userWithId(userId);
      user.setExternalId("   ");
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.replaceGroups(userId, List.of(UUID.randomUUID())))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("not been synced to Keycloak");
    }
  }

  // ---------------------------------------------------------------------------
  // resolveTopic
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("resolveTopic()")
  class ResolveTopicTests {

    @Test
    @DisplayName("Should resolve create topic")
    void shouldResolveCreateTopic() {
      assertThat(createService().resolveTopic("create")).isEqualTo(Topics.USER_CREATED);
    }

    @Test
    @DisplayName("Should resolve update topic")
    void shouldResolveUpdateTopic() {
      assertThat(createService().resolveTopic("update")).isEqualTo(Topics.USER_UPDATED);
    }

    @Test
    @DisplayName("Should resolve delete topic")
    void shouldResolveDeleteTopic() {
      assertThat(createService().resolveTopic("delete")).isEqualTo(Topics.USER_DELETED);
    }

    @Test
    @DisplayName("Should throw on unknown operation")
    void shouldThrowOnUnknownOperation() {
      assertThatThrownBy(() -> createService().resolveTopic("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown operation");
    }
  }

  // ---------------------------------------------------------------------------
  // getRealm / getTargetComponent / getConfigPath
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Config adapter metadata")
  class ConfigAdapterMetadataTests {

    @Test
    @DisplayName("Should return configured realm")
    void shouldReturnTargetRealm() {
      User user = userWithId(UUID.randomUUID());
      assertThat(createService().getRealm(user)).isEqualTo(TARGET_REALM);
    }

    @Test
    @DisplayName("Should return 'user' as target component")
    void shouldReturnUserComponent() {
      assertThat(createService().getTargetComponent()).isEqualTo("user");
    }

    @Test
    @DisplayName("Should return '/users' as config path")
    void shouldReturnUsersPath() {
      assertThat(createService().getConfigPath()).isEqualTo("/users");
    }
  }
}
