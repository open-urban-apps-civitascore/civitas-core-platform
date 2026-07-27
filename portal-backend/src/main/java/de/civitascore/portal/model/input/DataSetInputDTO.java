package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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

  /**
   * Defaults to {@code null} so the PATCH reconciler distinguishes "field omitted" (leave the
   * entity collection untouched) from "explicit empty list" (clear the collection).
   */
  @Valid @Schema(
      description =
          "Named API endpoints exposed by this dataset. Each entry produces one APISIX route"
              + " after release.")
  private List<NamedApiInputDTO> namedApis = null;

  @Schema(description = "ID of the datapool this dataset belongs to.")
  private UUID datapoolId;

  /**
   * Whether {@code datapoolId} was present in the request body at all. A plain {@code null} cannot
   * tell "omitted" from "explicitly cleared", but the two must behave differently: omitting the
   * field leaves the dataset's pool untouched, while sending {@code null} clears it. Without the
   * distinction a partial PUT (e.g. a rename) would silently drop the dataset out of its pool.
   *
   * <p>Derived from the setter, never from the wire: a client must not be able to claim the field
   * was present when it was not.
   */
  @JsonIgnore
  @Schema(hidden = true)
  private boolean datapoolIdPresent;

  public void setDatapoolId(UUID datapoolId) {
    this.datapoolId = datapoolId;
    this.datapoolIdPresent = true;
  }
}
