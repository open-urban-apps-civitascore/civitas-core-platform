package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.idm.UserConfig;
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

  private UserService createService() {
    return new UserService(
        configEventPublisher, userRepository, userMapper, groupRepository, TARGET_REALM);
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
  // validateGroupIdsExist (called via preProcessCreateInput / preProcessUpdateInput)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("validateGroupIdsExist()")
  class ValidateGroupIdsExistTests {

    @Test
    @DisplayName("Should skip validation when groupIds is null")
    void shouldPassWhenGroupIdsNull() {
      UserService service = createService();
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(null);

      assertThatNoException().isThrownBy(() -> service.preProcessCreateInput(input));
      verify(groupRepository, never()).countByIdIn(anyList());
    }

    @Test
    @DisplayName("Should skip validation when groupIds is empty")
    void shouldPassWhenGroupIdsEmpty() {
      UserService service = createService();
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(List.of());

      assertThatNoException().isThrownBy(() -> service.preProcessCreateInput(input));
      verify(groupRepository, never()).countByIdIn(anyList());
    }

    @Test
    @DisplayName("Should pass when all groups exist")
    void shouldPassWhenAllGroupsExist() {
      UserService service = createService();
      List<UUID> groupIds = List.of(UUID.randomUUID(), UUID.randomUUID());
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(groupIds);
      when(groupRepository.countByIdIn(groupIds)).thenReturn(2L);

      assertThatNoException().isThrownBy(() -> service.preProcessCreateInput(input));
    }

    @Test
    @DisplayName("Should throw when some groups are missing")
    void shouldThrowWhenGroupsMissing() {
      UserService service = createService();
      List<UUID> groupIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(groupIds);
      when(groupRepository.countByIdIn(groupIds)).thenReturn(1L);

      assertThatThrownBy(() -> service.preProcessCreateInput(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("groups do not exist");
    }

    @Test
    @DisplayName("Should also validate on update")
    void shouldValidateOnUpdate() {
      UserService service = createService();
      List<UUID> groupIds = List.of(UUID.randomUUID());
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(groupIds);
      User existingUser = userWithId(UUID.randomUUID());
      when(groupRepository.countByIdIn(groupIds)).thenReturn(1L);

      assertThatNoException().isThrownBy(() -> service.preProcessUpdateInput(input, existingUser));
    }
  }

  // ---------------------------------------------------------------------------
  // buildUserConfig (called via toConfigValue)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("buildUserConfig()")
  class BuildUserConfigTests {

    @Test
    @DisplayName("Should use email as username")
    void shouldSetEmailAsUsername() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setEmail("admin@civitas.de");

      ConfigValue result = service.toConfigValue(user);

      assertThat(result).isInstanceOf(UserConfig.class);
      UserConfig config = (UserConfig) result;
      assertThat(config.getUsername()).isEqualTo("admin@civitas.de");
      assertThat(config.getEmail()).isEqualTo("admin@civitas.de");
    }

    @Test
    @DisplayName("Should set required actions for new user without externalId")
    void shouldSetRequiredActionsForNewUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId(null);

      UserConfig config = (UserConfig) service.toConfigValue(user);

      assertThat(config.getRequiredActions()).containsExactly("VERIFY_EMAIL", "UPDATE_PASSWORD");
      assertThat(config.getId()).isNull();
    }

    @Test
    @DisplayName("Should not set required actions for existing user with externalId")
    void shouldNotSetRequiredActionsForExistingUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("keycloak-uuid-123");

      UserConfig config = (UserConfig) service.toConfigValue(user);

      assertThat(config.getRequiredActions()).isNull();
      assertThat(config.getId()).isEqualTo("keycloak-uuid-123");
    }

    @Test
    @DisplayName("Should treat blank externalId as new user")
    void shouldTreatBlankExternalIdAsNewUser() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setExternalId("   ");

      UserConfig config = (UserConfig) service.toConfigValue(user);

      assertThat(config.getRequiredActions()).containsExactly("VERIFY_EMAIL", "UPDATE_PASSWORD");
      assertThat(config.getId()).isNull();
    }

    @Test
    @DisplayName("Should set user properties correctly")
    void shouldSetUserProperties() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      user.setFirstName("Max");
      user.setLastName("Mustermann");
      user.setEmail("max@example.com");

      UserConfig config = (UserConfig) service.toConfigValue(user);

      assertThat(config.getFirstName()).isEqualTo("Max");
      assertThat(config.getLastName()).isEqualTo("Mustermann");
      assertThat(config.getEnabled()).isTrue();
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
  // prePublish (group membership handling)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("prePublish()")
  class PrePublishTests {

    @Test
    @DisplayName("Should load and set groups when groupIds provided")
    void shouldSetGroupsWhenGroupIdsProvided() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      List<UUID> groupIds = List.of(UUID.randomUUID());
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(groupIds);

      Group group = new Group();
      group.setId(groupIds.get(0));
      when(groupRepository.findAllByIdWithMembers(groupIds)).thenReturn(List.of(group));

      service.prePublish(user, input);

      verify(groupRepository).findAllByIdWithMembers(groupIds);
      assertThat(user.getGroups()).hasSize(1);
    }

    @Test
    @DisplayName("Should skip group loading when groupIds is null")
    void shouldSkipWhenGroupIdsNull() {
      UserService service = createService();
      User user = userWithId(UUID.randomUUID());
      UserInputDTO input = new UserInputDTO();
      input.setGroupIds(null);

      service.prePublish(user, input);

      verify(groupRepository, never()).findAllByIdWithMembers(anyList());
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
