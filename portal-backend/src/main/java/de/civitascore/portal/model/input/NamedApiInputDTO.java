package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.ApiStandard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Input DTO for a named API endpoint exposed by a dataset. Used in {@link
 * DataSetInputDTO#getNamedApis()} for POST/PUT/PATCH bodies.
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

  @NotBlank(message = "Slug is required") @Pattern(
      regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
      message =
          "Slug must be lowercase alphanumeric with internal hyphens (e.g. 'traffic-counter')")
  @Size(max = 32, message = "Slug must be at most 32 characters") @Schema(
      description =
          "URL slug used as the path segment in the public route"
              + " /v1/datasets/{datasetId}/{slug}. Lowercase alphanumeric with internal hyphens,"
              + " max 32 characters, unique within a dataset, immutable while AVAILABLE.",
      example = "traffic")
  private String slug;

  @NotNull(message = "Standard is required") @Schema(
      description =
          "API standard. CUSTOM allows free-form, non-standard APIs. Immutable once the dataset"
              + " reaches AVAILABLE.",
      example = "STA")
  private ApiStandard standard;

  @Schema(
      description = "Optional standard version (e.g. \"1.1\" for STA). Free-form string.",
      example = "1.1")
  private String version;

  @Size(max = 150, message = "Description must be at most 150 characters") @Schema(
      description =
          "Optional free-form description of the named API surfaced in the dataset form and"
              + " discovery responses. Max 150 characters.",
      example = "Live traffic counter readings from city sensors.")
  private String description;
}
