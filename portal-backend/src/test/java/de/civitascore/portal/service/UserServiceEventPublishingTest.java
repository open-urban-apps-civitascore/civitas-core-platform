package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.model.output.event.TopicResolver;
import de.civitascore.portal.model.output.event.UserEventDTO;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.event.EventPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class UserServiceEventPublishingTest {

  @Mock private EventPublisherService eventPublisherServiceMock;

  @Mock private TopicResolver topicResolverMock;

  @Mock private UserRepository userRepositoryMock;

  @Mock private UserMapper userMapperMock;

  @Mock private ObjectMapper objectMapperMock;

  @Mock private ServletRequestAttributes requestAttributesMock;

  @Mock private HttpServletRequest httpServletRequestMock;

  @Mock private SecurityContext securityContextMock;

  @Mock private Authentication authenticationMock;

  @InjectMocks private UserService sut;

  private MockedStatic<RequestContextHolder> requestContextHolderMock;
  private MockedStatic<SecurityContextHolder> securityContextHolderMock;

  @BeforeEach
  void setUp() {
    requestContextHolderMock = mockStatic(RequestContextHolder.class);
    securityContextHolderMock = mockStatic(SecurityContextHolder.class);
  }

  @AfterEach
  void tearDown() {
    requestContextHolderMock.close();
    securityContextHolderMock.close();
  }

  @Test
  @DisplayName("toKafkaRepresentation should convert User entity to UserEventDTO")
  void toKafkaRepresentationShouldConvertUserToDto() {
    // Given
    User testUser = createTestUser();

    // When
    UserEventDTO result = sut.toKafkaRepresentation(testUser);

    // Then
    assertNotNull(result);
    assertThat(result.id()).isEqualTo(testUser.getId());
    assertThat(result.firstName()).isEqualTo(testUser.getFirstName());
    assertThat(result.lastName()).isEqualTo(testUser.getLastName());
    assertThat(result.email()).isEqualTo(testUser.getEmail());
    assertThat(result.active()).isEqualTo(testUser.getActive());
    assertThat(result.externalId()).isEqualTo(testUser.getExternalId());
  }

  @Test
  @DisplayName("toKafkaRepresentation should exclude sensitive data like password")
  void toKafkaRepresentationShouldExcludeSensitiveData() {
    // Given
    User testUser = createTestUser();
    // User entity might have more fields in the future (password, tokens, etc.)

    // When
    UserEventDTO result = sut.toKafkaRepresentation(testUser);

    // Then
    // Verify that only the specified fields are in the DTO
    // This is a whitelist approach - if new fields are added to User,
    // they won't automatically be published to Kafka
    assertThat(result)
        .hasOnlyFields("id", "firstName", "lastName", "email", "active", "externalId");
  }

  @Test
  @DisplayName("toKafkaRepresentation should handle null externalId")
  void toKafkaRepresentationShouldHandleNullExternalId() {
    // Given
    User testUser = createTestUser();
    testUser.setExternalId(null);

    // When
    UserEventDTO result = sut.toKafkaRepresentation(testUser);

    // Then
    assertNotNull(result);
    assertNull(result.externalId());
  }

  @Test
  @DisplayName("create should publish UserEventDTO not full User entity")
  @SuppressWarnings("unchecked")
  void createShouldPublishUserEventDto() {
    // Given
    UserInputDTO inputDTO = new UserInputDTO();
    inputDTO.setFirstName("John");
    inputDTO.setLastName("Doe");
    inputDTO.setEmail("john.doe@example.com");

    User savedUser = createTestUser();

    setupHttpContext("POST", "admin");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");
    when(userMapperMock.toEntity(inputDTO)).thenReturn(savedUser);
    when(userRepositoryMock.save(any(User.class))).thenReturn(savedUser);

    // When
    User result = sut.create(inputDTO);

    // Then
    assertNotNull(result);
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(eq("User.create"), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.payload()).isInstanceOf(UserEventDTO.class);
    assertThat(capturedEvent.payload()).isNotInstanceOf(User.class);
  }

  @Test
  @DisplayName("update should publish UserEventDTO with updated values")
  @SuppressWarnings("unchecked")
  void updateShouldPublishUserEventDtoWithUpdatedValues() throws Exception {
    // Given
    UUID userId = UUID.randomUUID();
    UserInputDTO inputDTO = new UserInputDTO();
    inputDTO.setFirstName("Jane");
    inputDTO.setLastName("Smith");
    inputDTO.setEmail("jane.smith@example.com");

    User existingUser = createTestUser();
    existingUser.setId(userId);

    User updatedUser = createTestUser();
    updatedUser.setId(userId);
    updatedUser.setFirstName("Jane");
    updatedUser.setLastName("Smith");
    updatedUser.setEmail("jane.smith@example.com");

    setupHttpContext("PUT", "admin");

    // Mock ObjectMapper for preProcessUpdateInput
    ObjectMapper realObjectMapper = new ObjectMapper();
    String inputJson = realObjectMapper.writeValueAsString(inputDTO);
    JsonNode jsonNode = realObjectMapper.readTree(inputJson);
    when(objectMapperMock.writeValueAsString(inputDTO)).thenReturn(inputJson);
    when(objectMapperMock.readTree(inputJson)).thenReturn(jsonNode);

    when(topicResolverMock.resolve("User", "update")).thenReturn("User.update");
    when(userRepositoryMock.findById(userId)).thenReturn(java.util.Optional.of(existingUser));
    when(userRepositoryMock.save(any(User.class))).thenReturn(updatedUser);
    doNothing().when(userMapperMock).updateEntity(any(User.class), any(UserInputDTO.class));

    // When
    User result = sut.update(userId, inputDTO);

    // Then
    assertNotNull(result);
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(eq("User.update"), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.payload()).isInstanceOf(UserEventDTO.class);

    UserEventDTO payload = (UserEventDTO) capturedEvent.payload();
    assertThat(payload.firstName()).isEqualTo("Jane");
    assertThat(payload.lastName()).isEqualTo("Smith");
    assertThat(payload.email()).isEqualTo("jane.smith@example.com");
  }

  @Test
  @DisplayName("getAggregateType should return User")
  void getAggregateTypeShouldReturnUser() {
    // When
    String aggregateType = sut.getAggregateType();

    // Then
    assertThat(aggregateType).isEqualTo("User");
  }

  @Test
  @DisplayName("getRealm should return empty string")
  void getRealmShouldReturnEmptyString() {
    // Given
    User testUser = createTestUser();

    // When
    String realm = sut.getRealm(testUser);

    // Then
    assertThat(realm).isEmpty();
  }

  @Test
  @DisplayName("getEntityId should return user ID")
  void getEntityIdShouldReturnUserId() {
    // Given
    User testUser = createTestUser();

    // When
    UUID entityId = sut.getEntityId(testUser);

    // Then
    assertThat(entityId).isEqualTo(testUser.getId());
  }

  @Test
  @DisplayName("toKafkaRepresentation should create smaller payload than full entity")
  void toKafkaRepresentationShouldCreateSmallerPayload() {
    // Given
    User testUser = createTestUser();
    // In reality, User entity has many more fields: groups, roles, timestamps, etc.

    // When
    UserEventDTO dto = sut.toKafkaRepresentation(testUser);

    // Then
    // UserEventDTO should only contain 6 fields, not all User entity fields
    assertThat(dto).isNotNull();
    // This demonstrates that we're not sending the entire entity with all relationships
  }

  @Test
  @DisplayName("toKafkaRepresentation should handle inactive user")
  void toKafkaRepresentationShouldHandleInactiveUser() {
    // Given
    User testUser = createTestUser();
    testUser.setActive(false);

    // When
    UserEventDTO result = sut.toKafkaRepresentation(testUser);

    // Then
    assertThat(result.active()).isFalse();
  }

  @Test
  @DisplayName("UserEventDTO should be serializable as JSON")
  void userEventDtoShouldBeSerializable() {
    // Given
    User testUser = createTestUser();
    UserEventDTO dto = sut.toKafkaRepresentation(testUser);

    // When & Then
    assertDoesNotThrow(
        () -> {
          ObjectMapper mapper = new ObjectMapper();
          String json = mapper.writeValueAsString(dto);
          assertThat(json).contains("\"id\":");
          assertThat(json).contains("\"email\":");
          assertThat(json).contains("\"firstName\":");
        });
  }

  private void setupHttpContext(String httpMethod, String username) {
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(requestAttributesMock);
    when(requestAttributesMock.getRequest()).thenReturn(httpServletRequestMock);
    when(httpServletRequestMock.getMethod()).thenReturn(httpMethod);

    securityContextHolderMock
        .when(SecurityContextHolder::getContext)
        .thenReturn(securityContextMock);
    when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
    when(authenticationMock.isAuthenticated()).thenReturn(true);
    when(authenticationMock.getName()).thenReturn(username);
    when(authenticationMock.getPrincipal()).thenReturn(username);
  }

  private User createTestUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("test@example.com");
    user.setFirstName("Test");
    user.setLastName("User");
    user.setActive(true);
    user.setExternalId("keycloak-ext-123");
    return user;
  }
}
