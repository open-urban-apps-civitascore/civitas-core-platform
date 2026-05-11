package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.ApiStandard;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Output DTO for a named API endpoint exposed by a dataset (per concepts #1379 / #1383 / #1384, ADR
 * #1362). Used in {@link DataSetOutputDTO#getNamedApis()} for GET responses.
 *
 * <p>Mirrors {@link de.civitascore.portal.model.input.NamedApiInputDTO} plus a server-built {@code
 * previewUrl}, which is the predicted public URL once the dataset reaches AVAILABLE.
 */
@Data
public class NamedApiOutputDTO {

  @Schema(
      description = "Human-readable display label for this named API.",
      example = "Traffic Sensor Readings")
  private String name;

  @Schema(
      description =
          "URL slug used as the path segment in the public route"
              + " /v1/datasets/{datasetId}/{slug}.",
      example = "traffic")
  private String slug;

  @Schema(description = "API standard per ADR #1362.", example = "STA")
  private ApiStandard standard;

  @Schema(description = "Optional standard version (e.g. \"1.1\" for STA).", example = "1.1")
  private String version;

  @Schema(
      description = "Optional free-form description of the named API. Max 150 characters.",
      example = "Live traffic counter readings from city sensors.")
  private String description;

  @Schema(
      description =
          "Predicted public URL once the dataset reaches AVAILABLE: "
              + "https://{civitas.api.domain}/v1/datasets/{datasetId}/{slug}. Server-populated;"
              + " absent before the dataset is persisted.",
      accessMode = Schema.AccessMode.READ_ONLY,
      example =
          "https://api.core.civitasconnect.digital/v1/datasets/b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a/traffic")
  private String previewUrl;
}
