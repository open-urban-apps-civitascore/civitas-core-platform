package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DashboardFilterExpansion;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The Dashboard model in Model Forge is authoritative for user-controlled configuration. This
 * platform row holds the engine binding and a compatibility projection for existing API and
 * authorization paths.
 *
 * <p>A dashboard has no relation to {@link DashboardSource}: sources are anchored to a dataset via
 * their {@code mappedDatasetId}, not to a dashboard. The engine-sync result carries the two as
 * independent, dataset-scoped lists (see the dashboard-sync event contract).
 */
@Entity
@Table(name = "dashboards")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Dashboard extends NamedEntity {

  @NotNull @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  private DataSet dataSet;

  /**
   * Null until the engine has materialised the dashboard: the platform creates the row first, and
   * the engine reports its id back asynchronously.
   */
  @Column(name = "external_dashboard_id")
  private String externalDashboardId;

  /** Logical CORE URN of the authoritative Dashboard model in Model Forge. */
  @Column(name = "model_urn", length = 512)
  private String modelUrn;

  @Builder.Default
  @Column(name = "active", nullable = false)
  private boolean active = true;

  /** Whether the dashboard is published in the rendering engine. */
  @Builder.Default
  @Column(name = "released", nullable = false)
  private boolean released = false;

  /** Superset standalone rendering mode included in the dashboard link. */
  @Builder.Default
  @Column(name = "standalone_mode", nullable = false)
  private int standaloneMode = 1;

  /** Whether Superset shows the dashboard filter bar. */
  @Builder.Default
  @Column(name = "show_filters", nullable = false)
  private boolean showFilters = true;

  /** Initial expanded/collapsed state of the Superset filter bar. */
  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "filter_expansion", nullable = false, length = 16)
  private DashboardFilterExpansion filterExpansion = DashboardFilterExpansion.DEFAULT;

  /**
   * Superset export of the dashboard content: each file path without the export's root folder,
   * mapped to the parsed file. The engine imports it once, when it first creates the dashboard.
   * Prototype storage outside Model Forge.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "superset_bundle", columnDefinition = "jsonb")
  private Map<String, Object> supersetBundle;
}
