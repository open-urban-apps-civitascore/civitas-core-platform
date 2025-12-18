package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;
  private String identifier;
  private String version;
  private UUID ownerUserId;
  private UUID dataSetSeriesId;
  private List<UUID> dataSpaceIds;
  private List<UUID> agentIds;
  private List<UUID> distributionIds;
  private List<UUID> catalogIds;
  private String externalId;
  private String format;
}
