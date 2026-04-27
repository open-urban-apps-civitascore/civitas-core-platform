package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a named API endpoint exposed by a dataset (per concepts #1379 / #1383 / #1384,
 * ADR #1362). Used in both directions: input (POST/PUT/PATCH dataset bodies) and output (GET
 * responses), with {@link #previewUrl} populated only on the output side.
 *
 * <p>Format/length validation of {@code slug} and the controlled {@code standard} vocabulary land
 * in #1312; this issue (#1315) only wires the field through CRUD with non-blank guards.
 */
@Data
@NoArgsConstructor
public class NamedApiDTO {

  @NotBlank(message = "name must not be blank") @Schema(
      description = "Human-readable display label for this named API.",
      example = "Traffic Sensor Readings")
  private String name;

  @NotBlank(message = "slug must not be blank") @Schema(
      description =
          "URL slug used as the path segment in the public route"
              + " /v1/datasets/{datasetId}/{slug}. Lowercase alphanumeric with internal hyphens,"
              + " max 32 characters, unique within a dataset, immutable while AVAILABLE."
              + " Format/length validation lands in #1312.",
      example = "traffic")
  private String slug;

  @NotBlank(message = "standard must not be blank") @Schema(
      description =
          "API standard per ADR #1362. CUSTOM allows free-form, non-standard APIs. Immutable"
              + " once the dataset reaches AVAILABLE. Vocabulary validation lands in #1312.",
      allowableValues = {"WFS", "WMS", "STA", "CUSTOM"},
      example = "STA")
  private String standard;

  @Schema(
      description = "Optional standard version (e.g. \"1.1\" for STA). Free-form string.",
      example = "1.1")
  private String version;

  @Schema(
      description =
          "Predicted public URL once the dataset reaches AVAILABLE: "
              + "https://{civitas.api.domain}/v1/datasets/{datasetId}/{slug}. Server-populated on"
              + " output; ignored on input.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example =
          "https://api.core.civitasconnect.digital/v1/datasets/b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a/traffic")
  private String previewUrl;
}
