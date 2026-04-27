package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Input DTO for a named API endpoint exposed by a dataset (per concepts #1379 / #1383 / #1384, ADR
 * #1362). Used in {@link DataSetInputDTO#getNamedApis()} for POST/PUT/PATCH bodies.
 *
 * <p>Format/length validation of {@code slug} and the controlled {@code standard} vocabulary land
 * in #1312; this issue (#1315) only enforces non-blank guards.
 *
 * <p>Note: {@code previewUrl} is intentionally absent on the input side — it is server-built and
 * lives only on {@link de.civitascore.portal.model.output.NamedApiOutputDTO}.
 */
@Data
public class NamedApiInputDTO {

  @NotBlank(message = "Name is required") @Schema(
      description = "Human-readable display label for this named API.",
      example = "Traffic Sensor Readings")
  private String name;

  @NotBlank(message = "Slug is required") @Schema(
      description =
          "URL slug used as the path segment in the public route"
              + " /v1/datasets/{datasetId}/{slug}. Lowercase alphanumeric with internal hyphens,"
              + " max 32 characters, unique within a dataset, immutable while AVAILABLE.",
      example = "traffic")
  private String slug;

  @NotBlank(message = "Standard is required") @Schema(
      description =
          "API standard per ADR #1362. CUSTOM allows free-form, non-standard APIs. Immutable"
              + " once the dataset reaches AVAILABLE.",
      allowableValues = {"WFS", "WMS", "STA", "CUSTOM"},
      example = "STA")
  private String standard;

  @Schema(
      description = "Optional standard version (e.g. \"1.1\" for STA). Free-form string.",
      example = "1.1")
  private String version;
}
