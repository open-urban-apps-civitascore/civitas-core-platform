package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionOutputDTO extends BaseOutputDTO {

  @Schema(description = "Permission identifier (e.g. DATASET_READ)", example = "DATASET_READ")
  private String name;

  @Schema(example = "Read access to datasets")
  private String description;

  @Schema(example = "DATA")
  private PermissionType permissionType;

  @Schema(description = "Functional category (e.g. DATASET, USER)", example = "DATASET")
  private PermissionCategory category;
}
