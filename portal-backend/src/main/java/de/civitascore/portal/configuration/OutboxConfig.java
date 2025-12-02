package de.civitascore.portal.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Transactional Outbox Pattern.
 *
 * <p>Centralizes configuration for event processing, retry logic, and batch sizes.
 */
@Configuration
@Getter
@Validated
public class OutboxConfig {

  /** Default maximum number of retry attempts. */
  public static final int DEFAULT_MAX_RETRIES = 5;

  /** Default batch size for event processing. */
  public static final int DEFAULT_BATCH_SIZE = 100;

  /** Default polling delay in milliseconds. */
  public static final long DEFAULT_POLL_DELAY_MS = 500L;

  /** Default payload size warning threshold in bytes. */
  public static final long DEFAULT_PAYLOAD_SIZE_WARNING_BYTES = 100_000L;

  /**
   * Maximum number of retry attempts before marking an event as FAILED.
   *
   * <p>Default: 5 attempts
   */
  @Value("${outbox.max-retries:" + DEFAULT_MAX_RETRIES + "}")
  @Min(value = 1, message = "max-retries must be at least 1") @Max(value = 20, message = "max-retries should not exceed 20") private int maxRetries;

  /**
   * Maximum number of events to process in a single batch.
   *
   * <p>Prevents OutOfMemory errors when processing large backlogs. Default: 100 events
   */
  @Value("${outbox.batch-size:" + DEFAULT_BATCH_SIZE + "}")
  @Min(value = 1, message = "batch-size must be at least 1") @Max(value = 1000, message = "batch-size should not exceed 1000") private int batchSize;

  /**
   * Delay between polling cycles in milliseconds.
   *
   * <p>Default: 500ms (2 polls per second)
   */
  @Value("${outbox.poll-delay:" + DEFAULT_POLL_DELAY_MS + "}")
  @Min(value = 100, message = "poll-delay must be at least 100ms") private long pollDelay;

  /**
   * Payload size threshold for logging warnings (in bytes).
   *
   * <p>Default: 100,000 bytes (100KB)
   */
  @Value("${outbox.payload-size-warning-bytes:" + DEFAULT_PAYLOAD_SIZE_WARNING_BYTES + "}")
  @Min(value = 1000, message = "payload-size-warning-bytes must be at least 1000 bytes") private long payloadSizeWarningBytes;
}
