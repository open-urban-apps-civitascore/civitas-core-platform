package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
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
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a specific version of a {@link DataStructure}, including its schema definition,
 * status, and optional Model Atlas reference.
 *
 * @see DataStructureVersionStatus
 * @see DataStructureVersionSource
 */
@Entity
@Table(name = "data_structure_versions")
@Getter
@Setter
public class DataStructureVersion extends BaseEntity {

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_version_status", nullable = false)
  @NotNull private DataStructureVersionStatus dataStructureVersionStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_version_source", nullable = false)
  private DataStructureVersionSource dataStructureVersionSource;

  @Column(name = "version", nullable = false)
  private String version;

  @Column(name = "model_atlas_uri")
  private String modelAtlasUri;

  @Column(name = "model_name")
  private String modelName;

  @Column(name = "external_id")
  private String externalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "styles", columnDefinition = "jsonb")
  private Map<String, Object> styles;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_structure_id", nullable = false)
  private DataStructure dataStructure;
}
