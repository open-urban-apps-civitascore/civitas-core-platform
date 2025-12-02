package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.event.DomainEvent;
import de.civitascore.portal.model.output.event.TopicResolver;
import de.civitascore.portal.model.output.event.UserEventDTO;
import de.civitascore.portal.service.event.EventPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class EventPublishingServiceTest {

  @Mock protected EventPublisherService eventPublisherServiceMock;

  @Mock protected TopicResolver topicResolverMock;

  @Mock private ServletRequestAttributes requestAttributesMock;

  @Mock private HttpServletRequest httpServletRequestMock;

  @Mock private SecurityContext securityContextMock;

  @Mock private Authentication authenticationMock;

  private MockedStatic<RequestContextHolder> requestContextHolderMock;
  private MockedStatic<SecurityContextHolder> securityContextHolderMock;

  private TestEventPublishingService sut;

  @BeforeEach
  void setUp() {
    requestContextHolderMock = mockStatic(RequestContextHolder.class);
    securityContextHolderMock = mockStatic(SecurityContextHolder.class);

    sut = new TestEventPublishingService(eventPublisherServiceMock, topicResolverMock);
  }

  @AfterEach
  void tearDown() {
    requestContextHolderMock.close();
    securityContextHolderMock.close();
  }

  @Test
  @DisplayName("postSave should publish create event when HTTP method is POST")
  @SuppressWarnings("unchecked")
  void postSaveShouldPublishCreateEvent() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("POST", "testuser");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(eq("User.create"), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.eventType()).isEqualTo("User.create");
    assertThat(capturedEvent.aggregateType()).isEqualTo("User");
    assertThat(capturedEvent.operation()).isEqualTo("create");
    assertThat(capturedEvent.entityId()).isEqualTo(testUser.getId());
  }

  @Test
  @DisplayName("postSave should publish update event when HTTP method is PUT")
  void postSaveShouldPublishUpdateEvent() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("PUT", "testuser");
    when(topicResolverMock.resolve("User", "update")).thenReturn("User.update");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(eq("User.update"), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.operation()).isEqualTo("update");
  }

  @Test
  @DisplayName("postSave should publish update event when HTTP method is PATCH")
  void postSaveShouldPublishUpdateEventOnPatch() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("PATCH", "testuser");
    when(topicResolverMock.resolve("User", "update")).thenReturn("User.update");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    verify(eventPublisherServiceMock).publish(eq("User.update"), any(DomainEvent.class));
  }

  @Test
  @DisplayName("postDelete should publish delete event")
  void postDeleteShouldPublishDeleteEvent() {
    // Given
    User testUser = createTestUser();
    setupHttpContext("DELETE", "testuser");
    when(topicResolverMock.resolve("User", "delete")).thenReturn("User.delete");

    // When
    sut.postDelete(testUser);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(eq("User.delete"), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.operation()).isEqualTo("delete");
  }

  @Test
  @DisplayName("toKafkaRepresentation should return custom DTO when overridden")
  void toKafkaRepresentationShouldReturnCustomDto() {
    // Given
    User testUser = createTestUser();

    // When
    Object representation = sut.toKafkaRepresentation(testUser);

    // Then
    assertThat(representation).isInstanceOf(UserEventDTO.class);
    UserEventDTO dto = (UserEventDTO) representation;
    assertThat(dto.id()).isEqualTo(testUser.getId());
    assertThat(dto.email()).isEqualTo(testUser.getEmail());
    assertThat(dto.firstName()).isEqualTo(testUser.getFirstName());
    assertThat(dto.lastName()).isEqualTo(testUser.getLastName());
  }

  @Test
  @DisplayName("publishEvent should use toKafkaRepresentation for payload")
  void publishEventShouldUseKafkaRepresentation() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("POST", "testuser");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(anyString(), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.payload()).isInstanceOf(UserEventDTO.class);
  }

  @Test
  @DisplayName("getCorrelationIds should include userId from authentication")
  void getCorrelationIdsShouldIncludeUserId() {
    // Given
    setupHttpContext("POST", "admin-user");

    // When
    Map<String, String> correlationIds = sut.getCorrelationIds();

    // Then
    assertThat(correlationIds).containsEntry("userId", "admin-user");
  }

  @Test
  @DisplayName("getCorrelationIds should include traceId from request header")
  void getCorrelationIdsShouldIncludeTraceId() {
    // Given
    setupHttpContext("POST", "testuser");
    when(httpServletRequestMock.getHeader("X-B3-TraceId")).thenReturn("trace-123-456");

    // When
    Map<String, String> correlationIds = sut.getCorrelationIds();

    // Then
    assertThat(correlationIds).containsEntry("traceId", "trace-123-456");
    assertThat(correlationIds).containsEntry("userId", "testuser");
  }

  @Test
  @DisplayName("getCorrelationIds should return empty map when no HTTP context")
  void getCorrelationIdsShouldReturnEmptyMapWithoutHttpContext() {
    // Given
    requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
    securityContextHolderMock
        .when(SecurityContextHolder::getContext)
        .thenReturn(securityContextMock);
    when(securityContextMock.getAuthentication()).thenReturn(null);

    // When
    Map<String, String> correlationIds = sut.getCorrelationIds();

    // Then
    assertThat(correlationIds).isEmpty();
  }

  @Test
  @DisplayName("getCorrelationIds should not include userId for anonymous user")
  void getCorrelationIdsShouldNotIncludeAnonymousUser() {
    // Given
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(requestAttributesMock);
    when(requestAttributesMock.getRequest()).thenReturn(httpServletRequestMock);
    when(httpServletRequestMock.getHeader("X-B3-TraceId")).thenReturn(null);
    securityContextHolderMock
        .when(SecurityContextHolder::getContext)
        .thenReturn(securityContextMock);
    when(securityContextMock.getAuthentication()).thenReturn(authenticationMock);
    when(authenticationMock.isAuthenticated()).thenReturn(true);
    when(authenticationMock.getPrincipal()).thenReturn("anonymousUser");

    // When
    Map<String, String> correlationIds = sut.getCorrelationIds();

    // Then
    assertThat(correlationIds).doesNotContainKey("userId");
  }

  @Test
  @DisplayName("postSave should fallback to update operation without HTTP context")
  void postSaveShouldFallbackToUpdateWithoutHttpContext() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
    securityContextHolderMock
        .when(SecurityContextHolder::getContext)
        .thenReturn(securityContextMock);
    when(securityContextMock.getAuthentication()).thenReturn(null);
    when(topicResolverMock.resolve("User", "update")).thenReturn("User.update");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    verify(eventPublisherServiceMock).publish(eq("User.update"), any(DomainEvent.class));
  }

  @Test
  @DisplayName("publishEvent should include event metadata with realm")
  void publishEventShouldIncludeMetadataWithRealm() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("POST", "testuser");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(anyString(), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.metadata()).isNotNull();
    assertThat(capturedEvent.metadata().realm()).isEmpty(); // TestEventPublishingService returns ""
  }

  @Test
  @DisplayName("publishEvent should have schema version 1")
  void publishEventShouldHaveSchemaVersion() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("POST", "testuser");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(anyString(), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.schemaVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("publishEvent should have timestamp")
  void publishEventShouldHaveTimestamp() {
    // Given
    User testUser = createTestUser();
    UserInputDTO inputDTO = new UserInputDTO();
    setupHttpContext("POST", "testuser");
    when(topicResolverMock.resolve("User", "create")).thenReturn("User.create");

    // When
    sut.postSave(testUser, inputDTO);

    // Then
    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisherServiceMock).publish(anyString(), eventCaptor.capture());

    DomainEvent<?> capturedEvent = eventCaptor.getValue();
    assertThat(capturedEvent.timestamp()).isNotNull();
  }

  private void setupHttpContext(String httpMethod, String username) {
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(requestAttributesMock);
    when(requestAttributesMock.getRequest()).thenReturn(httpServletRequestMock);
    lenient().when(httpServletRequestMock.getMethod()).thenReturn(httpMethod);
    lenient().when(httpServletRequestMock.getHeader(anyString())).thenReturn(null);

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
    user.setExternalId("ext-123");
    return user;
  }

  // Test implementation of EventPublishingService
  private static class TestEventPublishingService
      extends EventPublishingService<User, UserInputDTO> {

    protected TestEventPublishingService(
        EventPublisherService events, TopicResolver topicResolver) {
      super(events, topicResolver);
    }

    @Override
    protected de.civitascore.portal.repository.BaseRepository<User, UUID> getRepository() {
      return null; // Not needed for these tests
    }

    @Override
    protected de.civitascore.portal.mapper.DtoMapper<UserInputDTO, ?, User> getMapper() {
      return null; // Not needed for these tests
    }

    @Override
    protected String getEntityName() {
      return "User";
    }

    @Override
    protected String getAggregateType() {
      return "User";
    }

    @Override
    protected String getRealm(User entity) {
      return "";
    }

    @Override
    protected UUID getEntityId(User entity) {
      return entity.getId();
    }

    @Override
    protected UserEventDTO toKafkaRepresentation(User entity) {
      return new UserEventDTO(
          entity.getId(),
          entity.getFirstName(),
          entity.getLastName(),
          entity.getEmail(),
          entity.getActive(),
          entity.getExternalId());
    }
  }
}
