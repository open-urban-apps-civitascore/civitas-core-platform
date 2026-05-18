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

  private UUID dataSetId;
  private UUID dataSinkId;
  private String layerName;
  private String title;
  private String description;
  private List<String> keywords = new ArrayList<>();
  private List<String> attribute = new ArrayList<>();
  private String geometryColumnRef;
  private String cqlFilter;
  private UUID defaultStyleId;
  private List<UUID> alternativeStyleIds = new ArrayList<>();
  private String crs;
  private boolean bboxAutoCalculate;
  private Map<String, Object> nativeBoundingBox;
  private Map<String, Object> latLonBoundingBox;

  @Schema(
      description = "Derived geometry type — null until provisioned",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String geometryType;

  @Schema(
      description = "Native CRS — null until provisioned",
      accessMode = Schema.AccessMode.READ_ONLY)
  private String nativeCRS;
}
