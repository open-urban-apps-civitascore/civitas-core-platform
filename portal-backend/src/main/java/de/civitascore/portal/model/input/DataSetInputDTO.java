package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") private String name;

  @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters") private String title;

  private String identifier;
  private String description;

  private String version;

  private UUID ownerUserId;
  private UUID dataSetSeriesId;
  private List<UUID> dataSpaceIds;
  private List<UUID> agentIds;
  private List<UUID> distributionIds;
  private List<UUID> catalogIds;
  private List<UUID> pipelineIds;

  private Long persistenceId;
  private String externalId;
  private String format;
  private Boolean openDataAccess;
}
