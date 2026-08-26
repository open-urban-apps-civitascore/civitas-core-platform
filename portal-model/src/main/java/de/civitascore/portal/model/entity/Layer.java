package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a WFS/WMS layer derived from a {@link DataSink}, belonging to a {@link DataSet}. Each
 * layer has a unique name within its sink and optionally references a default {@link Style} and a
 * set of alternative styles.
 */
@Entity
@Table(name = "layers")
@Getter
@Setter
public class Layer extends BaseEntity implements DataSetOwned {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  @NotNull private DataSet dataSet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "datasink_id", nullable = false)
  @NotNull private DataSink dataSink;

  @NotBlank @Column(name = "layer_name", nullable = false)
  private String layerName;

  @Column(name = "title")
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "keywords", columnDefinition = "text[]")
  private List<String> keywords;

  @Column(name = "attribute", columnDefinition = "text[]")
  private List<String> attribute;

  @Column(name = "geometry_column_ref")
  private String geometryColumnRef;

  @Column(name = "cql_filter", columnDefinition = "TEXT")
  private String cqlFilter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "default_style_id")
  private Style defaultStyle;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "layer_alternative_styles",
      joinColumns = @JoinColumn(name = "layer_id"),
      inverseJoinColumns = @JoinColumn(name = "style_id"))
  private Set<Style> alternativeStyles = new HashSet<>();

  @Column(name = "crs")
  private String crs;

  @Column(name = "bbox_auto_calculate", nullable = false)
  private boolean bboxAutoCalculate = true;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "native_bounding_box", columnDefinition = "jsonb")
  private Map<String, Object> nativeBoundingBox;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "lat_lon_bounding_box", columnDefinition = "jsonb")
  private Map<String, Object> latLonBoundingBox;
}
