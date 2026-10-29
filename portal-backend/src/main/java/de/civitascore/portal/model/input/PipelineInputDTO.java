package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating pipeline resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineInputDTO extends DataSetOwnedInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  private Map<String, Object> styles;
  private Map<String, Object> model;

  private Set<@NotNull UUID> dataSourceIds;

  private Set<@NotNull UUID> dataSinkIds;

  /**
   * The ids the caller confirmed for removal. Taken from the {@code confirmedRemovalIds} query
   * parameter, never from the body.
   */
  @JsonIgnore private Set<UUID> confirmedRemovalIds = Set.of();
}
