package de.civitascore.authz.repository.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a user's full authorization context.
 *
 * <p>Returned by the {@code /api/v1/user-context/{externalId}} endpoint. Contains the user's group
 * memberships, role assignments (with scope), and flattened permission names. OPA consumes this to
 * make authorization decisions.
 */
@Data
@Builder
public class UserContextResponse {
  private UUID userId;
  private String externalId;
  private List<GroupContext> groups;

  @Data
  @Builder
  public static class GroupContext {
    private UUID id;
    private String name;
    private List<AssignmentContext> assignments;
  }

  @Data
  @Builder
  public static class AssignmentContext {
    private UUID roleId;
    private String roleName;
    private String roleType;
    private String scopeType;
    private String scopeId;
    private List<String> permissions;
  }
}
