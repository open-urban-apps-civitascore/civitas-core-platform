package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents an external data source with its connector configuration and lifecycle status. A data
 * source may optionally reference a {@link DataStructureVersion} that defines its schema.
 *
 * @see ConnectorType
 * @see DataSourceStatus
 */
@Entity
@Table(
    name = "data_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_data_sources_name",
            columnNames = {"name"}),
    indexes = {
      @Index(name = "idx_data_sources_status", columnList = "data_source_status"),
      @Index(name = "idx_data_sources_connector_type", columnList = "connector_type"),
      @Index(
          name = "idx_data_sources_data_structure_version",
          columnList = "data_structure_version_id")
    })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataSource extends BaseDataEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "data_source_status", nullable = false)
  @Builder.Default
  private DataSourceStatus dataSourceStatus = DataSourceStatus.DRAFT;

  @Enumerated(EnumType.STRING)
  @Column(name = "connector_type")
  private ConnectorType connectorType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "configuration", columnDefinition = "jsonb")
  private Map<String, Object> configuration;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_structure_version_id")
  private DataStructureVersion dataStructureVersion;

  @OneToMany(
      mappedBy = "dataSource",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();

  /** {@inheritDoc} Links the assignment to this data source by setting its scope. */
  @Override
  protected void linkAssignment(Assignment assignment) {
    assignment.setScope(this);
  }
}
