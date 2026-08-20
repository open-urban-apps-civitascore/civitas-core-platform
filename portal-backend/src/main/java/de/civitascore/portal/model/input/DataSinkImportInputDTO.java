package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.DataSinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One data sink inside a dataset import bundle. A sink row has no name column — the {@code name}
 * here is the bundle-local handle a pipeline's {@code sinkRef} resolves against, and what the
 * install provenance records as the line's display name.
 *
 * <p>Unlike {@link DataSinkInputDTO} the configuration's {@code element} may name the target data
 * structure by CORE URN (logical or versioned): the bundle cannot know the versioned model URN of a
 * structure the receiving instance resolves, so the import rewrites the reference to the resolved
 * version before the domain service validates it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSinkImportInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") @Schema(
      description =
          "Bundle-local handle: pipelines in the same bundle reference this sink by name in their"
              + " sinkRef, and the install provenance records it as the line's name. Must not"
              + " start with 'urn:' — that prefix marks a literal CORE URN reference.")
  private String name;

  @NotNull private DataSinkType dataSinkType;

  @Schema(
      description =
          "Type-specific configuration. POSTGIS: tableName plus element. FROST: an empty object"
              + " for a passthrough sink (the source delivers the SensorThings envelope itself),"
              + " or just element when a mapping targets this sink. In both cases element may be"
              + " the target structure's CORE URN (logical or versioned), resolved against the"
              + " bundle first and the installed instance second, then rewritten to the resolved"
              + " version's model URN. Send {} rather than omitting the field: only a stored"
              + " configuration mints the URN a bundle pipeline's sinkRef can resolve to.")
  private Map<String, Object> configuration;
}
