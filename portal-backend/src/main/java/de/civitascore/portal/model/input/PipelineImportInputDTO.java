package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One pipeline inside a dataset import bundle. Unlike {@link PipelineInputDTO} it does not carry
 * {@code dataSourceIds}/{@code dataSinkIds}: the bundle cannot know server-assigned ids. Instead
 * the graph itself is the single source of truth — the import walks the model's nodes, resolves
 * every name reference to the bundle member of that name, rewrites it to the member's minted CORE
 * URN, and derives the source/sink links from exactly those resolutions.
 *
 * <p>Reference rules inside {@code model.nodes[]}: a {@code sourceRef}/{@code sinkRef}/{@code
 * mappingRef} value starting with {@code urn:} is treated as a literal CORE URN and passed through
 * verbatim (no link is derived — the referenced artifact is expected to exist on the instance); any
 * other value is the bundle-local {@code name} of a data source, data sink or mapping shipped in
 * the same bundle.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineImportInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  @Schema(
      description =
          "CORE Pipeline graph (nodes/edges). Node references may name bundle members by their"
              + " bundle-local name; the import rewrites them to the minted CORE URNs of the"
              + " created artifacts. Values starting with 'urn:' pass through verbatim.")
  private Map<String, Object> model;

  @Schema(description = "Editor layout (React Flow), stored alongside the model as x-ui-styles")
  private Map<String, Object> styles;
}
