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
 * One install as it happened: which catalogue entry (as declared by the caller) and — via {@link
 * InstalledArtifact} — what it did to every artifact it touched. Written in the same transaction as
 * the import itself, so the record exists exactly iff the install committed. Who and when come from
 * the audit columns ({@code createdBy}, {@code createdAt}).
 *
 * <p>Append-only history: referenced artifacts are stored as plain ids and URNs, not foreign keys,
 * so the record survives their deletion — which is what makes it usable for uninstall and reference
 * counting later.
 */
@Getter
@Setter
@Entity
@Table(name = "installations")
public class Installation extends BaseEntity {

  /**
   * Catalogue identity of the installed entry, as declared by the caller — recorded verbatim and
   * never interpreted. Deliberately not called a URN: the value follows whatever scheme the
   * catalogue uses and is not required to be a CORE URN.
   */
  @Column(name = "catalog_entry_id", length = 1024)
  private String catalogEntryId;

  @Column(name = "catalog_entry_version")
  private String catalogEntryVersion;

  /**
   * The dataset this install produced, if any — a denormalised copy so the install list renders a
   * title without joining the lines. The authoritative record is the {@code DATA_SET} artifact
   * line; null for installs that produce no dataset, such as the single-structure import.
   */
  @Column(name = "data_set_id")
  private UUID dataSetId;

  @Column(name = "data_set_name")
  private String dataSetName;

  /**
   * When this installation was uninstalled; null while active. The one permitted amendment to the
   * journal: a single terminal timestamp — lines and header survive, so "was installed from X to Y"
   * stays answerable. Who uninstalled follows from {@code modifiedBy} of the same write.
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
}
