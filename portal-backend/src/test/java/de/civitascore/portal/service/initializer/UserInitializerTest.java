package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Initializer Tests")
class UserInitializerTest {

  private static final String EMAIL = "dev@civitas.local";
  private static final String TARGET_REALM = "civitas-core";
  private static final String KEYCLOAK_SUB = "cc1207f4-969f-456c-85df-b373dabfe235";

  @Mock private InitProperties properties;
  @Mock private UserRepository userRepository;
  @Mock private GroupRepository groupRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private ConfigEventPublisherService configEventPublisher;
  @Mock private Environment environment;

  private UserInitializer initializer;

  @BeforeEach
  void setUp() {
    initializer =
        new UserInitializer(
            properties,
            userRepository,
            groupRepository,
            roleRepository,
            assignmentRepository,
            configEventPublisher,
            environment,
            new KeycloakProperties(TARGET_REALM, "http://keycloak:8080", "civitas-core"),
            new EventProperties(30));
    when(properties.getGroups()).thenReturn(List.of());
    when(properties.getUsers()).thenReturn(List.of(userEntry()));
  }

  @Test
  @DisplayName(
      "Reconciles an existing user whose Keycloak sync never completed by backfilling externalId")
  void backfillsExternalIdForUnlinkedExistingUser() {
    User unlinked = existingUser(null);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unlinked));
    when(configEventPublisher.publishUserCreated(eq(TARGET_REALM), any()))
        .thenReturn(CompletableFuture.completedFuture(successResult(KEYCLOAK_SUB)));

    initializer.initialize();

    verify(configEventPublisher).publishUserCreated(eq(TARGET_REALM), any(UserConfig.class));
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getExternalId()).isEqualTo(KEYCLOAK_SUB);
  }

  @Test
  @DisplayName("Skips an existing user that is already linked to Keycloak — no re-sync, no save")
  void skipsAlreadyLinkedExistingUser() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser(KEYCLOAK_SUB)));

    initializer.initialize();

    verify(configEventPublisher, never()).publishUserCreated(anyString(), any());
    verify(userRepository, never()).save(any());
  }

  private InitProperties.UserEntry userEntry() {
    InitProperties.UserEntry entry = new InitProperties.UserEntry();
    entry.setFirstName("Developer");
    entry.setLastName("User");
    entry.setEmail(EMAIL);
    entry.setTitle(UserTitleType.OTHER);
    entry.setPassword("dev123");
    entry.setGroups(List.of());
    return entry;
  }

  private User existingUser(String externalId) {
    User user = new User();
    user.setEmail(EMAIL);
    user.setFirstName("Developer");
    user.setLastName("User");
    user.setExternalId(externalId);
    return user;
  }

  private ConfigResultEvent successResult(String resourceId) {
    return ConfigResultEvent.success(
        "corr",
        "msg",
        "created",
        resourceId,
        Operation.CREATE,
        "/users",
        "de.civitascore.config-adapter.keycloak",
        "de.civitascore.idm.processing.result");
  }
}
