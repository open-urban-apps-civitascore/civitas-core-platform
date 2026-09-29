package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Layer entities. */
@Schema(description = "Layer details")
@Data
@EqualsAndHashCode(callSuper = true)
public class LayerOutputDTO extends BaseOutputDTO {

  @Schema(description = "ID of the dataset this layer belongs to")
  private UUID dataSetId;

  @Schema(description = "ID of the datasink this layer is derived from")
  private UUID dataSinkId;

  @Schema(description = "Unique name of the layer within its datasink")
  private String layerName;

  @Schema(description = "Human-readable title")
  private String title;

  @Schema(description = "Human-readable description")
  private String description;

  @Schema(description = "Keywords associated with the layer")
  private List<String> keywords = new ArrayList<>();

  @Schema(description = "Attribute names exposed by the layer")
  private List<String> attribute = new ArrayList<>();

  @Schema(description = "Name of the geometry column in the underlying data")
  private String geometryColumnRef;

  @Schema(description = "CQL filter applied to the layer data")
  private String cqlFilter;

  @Schema(description = "ID of the default style for this layer")
  private UUID defaultStyleId;

  @Schema(description = "IDs of alternative styles available for this layer")
  private List<UUID> alternativeStyleIds = new ArrayList<>();

  @Schema(description = "Coordinate reference system (e.g. EPSG:4326)")
  private String crs;

  @Schema(description = "Native bounding box of the layer data")
  private Map<String, Object> nativeBoundingBox;

  @Schema(description = "Bounding box in WGS84 lat/lon coordinates")
  private Map<String, Object> latLonBoundingBox;
}
