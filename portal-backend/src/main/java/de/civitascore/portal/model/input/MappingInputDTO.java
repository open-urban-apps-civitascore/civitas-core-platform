package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

/** Input DTO for creating a Mapping and for storing a new version of it. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingInputDTO {

  private String title;

  private String description;

  @Schema(description = "Versioned CORE URN of the source DataStructure, optional for a draft")
  private String source;

  @Schema(description = "Versioned CORE URN of the target DataStructure, optional for a draft")
  private String target;

  @Schema(
      description =
          "Rules per target field path, as the CORE Mapping schema mapping.schema.json defines"
              + " them")
  @NotNull private Map<String, Object> fields;

  private Map<String, @Valid MappingNodePosition> positions;
}
