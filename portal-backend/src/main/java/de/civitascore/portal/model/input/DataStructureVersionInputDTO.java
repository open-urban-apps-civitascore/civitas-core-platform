package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Input DTO for creating and updating data structure version resources.
 *
 * <p>The version string is not a client input: Model Forge is the sole version authority and
 * assigns it when the model is stored.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionInputDTO extends DataStructureVersionMetaInputDTO {

  @JsonIgnore private DataStructureVersionStatus dataStructureVersionStatus;

  private String modelName;

  @Schema(description = "Data model definition as a JSON Schema document")
  private Map<String, Object> model;

  private Map<String, Object> styles;

  /**
   * The published structures the diagram was built from, each pinned at the version it was loaded
   * at. The editor derives them from the diagram, like the model document.
   */
  private List<String> importedStructureUrns;

  @JsonIgnore
  // The dataStructureId is required for the service layer to associate the version with the correct
  // data structure, but it should not be provided by the client in the input DTO.
  private UUID dataStructureId;
}
