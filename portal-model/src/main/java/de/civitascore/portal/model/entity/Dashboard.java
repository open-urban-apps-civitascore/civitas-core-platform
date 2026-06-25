package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The dashboard itself lives in the rendering engine's own metadata DB; this platform row holds
 * only the binding the engine and the authorisation layer need to resolve and authorise it.
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

  @Builder.Default
  @Column(name = "active", nullable = false)
  private boolean active = true;
}
