package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxConfigTest {

  private OutboxConfig sut;
  private Validator validator;

  @BeforeEach
  void setUp() {
    sut = new OutboxConfig();
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("should use default values when not configured")
  void shouldUseDefaultValues() {
    // Given
    ReflectionTestUtils.setField(sut, "maxRetries", OutboxConfig.DEFAULT_MAX_RETRIES);
    ReflectionTestUtils.setField(sut, "batchSize", OutboxConfig.DEFAULT_BATCH_SIZE);
    ReflectionTestUtils.setField(sut, "pollDelay", OutboxConfig.DEFAULT_POLL_DELAY_MS);
    ReflectionTestUtils.setField(
        sut, "payloadSizeWarningBytes", OutboxConfig.DEFAULT_PAYLOAD_SIZE_WARNING_BYTES);

    // Then
    assertThat(sut.getMaxRetries()).isEqualTo(5);
    assertThat(sut.getBatchSize()).isEqualTo(100);
    assertThat(sut.getPollDelay()).isEqualTo(500L);
    assertThat(sut.getPayloadSizeWarningBytes()).isEqualTo(100_000L);
  }

  @Test
  @DisplayName("should accept valid max retries value")
  void shouldAcceptValidMaxRetries() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "maxRetries", 10);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
    assertThat(sut.getMaxRetries()).isEqualTo(10);
  }

  @Test
  @DisplayName("should reject max retries below minimum")
  void shouldRejectMaxRetriesBelowMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "maxRetries", 0);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("max-retries must be at least 1"));
  }

  @Test
  @DisplayName("should reject max retries above maximum")
  void shouldRejectMaxRetriesAboveMaximum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "maxRetries", 25);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("max-retries should not exceed 20"));
  }

  @Test
  @DisplayName("should accept max retries at boundary values")
  void shouldAcceptMaxRetriesAtBoundaries() {
    setValidDefaults();
    // Min boundary
    ReflectionTestUtils.setField(sut, "maxRetries", 1);
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);
    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("maxRetries"));

    // Max boundary
    ReflectionTestUtils.setField(sut, "maxRetries", 20);
    violations = validator.validate(sut);
    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("maxRetries"));
  }

  @Test
  @DisplayName("should accept valid batch size value")
  void shouldAcceptValidBatchSize() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "batchSize", 200);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
    assertThat(sut.getBatchSize()).isEqualTo(200);
  }

  @Test
  @DisplayName("should reject batch size below minimum")
  void shouldRejectBatchSizeBelowMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "batchSize", 0);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("batch-size must be at least 1"));
  }

  @Test
  @DisplayName("should reject batch size above maximum")
  void shouldRejectBatchSizeAboveMaximum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "batchSize", 1500);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("batch-size should not exceed 1000"));
  }

  @Test
  @DisplayName("should accept batch size at boundary values")
  void shouldAcceptBatchSizeAtBoundaries() {
    setValidDefaults();
    // Min boundary
    ReflectionTestUtils.setField(sut, "batchSize", 1);
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);
    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("batchSize"));

    // Max boundary
    ReflectionTestUtils.setField(sut, "batchSize", 1000);
    violations = validator.validate(sut);
    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("batchSize"));
  }

  @Test
  @DisplayName("should accept valid poll delay value")
  void shouldAcceptValidPollDelay() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "pollDelay", 1000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
    assertThat(sut.getPollDelay()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("should reject poll delay below minimum")
  void shouldRejectPollDelayBelowMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "pollDelay", 50L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("poll-delay must be at least 100ms"));
  }

  @Test
  @DisplayName("should accept poll delay at minimum boundary")
  void shouldAcceptPollDelayAtMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "pollDelay", 100L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("pollDelay"));
  }

  @Test
  @DisplayName("should accept valid payload size warning threshold")
  void shouldAcceptValidPayloadSizeWarning() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 200_000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
    assertThat(sut.getPayloadSizeWarningBytes()).isEqualTo(200_000L);
  }

  @Test
  @DisplayName("should reject payload size warning below minimum")
  void shouldRejectPayloadSizeWarningBelowMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 500L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(
            v -> v.getMessage().contains("payload-size-warning-bytes must be at least 1000 bytes"));
  }

  @Test
  @DisplayName("should accept payload size warning at minimum boundary")
  void shouldAcceptPayloadSizeWarningAtMinimum() {
    // Given
    setValidDefaults();
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 1000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations)
        .noneMatch(v -> v.getPropertyPath().toString().equals("payloadSizeWarningBytes"));
  }

  @Test
  @DisplayName("should allow production-ready configuration")
  void shouldAllowProductionConfiguration() {
    // Given - Production values
    ReflectionTestUtils.setField(sut, "maxRetries", 5);
    ReflectionTestUtils.setField(sut, "batchSize", 100);
    ReflectionTestUtils.setField(sut, "pollDelay", 500L);
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 100_000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should allow high-throughput configuration")
  void shouldAllowHighThroughputConfiguration() {
    // Given - High throughput values
    ReflectionTestUtils.setField(sut, "maxRetries", 3);
    ReflectionTestUtils.setField(sut, "batchSize", 500);
    ReflectionTestUtils.setField(sut, "pollDelay", 100L);
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 50_000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should allow conservative configuration")
  void shouldAllowConservativeConfiguration() {
    // Given - Conservative values
    ReflectionTestUtils.setField(sut, "maxRetries", 10);
    ReflectionTestUtils.setField(sut, "batchSize", 50);
    ReflectionTestUtils.setField(sut, "pollDelay", 2000L);
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 200_000L);

    // When
    Set<ConstraintViolation<OutboxConfig>> violations = validator.validate(sut);

    // Then
    assertThat(violations).isEmpty();
  }

  /**
   * Helper method to set all fields to valid values. This ensures that validation tests only fail
   * on the specific field being tested.
   */
  private void setValidDefaults() {
    ReflectionTestUtils.setField(sut, "maxRetries", 5);
    ReflectionTestUtils.setField(sut, "batchSize", 100);
    ReflectionTestUtils.setField(sut, "pollDelay", 500L);
    ReflectionTestUtils.setField(sut, "payloadSizeWarningBytes", 100_000L);
  }
}
