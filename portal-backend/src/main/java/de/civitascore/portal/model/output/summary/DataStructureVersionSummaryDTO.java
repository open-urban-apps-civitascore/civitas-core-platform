package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionSummaryDTO extends BaseSummaryDTO {
  protected LocalDateTime createdAt;
  protected LocalDateTime modifiedAt;

  private DataStructureVersionStatus dataStructureVersionStatus;
  private DataStructureVersionSource dataStructureVersionSource;
  private String version;
}
