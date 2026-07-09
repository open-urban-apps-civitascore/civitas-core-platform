package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating Layer resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LayerInputDTO extends BaseInputDTO {

  @NotNull private UUID dataSinkId;

  @NotBlank private String layerName;

  private String title;
  private String description;
  private List<String> keywords;
  private List<String> attribute;
  private String geometryColumnRef;
  private String cqlFilter;
  private UUID defaultStyleId;
  private List<UUID> alternativeStyleIds;
  private String crs;
  private boolean bboxAutoCalculate = true;
  private Map<String, Object> nativeBoundingBox;
  private Map<String, Object> latLonBoundingBox;

  @JsonIgnore private UUID dataSetId;
}
