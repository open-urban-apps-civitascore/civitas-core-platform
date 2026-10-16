package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.InstalledArtifactType;
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
 * One artifact line of an {@link Installation}: the type of the artifact, and the shell row and the
 * URN that the install created for it. The shell id and the URN are values, not foreign keys (see
 * {@link Installation}).
 */
@Getter
@Setter
@Entity
@Table(name = "installed_artifacts")
public class InstalledArtifact extends BaseEntity {

  private static final int URN_LENGTH = 1024;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "installation_id", nullable = false)
  private Installation installation;

  /** The position of the line in its installation. {@link Installation#addArtifact} sets it. */
  @Column(name = "position", nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(name = "artifact_type", nullable = false)
  private InstalledArtifactType artifactType;

  @Column(name = "name")
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
