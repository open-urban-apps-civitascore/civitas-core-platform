package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating Layer resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class LayerInputDTO extends DataSetOwnedInputDTO {

  @NotNull private UUID dataSinkId;

  // Becomes a GeoServer WFS feature-type name, which must be safe as an XML element name; an unsafe
  // value would otherwise only fail later, opaquely, when the publish saga hits GeoServer.
  @NotBlank @Pattern(
      regexp = "^[A-Za-z_-][A-Za-z0-9_-]*$",
      message =
          "Layer name must contain only letters, digits, underscores or hyphens"
              + " and must not start with a digit")
  private String layerName;

  private String title;
  private String description;
  private List<String> keywords;
  private List<String> attribute;
  private String geometryColumnRef;
  private String cqlFilter;
  private UUID defaultStyleId;
  private List<UUID> alternativeStyleIds;
  private String crs;
  private Map<String, Object> nativeBoundingBox;
  private Map<String, Object> latLonBoundingBox;
}
