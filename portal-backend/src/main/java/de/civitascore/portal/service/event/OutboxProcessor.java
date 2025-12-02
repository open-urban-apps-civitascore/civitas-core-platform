package de.civitascore.portal.service.event;

import de.civitascore.portal.configuration.OutboxConfig;
import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.OutboxEvent;
import de.civitascore.portal.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {
  private final OutboxEventRepository repository;
  private final OutboxPublisher publisher;
  private final OutboxConfig config;

  /**
   * Periodically processes pending events from the outbox table.
   *
   * <p>Runs every {@code outbox.poll-delay} milliseconds (default: 500ms). Uses pessimistic locking
   * to prevent duplicate processing in multi-instance deployments.
   *
   * <p>Note: Not read-only because pessimistic locking requires write capability.
   */
  @Scheduled(fixedDelayString = "${outbox.poll-delay:500}")
  @Transactional
  public void processPendingEvents() {
    List<OutboxEvent> pending =
        repository.findByStatusWithLock(
            OutboxStatus.PENDING, PageRequest.of(0, config.getBatchSize()));

    if (pending.isEmpty()) return;

    log.debug("Processing {} pending events", pending.size());

    // Process each event in its own transaction
    for (OutboxEvent event : pending) {
      processEventInNewTransaction(event);
    }
  }

  /**
   * Processes a single event in a new transaction.
   *
   * <p>Each event gets its own transaction to ensure that failures don't rollback successful
   * events.
   *
   * @param event the event to process
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processEventInNewTransaction(OutboxEvent event) {
    try {
      publisher.publish(event);
      event.markProcessed();
      log.debug("Successfully published event {}", event.getId());
    } catch (Exception ex) {
      log.warn(
          "Failed to publish event {} (attempt {}/{}): {}",
          event.getId(),
          event.getRetryCount() + 1,
          config.getMaxRetries(),
          ex.getMessage());

      event.markRetry();

      if (event.hasExceededMaxRetries(config.getMaxRetries())) {
        event.setStatus(OutboxStatus.FAILED);
        publisher.publishError(event, ex);
        log.error(
            "Event {} marked as FAILED after {} retries", event.getId(), event.getRetryCount());
      }
    } finally {
      repository.save(event);
    }
  }
}
