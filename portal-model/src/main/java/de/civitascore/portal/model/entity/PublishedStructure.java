package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The pin of a Data structure that a sink port publishes.
 *
 * <p>The document itself is generated at build time and ships as a resource. This shell holds what
 * the registry decides: the identity the structure is stored under and the version it was given. An
 * import names that version, so a later version of the port structure leaves a Data structure that
 * imported an earlier one untouched.
 *
 * <p>It is not Tenant data. One row per port, the same in every installation.
 */
@Entity
@Table(name = "published_structures")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PublishedStructure extends BaseEntity {

  /** The port label, as the sink configuration carries it. */
  @Column(name = "port", nullable = false, unique = true)
  @NotNull private String port;

  /** The identity of the structure across its versions. */
  @Column(name = "logical_urn", nullable = false)
  @NotNull private String logicalUrn;

  /** The version the registry assigned when the structure was last stored. */
  @Column(name = "versioned_urn", nullable = false)
  @NotNull private String versionedUrn;

  /**
   * The hash of the rendered document. It decides whether the declaration changed, because the
   * stored artifact cannot be compared with it: the registry splits the document into its member
   * Elements and stamps its own self-description on it.
   */
  @Column(name = "content_hash", nullable = false)
  @NotNull private String contentHash;
}
