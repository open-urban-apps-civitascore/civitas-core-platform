package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for data structure version entities, used in list endpoints and nested
 * references.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionSummaryDTO extends BaseSummaryDTO {
  protected LocalDateTime createdAt;
  protected LocalDateTime modifiedAt;

  private DataStructureVersionStatus dataStructureVersionStatus;
  private DataStructureVersionSource dataStructureVersionSource;

  private String description;
  private String version;
  private UUID dataStructureId;
}
