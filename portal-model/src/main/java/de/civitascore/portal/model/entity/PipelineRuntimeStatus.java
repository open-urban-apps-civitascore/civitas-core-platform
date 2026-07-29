package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The most recent runtime health of a single {@link Pipeline}. One row per pipeline (shared primary
 * key via {@link MapsId}); only the current status is kept, not a history.
 */
@Entity
@Table(name = "pipeline_runtime_status")
@Getter
@Setter
@NoArgsConstructor
public class PipelineRuntimeStatus {
  // Shared primary key: this row's id IS the owning pipeline's id (@MapsId derives it from the
  // pipeline association). Leave it unset on a new instance so Hibernate resolves it from the
  // pipeline during persist — pre-setting it makes Spring Data choose merge() over persist().
  @Id private UUID id;

  @OneToOne
  @MapsId
  @JoinColumn(name = "pipeline_id")
  private Pipeline pipeline;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PipelineRuntimeState state;

  @Enumerated(EnumType.STRING)
  private PipelineRuntimeSource source;

  @Column(length = 2000)
  private String message;

  @Column(columnDefinition = "text")
  private String sanitizedStacktrace;

  private Instant occurredAt;
  private UUID correlationId;
  private UUID lastEventId;
}
