package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating dataset resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required and must be between 3 and 255 characters") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  private String description;

  @Schema(description = "Whether this dataset is publicly accessible, defaults to false")
  private Boolean openDataAccess = false;

  @Valid @Schema(
      description =
          "Named API endpoints exposed by this dataset (per concepts #1379 and #1383). Each entry"
              + " produces one published distribution and one APISIX route after release. Slug"
              + " uniqueness within the dataset is enforced at publish time (#1312).")
  private List<NamedApiDTO> namedApis = new ArrayList<>();
}
