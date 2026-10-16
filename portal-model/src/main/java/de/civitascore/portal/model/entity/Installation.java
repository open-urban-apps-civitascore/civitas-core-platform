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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * The record of one install: the package that the caller installed and, through {@link
 * InstalledArtifact}, each artifact that the install created. The install writes the record in the
 * same transaction as the artifacts. A record exists only for a complete install. The audit columns
 * record who installed the package and when.
 *
 * <p>A line names its artifact by id and URN, not by a foreign key. When a user deletes an
 * artifact, the record stays.
 */
@Getter
@Setter
@Entity
@Table(name = "installations")
public class Installation extends BaseEntity {

  private static final int PACKAGE_ID_LENGTH = 1024;

  /**
   * The identity of the package, as the caller gives it. The journal keeps this value as it is and
   * does not interpret it. The value does not have to be a CORE URN, because it follows the scheme
   * of the catalogue.
   */
  @Column(name = "package_id", length = PACKAGE_ID_LENGTH, nullable = false)
  private String packageId;

  @Column(name = "package_version")
  private String packageVersion;

  /**
   * The artifact lines in the sequence of the install. A list, not a set: the install only adds
   * lines, and the position keeps the sequence the same for each read. {@code @BatchSize} loads the
   * lines for a page of installations in a few queries. A fetch join would make Hibernate page in
   * memory.
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

  /** Adds an artifact line. Sets the position of the line and the link to this installation. */
  public void addArtifact(InstalledArtifact artifact) {
    artifact.setPosition(artifacts.size());
    artifacts.add(artifact);
    artifact.setInstallation(this);
  }
}
