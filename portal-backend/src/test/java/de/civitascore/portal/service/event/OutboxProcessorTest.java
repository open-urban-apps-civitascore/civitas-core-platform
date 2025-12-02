package de.civitascore.portal.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.configuration.OutboxConfig;
import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.repository.OutboxEventRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

  @Mock private OutboxEventRepository repositoryMock;

  @Mock private OutboxPublisher publisherMock;

  @Mock private OutboxConfig configMock;

  @InjectMocks private OutboxProcessor sut;

  private OutboxEvent testEvent;

  @BeforeEach
  void setUp() {
    testEvent = new OutboxEvent();
    testEvent.setId(UUID.randomUUID());
    testEvent.setTopic("User.create");
    testEvent.setAggregateId(UUID.randomUUID());
    testEvent.setAggregateType("User");
    testEvent.setPayload("{\"id\":\"123\"}");
    testEvent.setStatus(OutboxStatus.PENDING);
    testEvent.setRetryCount(0);

    // Setup default config values with lenient() to avoid IllegalArgumentException
    // Tests can override these values if needed
    lenient().when(configMock.getBatchSize()).thenReturn(100);
    lenient().when(configMock.getMaxRetries()).thenReturn(5);
  }

  @Test
  @DisplayName("processPendingEvents should skip processing when no pending events exist")
  void processPendingEventsShouldSkipWhenNoPendingEvents() {
    // Given
    when(repositoryMock.findByStatusWithLock(eq(OutboxStatus.PENDING), any(PageRequest.class)))
        .thenReturn(Collections.emptyList());

    // When
    sut.processPendingEvents();

    // Then
    verify(repositoryMock).findByStatusWithLock(eq(OutboxStatus.PENDING), any(PageRequest.class));
    verify(publisherMock, never()).publish(any());
    verify(repositoryMock, never()).save(any());
  }

  @Test
  @DisplayName("processPendingEvents should process pending events successfully")
  void processPendingEventsShouldProcessEvents() {
    // Given
    OutboxEvent event1 = createTestEvent();
    OutboxEvent event2 = createTestEvent();
    when(repositoryMock.findByStatusWithLock(eq(OutboxStatus.PENDING), any(PageRequest.class)))
        .thenReturn(List.of(event1, event2));
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doNothing().when(publisherMock).publish(any());

    // When
    sut.processPendingEvents();

    // Then
    verify(publisherMock, times(2)).publish(any(OutboxEvent.class));
    verify(repositoryMock, times(2)).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("processPendingEvents should respect batch size configuration")
  void processPendingEventsShouldRespectBatchSize() {
    // Given
    when(configMock.getBatchSize()).thenReturn(50);
    when(repositoryMock.findByStatusWithLock(eq(OutboxStatus.PENDING), any(PageRequest.class)))
        .thenReturn(Collections.emptyList());

    // When
    sut.processPendingEvents();

    // Then
    ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(repositoryMock)
        .findByStatusWithLock(eq(OutboxStatus.PENDING), pageRequestCaptor.capture());

    PageRequest capturedPageRequest = pageRequestCaptor.getValue();
    assertThat(capturedPageRequest.getPageSize()).isEqualTo(50);
    assertThat(capturedPageRequest.getPageNumber()).isZero();
  }

  @Test
  @DisplayName("processEventInNewTransaction should mark event as PROCESSED on success")
  void processEventInNewTransactionShouldMarkAsProcessed() {
    // Given
    doNothing().when(publisherMock).publish(testEvent);
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    assertNotNull(savedEvent.getProcessedAt());
  }

  @Test
  @DisplayName("processEventInNewTransaction should increment retry count on failure")
  void processEventInNewTransactionShouldIncrementRetryCount() {
    // Given
    doThrow(new RuntimeException("Kafka unavailable")).when(publisherMock).publish(testEvent);
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getRetryCount()).isEqualTo(1);
    assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
  }

  @Test
  @DisplayName("processEventInNewTransaction should mark as FAILED after exceeding max retries")
  void processEventInNewTransactionShouldMarkAsFailedAfterMaxRetries() {
    // Given
    testEvent.setRetryCount(4); // Already 4 retries
    when(configMock.getMaxRetries()).thenReturn(5);
    doThrow(new RuntimeException("Kafka unavailable")).when(publisherMock).publish(testEvent);
    doNothing().when(publisherMock).publishError(any(), any());
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getRetryCount()).isEqualTo(5);
    assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
    verify(publisherMock).publishError(eq(testEvent), any(RuntimeException.class));
  }

  @Test
  @DisplayName("processEventInNewTransaction should publish error when max retries exceeded")
  void processEventInNewTransactionShouldPublishErrorWhenMaxRetriesExceeded() {
    // Given
    testEvent.setRetryCount(5);
    when(configMock.getMaxRetries()).thenReturn(5);
    RuntimeException testException = new RuntimeException("Test error");
    doThrow(testException).when(publisherMock).publish(testEvent);
    doNothing().when(publisherMock).publishError(any(), any());
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    verify(publisherMock).publishError(testEvent, testException);
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repositoryMock).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  @DisplayName("processEventInNewTransaction should not mark as FAILED before max retries")
  void processEventInNewTransactionShouldNotMarkAsFailedBeforeMaxRetries() {
    // Given
    testEvent.setRetryCount(2); // Only 2 retries
    when(configMock.getMaxRetries()).thenReturn(5);
    doThrow(new RuntimeException("Kafka unavailable")).when(publisherMock).publish(testEvent);
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repositoryMock).save(eventCaptor.capture());

    OutboxEvent savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getRetryCount()).isEqualTo(3);
    assertThat(savedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    verify(publisherMock, never()).publishError(any(), any());
  }

  @Test
  @DisplayName("processEventInNewTransaction should always save event even on exception")
  void processEventInNewTransactionShouldAlwaysSaveEvent() {
    // Given
    doThrow(new RuntimeException("Unexpected error")).when(publisherMock).publish(testEvent);
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    sut.processEventInNewTransaction(testEvent);

    // Then
    verify(repositoryMock).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("processEventInNewTransaction should handle null exception gracefully")
  void processEventInNewTransactionShouldHandleNullException() {
    // Given
    doThrow(new NullPointerException()).when(publisherMock).publish(testEvent);
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    assertDoesNotThrow(() -> sut.processEventInNewTransaction(testEvent));

    // Then
    verify(repositoryMock).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("processPendingEvents should process multiple events independently")
  void processPendingEventsShouldProcessEventsIndependently() {
    // Given
    OutboxEvent successEvent = createTestEvent();
    OutboxEvent failEvent = createTestEvent();

    when(repositoryMock.findByStatusWithLock(eq(OutboxStatus.PENDING), any(PageRequest.class)))
        .thenReturn(List.of(successEvent, failEvent));
    when(repositoryMock.save(any(OutboxEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(publisherMock).publish(successEvent);
    doThrow(new RuntimeException("Kafka error")).when(publisherMock).publish(failEvent);

    // When
    sut.processPendingEvents();

    // Then
    verify(publisherMock, times(2)).publish(any(OutboxEvent.class));
    verify(repositoryMock, times(2)).save(any(OutboxEvent.class));
  }

  private OutboxEvent createTestEvent() {
    OutboxEvent event = new OutboxEvent();
    event.setId(UUID.randomUUID());
    event.setTopic("User.create");
    event.setAggregateId(UUID.randomUUID());
    event.setAggregateType("User");
    event.setPayload("{\"id\":\"123\"}");
    event.setStatus(OutboxStatus.PENDING);
    event.setRetryCount(0);
    return event;
  }
}
