package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A named API endpoint exposed by a {@link DataSet}. Each entry produces one published distribution
 * and, after release, one APISIX route at {@code /v1/datasets/{datasetId}/{slug}}.
 *
 * <p>{@code slug} and {@code standard} are immutable once the dataset reaches AVAILABLE. Slug
 * format ({@code ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$}, max 32 chars) is enforced at the DTO boundary;
 * uniqueness within a dataset is enforced by the DB via {@code uk_named_api_dataset_slug}.
 */
@Entity
@Table(
    name = "named_apis",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_named_api_dataset_slug",
            columnNames = {"dataset_id", "slug"}),
    indexes = {@Index(name = "idx_named_api_dataset", columnList = "dataset_id")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NamedApi extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dataset_id", nullable = false)
  private DataSet dataSet;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, length = 32)
  private String slug;

  /**
   * API standard: one of {@code WFS}, {@code WMS}, {@code STA}, {@code CUSTOM}. Stored as a string
   * so the vocabulary can grow and {@code CUSTOM} stays free-form.
   */
  @Column(name = "standard", nullable = false, length = 16)
  private String standard;

  @Column(name = "version", length = 32)
  private String version;

  /** Portal-backend-private; not part of the saga contract. */
  @Size(max = 150) @Column(name = "description", length = 150)
  private String description;

  /**
   * Populated by the saga result handler after release; null before release and after unrelease.
   */
  @Column(name = "route_id")
  private String routeId;
}
