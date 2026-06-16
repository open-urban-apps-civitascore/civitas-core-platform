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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The dashboard itself lives in the rendering engine's own metadata DB; this platform row holds
 * only the binding the engine and the authorisation layer need to resolve and authorise it.
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

  @Builder.Default
  @Setter(AccessLevel.NONE)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dashboard_dashboard_sources",
      joinColumns = @JoinColumn(name = "dashboard_id"),
      inverseJoinColumns = @JoinColumn(name = "dashboard_source_id"))
  private Set<DashboardSource> sources = new HashSet<>();

  /**
   * Mutates the managed set in place rather than swapping the reference, so the binding relation a
   * persistence context already tracks stays intact. The 1:N anchor invariant (every source maps to
   * this dashboard's dataset) is enforced on the write path, not here.
   *
   * @param newSources the new source bindings, or {@code null} to clear
   */
  public void setSources(Collection<DashboardSource> newSources) {
    this.sources.clear();
    if (newSources != null) {
      this.sources.addAll(newSources);
    }
  }
}
