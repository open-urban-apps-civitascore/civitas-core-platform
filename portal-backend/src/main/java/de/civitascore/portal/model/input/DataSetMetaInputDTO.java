package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** The metadata of a dataset: the fields that stay changeable while the dataset is released. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetMetaInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required and must be between 3 and 255 characters") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  @NotBlank(message = "Description is required") private String description;

  @Schema(description = "Whether this dataset is publicly accessible, defaults to false")
  private Boolean openDataAccess = false;

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
