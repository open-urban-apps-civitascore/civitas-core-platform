package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The metadata of a data source: the fields that stay changeable while the data source is released.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceMetaInputDTO extends BaseDataEntityInputDTO {

  @Schema(description = "Data source name (required)")
  @NotBlank(message = "Name is required") private String name;

  @Schema(description = "Data source description (required)")
  @NotBlank(message = "Description is required") private String description;

  @Schema(description = "Datapool scope configuration. Defaults to ALL if omitted on create.")
  @JsonSetter(nulls = Nulls.FAIL)
  private DatapoolScopeInputDTO datapoolScope;
}
