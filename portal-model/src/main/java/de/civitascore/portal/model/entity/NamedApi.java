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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A named API endpoint exposed by a {@link DataSet} (per concept #1379, #1383, and #1384, ADR
 * #1362). Each entry produces one published distribution and, after release, one APISIX route at
 * {@code /v1/datasets/{datasetId}/{slug}}.
 *
 * <p>{@code slug} is the URL segment; {@code name} is a human-readable display label. {@code
 * standard} carries the API standard (WFS / WMS / STA / CUSTOM); it is stored as a string rather
 * than a Java enum so the vocabulary can grow and {@code CUSTOM} can stay free-form. {@code
 * routeId} is populated by the dataset saga result handler after a successful release; it is null
 * before release and after unrelease.
 *
 * <p>Validation constraints (enforced by portal-backend, not this entity):
 *
 * <ul>
 *   <li>{@code slug} matches {@code ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$}, max 32 characters
 *   <li>{@code slug} and {@code standard} immutable once the dataset reaches AVAILABLE
 * </ul>
 *
 * Slug uniqueness within a dataset is enforced by the DB via {@code uk_named_api_dataset_slug}.
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

  /** Human-readable display label (e.g. "Traffic Sensor Readings"). Free-form, non-blank. */
  @Column(name = "name", nullable = false)
  private String name;

  /**
   * URL slug used in the public route {@code /v1/datasets/{datasetId}/{slug}}. Lowercase
   * alphanumeric with internal hyphens, max 32 characters, unique within a dataset. Immutable while
   * AVAILABLE.
   */
  @Column(name = "slug", nullable = false, length = 32)
  private String slug;

  /**
   * API standard per ADR #1362: one of {@code WFS}, {@code WMS}, {@code STA}, {@code CUSTOM}.
   * Stored as a string so the vocabulary can grow and {@code CUSTOM} stays free-form.
   */
  @Column(name = "standard", nullable = false, length = 16)
  private String standard;

  /** Optional standard version (e.g. {@code "1.1"} for STA). */
  @Column(name = "version", length = 32)
  private String version;

  /**
   * APISIX route ID populated by the dataset saga result handler after release. Null before release
   * and after unrelease.
   */
  @Column(name = "route_id")
  private String routeId;
}
