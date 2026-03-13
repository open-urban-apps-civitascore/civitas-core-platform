package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.ScopeType;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

@Data
public class MeAssignmentOutputDTO {
  private ScopeType scopeType;
  private UUID scopeId;
  private Set<String> permissions;
}
