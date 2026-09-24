package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a specific version of a {@link DataStructure}. The version is a thin shell: name,
 * status and relations live here, while the model content (JSON Schema, including its UI styles
 * under the {@code x-ui-styles} keyword) lives in the Model Forge registry, pinned by {@link
 * #modelUrn}.
 *
 * @see DataStructureVersionStatus
 */
@Entity
@Table(name = "data_structure_versions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataStructureVersion extends BaseEntity {

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_version_status", nullable = false)
  @NotNull private DataStructureVersionStatus dataStructureVersionStatus;

  // Assigned by Model Forge when the model is stored; null while a draft has no model yet.
  @Column(name = "version")
  private String version;

  /**
   * Versioned CORE URN pinning this version's model artifact in Model Forge (the concrete version
   * the registry assigned). Null until the model has been stored in Model Forge.
   */
  @Column(name = "model_urn")
  private String modelUrn;

  @Column(name = "model_name")
  private String modelName;

  /**
   * The published structures this version was built from, each pinned at the version it was loaded
   * at. Derived from the diagram on every save, like the model document: the diagram is where the
   * import happens, and this is what makes the provenance readable without it.
   */
  @Column(name = "imported_structure_urns", columnDefinition = "text[]")
  private List<String> importedStructureUrns;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_structure_id", nullable = false)
  private DataStructure dataStructure;
}
