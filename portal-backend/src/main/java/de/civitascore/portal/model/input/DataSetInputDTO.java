package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  @NotBlank(message = "Title is required") private String title;

  private String description;
  private String ownerUserId;
  private List<String> dataSpaceIds;
  private String externalId;
  private String format;
}
