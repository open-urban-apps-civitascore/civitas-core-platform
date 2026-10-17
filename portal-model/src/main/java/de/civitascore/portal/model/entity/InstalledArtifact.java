package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
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
 * One artifact line of an {@link Installation}: what kind of artifact, which shell row and URN it
 * ended up as, and whether this install created it or merely linked an existing one. Shell id and
 * URN are plain values, not foreign keys — see the parent's append-only contract.
 */
@Getter
@Setter
@Entity
@Table(name = "installed_artifacts")
public class InstalledArtifact extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "installation_id", nullable = false)
  private Installation installation;

  /** Stable ordering within the installation; assigned by {@link Installation#addArtifact}. */
  @Column(name = "position", nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(name = "artifact_type", nullable = false)
  private InstalledArtifactType artifactType;

  @Column(name = "name")
  private String name;

  /** Id of the shell row (data structure, data source, …) at install time. */
  @Column(name = "shell_id")
  private UUID shellId;

  /** Logical CORE URN, where the artifact type carries one. */
  @Column(name = "urn", length = 1024)
  private String urn;

  /**
   * The versioned URN of the resolved artifact, where one exists — which concrete version this
   * install created or reused. The {@code urn} column above stays the stable logical identity that
   * reference counting keys on; this column answers the update flow's question. Null for artifact
   * types without a registry identity, and for a dataset, whose manifest the platform does not pin
   * to a version.
   */
  @Column(name = "versioned_urn", length = 1024)
  private String versionedUrn;

  /**
   * The URN this artifact carried in the package it came from. Traceability only — the identity on
   * this instance is {@code urn}, minted here. Origin is what prerequisites ("is standard X
   * installed?"), cross-instance comparison and updates key on.
   */
  @Column(name = "origin", length = 1024)
  private String origin;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false)
  private InstalledArtifactAction action;
}
