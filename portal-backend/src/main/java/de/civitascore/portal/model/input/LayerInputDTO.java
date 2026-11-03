package de.civitascore.portal.model.input;

import de.civitascore.portal.model.input.validation.CqlFilterSyntax;
import de.civitascore.portal.model.input.validation.CqlFilterSyntaxValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
  private List<String> attribute;
  private String geometryColumnRef;

  @CqlFilterSyntax
  @Size(
      max = CqlFilterSyntaxValidator.MAX_LENGTH,
      message = "CQL filter must be at most {max} characters")
  @Schema(
      description =
          "Optional ECQL condition restricting the rows the layer publishes, e.g. status = 'frei'"
              + " AND kapazitaet > 50. Rejected on save when its syntax is invalid; column and"
              + " function names are not checked. Max 500 characters.",
      example = "status = 'frei'")
  private String cqlFilter;

  private UUID defaultStyleId;
  private List<UUID> alternativeStyleIds;
  private String crs;
  private Map<String, Object> nativeBoundingBox;
  private Map<String, Object> latLonBoundingBox;
}
