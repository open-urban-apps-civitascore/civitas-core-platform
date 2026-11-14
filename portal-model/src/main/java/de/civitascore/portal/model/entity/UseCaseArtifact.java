package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.UseCaseArtifactType;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One artifact line of a {@link UseCase}: the type of the artifact, and the shell row and the URN
 * that the install created for it. The shell id and the URN are values, not foreign keys (see
 * {@link UseCase}).
 */
@Getter
@Setter
@Entity
@Table(name = "use_case_artifacts")
public class UseCaseArtifact extends BaseEntity {

  /** The maximum length of a URN in a line, in characters. */
  public static final int URN_LENGTH = 1024;

  /** The maximum length of the name in a line, in characters. */
  public static final int NAME_LENGTH = 255;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "use_case_id", nullable = false)
  private UseCase useCase;

  /** The position of the line in its use case. {@link UseCase#addArtifact} sets it. */
  @Column(name = "position", nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(name = "artifact_type", nullable = false)
  private UseCaseArtifactType artifactType;

  @Column(name = "name", length = NAME_LENGTH)
  private String name;

  /** The id of the shell row that the install created, for example the row of a Data structure. */
  @Column(name = "shell_id")
  private UUID shellId;

  /** The logical CORE URN of the artifact on this instance. */
  @Column(name = "urn", length = URN_LENGTH)
  private String urn;

  /** The versioned CORE URN: the version of the artifact that the install created. */
  @Column(name = "versioned_urn", length = URN_LENGTH)
  private String versionedUrn;

  /**
   * The identity of the artifact in the package, as the package gives it. It is for traceability
   * only: the identity on this instance is {@link #urn}.
   */
  @Column(name = "origin", length = URN_LENGTH)
  private String origin;
}
