package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * One install as it happened: which package (as declared by the caller) and — via {@link
 * InstalledArtifact} — what it did to every artifact it touched. Written in the same transaction as
 * the install itself, so the record exists exactly iff the install committed. Who and when come
 * from the audit columns.
 *
 * <p>History: referenced artifacts are stored as plain ids and URNs, not foreign keys, so the
 * record survives their deletion. An uninstall removes the artifacts and sets {@link
 * #uninstalledAt}. The record and its lines stay.
 */
@Getter
@Setter
@Entity
@Table(name = "installations")
public class Installation extends BaseEntity {

  /**
   * Identity of the installed package, as declared by the caller — recorded verbatim and never
   * interpreted. Deliberately not called a URN: the value follows whatever scheme the catalogue
   * uses and is not required to be a CORE URN.
   */
  @Column(name = "package_id", length = 1024)
  private String packageId;

  @Column(name = "package_version")
  private String packageVersion;

  /**
   * The dataset this install produced, if any — a denormalised copy so the install list renders a
   * title without joining the lines. The authoritative record is the {@code DATA_SET} artifact
   * line; null for installs that produce no dataset.
   */
  @Column(name = "data_set_id")
  private UUID dataSetId;

  @Column(name = "data_set_name")
  private String dataSetName;

  /**
   * The time of the uninstall. Null while the installation is active. A package can be installed
   * again when it has no active installation.
   */
  @Column(name = "uninstalled_at")
  private LocalDateTime uninstalledAt;

  /**
   * Ordered list, not a set: lines are append-only with no dedup semantics, and the explicit
   * position keeps the response order stable across loads. {@code @BatchSize} instead of a fetch
   * join keeps the collection compatible with paged queries on the parent.
   */
  @Setter(AccessLevel.NONE)
  @OneToMany(
      mappedBy = "installation",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("position ASC")
  @BatchSize(size = 50)
  private List<InstalledArtifact> artifacts = new ArrayList<>();

  /** Adds an artifact line, keeping both association sides and the position in sync. */
  public void addArtifact(InstalledArtifact artifact) {
    artifact.setPosition(artifacts.size());
    artifacts.add(artifact);
    artifact.setInstallation(this);
  }

  /** Whether an uninstall removed the artifacts of this installation. */
  public boolean isUninstalled() {
    return uninstalledAt != null;
  }
}
