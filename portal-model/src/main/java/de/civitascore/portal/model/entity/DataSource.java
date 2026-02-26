package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "data_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_data_sources_name",
            columnNames = {"name"}),
    indexes = {
      @Index(name = "idx_data_sources_status", columnList = "data_source_status"),
      @Index(name = "idx_data_sources_connector_type", columnList = "connector_type")
    })
@Getter
@Setter
public class DataSource extends NamedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "data_source_status", nullable = false)
  private DataSourceStatus dataSourceStatus = DataSourceStatus.DRAFT;

  @Enumerated(EnumType.STRING)
  @Column(name = "connector_type")
  private ConnectorType connectorType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "configuration", columnDefinition = "jsonb")
  private Map<String, Object> configuration;

  @OneToMany(mappedBy = "dataSource", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();
}
