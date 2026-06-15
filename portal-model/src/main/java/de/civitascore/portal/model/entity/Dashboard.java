package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A dashboard rendered by the dashboard engine (Superset today). The dashboard itself lives in the
 * engine's metadata DB; the platform row holds the binding the engine and the authorisation layer
 * need.
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
  private DataSet dataset;

  /**
   * Null until the engine has materialised the dashboard: the platform creates the row first, and
   * the engine reports its id back asynchronously.
   */
  @Column(name = "external_dashboard_id")
  private String externalDashboardId;

  @Builder.Default
  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Builder.Default
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dashboard_dashboard_sources",
      joinColumns = @JoinColumn(name = "dashboard_id"),
      inverseJoinColumns = @JoinColumn(name = "dashboard_source_id"))
  private Set<DashboardSource> sources = new HashSet<>();
}
