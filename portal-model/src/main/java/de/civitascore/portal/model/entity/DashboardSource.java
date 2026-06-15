package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The platform-side projection of a source object that lives in the dashboard engine's own metadata
 * DB (Superset today). The platform stores only what it needs to authorise access: the engine's id
 * for the object, the dataset that authorises it, and the physical table/view the engine queries.
 *
 * <p>This is deliberately <em>not</em> the platform {@link DataSet}: several of these may map to
 * the same dataset, and they carry no scope of their own.
 */
@Entity
@Table(name = "dashboard_sources")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DashboardSource extends BaseEntity {

  @NotBlank @Column(name = "external_source_id", nullable = false)
  private String externalSourceId;

  /**
   * The {@link DataSet} that authorises access to this source. A loose UUID rather than a typed FK:
   * the config-adapter writes these bindings on its own schedule, independent of the dataset
   * lifecycle, so a hard FK would be too rigid — referential integrity is left to the binding-CRUD
   * layer rather than the database.
   */
  @Column(name = "mapped_dataset_id", nullable = false)
  @NotNull private UUID mappedDatasetId;

  @NotBlank @Column(name = "physical_source_identifier", nullable = false)
  private String physicalSourceIdentifier;
}
