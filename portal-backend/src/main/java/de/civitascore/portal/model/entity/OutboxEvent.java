package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "event_outbox",
    indexes = {@Index(name = "idx_outbox_status_created", columnList = "status, created_at")})
@Getter
@Setter
public class OutboxEvent extends BaseEntity {

  @NotBlank @Column(nullable = false, length = 255)
  private String topic;

  @NotBlank @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @NotBlank @Column(nullable = false, length = 100)
  private String aggregateType;

  @NotNull @Column(nullable = false)
  private UUID aggregateId;

  @Column(nullable = false)
  private int retryCount = 0;

  @NotNull @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  private Instant processedAt;

  private Instant lastRetryAt;

  public void markProcessed() {
    this.status = OutboxStatus.PROCESSED;
    this.processedAt = Instant.now();
  }

  public void markRetry() {
    this.retryCount++;
    this.lastRetryAt = Instant.now();
  }

  public boolean hasExceededMaxRetries(int maxRetries) {
    return this.retryCount >= maxRetries;
  }
}
