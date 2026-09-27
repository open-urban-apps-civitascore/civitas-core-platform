package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating dataset resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends DataSetMetaInputDTO {

  /**
   * Defaults to {@code null} so the PATCH reconciler distinguishes "field omitted" (leave the
   * entity collection untouched) from "explicit empty list" (clear the collection).
   */
  @Valid @Schema(
      description =
          "Named API endpoints exposed by this dataset. Each entry produces one APISIX route"
              + " after release.")
  private List<NamedApiInputDTO> namedApis = null;
}
