package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * One bundle install as it happened: which bundle (as declared by the caller), which dataset shell
 * it produced, and — via {@link InstalledArtifact} — what it did to every contained artifact.
 * Written in the same transaction as the import itself, so the record exists exactly iff the
 * install committed. Who and when come from the audit columns ({@code createdBy}, {@code
 * createdAt}).
 *
 * <p>Append-only history: the referenced dataset and artifacts are stored as plain ids and names,
 * not foreign keys, so the record survives their deletion — which is what makes it usable for
 * uninstall and reference counting later.
 */
@Getter
@Setter
@Entity
@Table(name = "bundle_installations")
public class BundleInstallation extends BaseEntity {

  /** Catalogue identity of the installed bundle, as declared by the caller. Optional. */
  @Column(name = "bundle_urn", length = 1024)
  private String bundleUrn;

  @Column(name = "bundle_version")
  private String bundleVersion;

  @Column(name = "data_set_id", nullable = false)
  private UUID dataSetId;

  @Column(name = "data_set_name", nullable = false)
  private String dataSetName;

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
