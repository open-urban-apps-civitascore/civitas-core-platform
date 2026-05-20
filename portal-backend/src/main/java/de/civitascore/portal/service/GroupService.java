package de.civitascore.portal.service;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService extends EventPublishingService<Group, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final AssignmentFactory assignmentFactory;
  private final String targetRealm;

  public GroupService(
      ConfigEventPublisherService configEventPublisher,
      GroupRepository groupRepository,
      GroupMapper groupMapper,
      UserService userService,
      AssignmentFactory assignmentFactory,
      @Value("${keycloak.target-realm}") String targetRealm) {
    super(configEventPublisher);
    this.groupRepository = groupRepository;
    this.groupMapper = groupMapper;
    this.userService = userService;
    this.assignmentFactory = assignmentFactory;
    this.targetRealm = targetRealm;
  }

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    if (input.getContactUserId() != null) {
      entity.setContactUser(userService.findByIdOrThrow(input.getContactUserId()));
    } else {
      entity.setContactUser(null);
    }

    if (Objects.nonNull(input.getMemberIds())) {
      entity.setMembers(new HashSet<>());
      if (!input.getMemberIds().isEmpty()) {
        entity.setMembers(
            new HashSet<>(userService.getRepository().findAllById(input.getMemberIds())));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Transactional
  public Group replaceAssignments(UUID groupId, Set<AssignmentGroupInputDTO> assignmentInputs) {
    Group group = findByIdOrThrow(groupId);

    Set<Assignment> newAssignments =
        assignmentInputs.stream().map(assignmentFactory::build).collect(Collectors.toSet());

    group.setAssignments(newAssignments);

    return save(group);
  }

  /**
   * Override deleteById to validate child groups before publishing to Keycloak.
   * EventPublishingService.deleteById does not call preProcessDelete, so we perform the validation
   * here.
   */
  @Override
  @Transactional
  public void deleteById(UUID id) {
    Group group = findByIdOrThrow(id);
    if (!group.getChildGroups().isEmpty()) {
      throw new ResourceInUseException(
          "Group",
          group.getId(),
          "Cannot delete Group because it has child groups. Remove or reassign child groups first.");
    }
    super.deleteById(id);
  }

  @Override
  protected ConfigValue toConfigValuePostSave(
      Group entity, GroupInputDTO input, ConfigValue preSaveConfigValue) {
    return buildGroupConfig(entity);
  }

  /**
   * Builds a {@link GroupConfig} from a Group entity. Used by both CRUD events and catch-up sync.
   *
   * @throws IllegalStateException if {@code entity.getName()} is null or blank — name is enforced
   *     non-blank at API ingress, so a null here means a corrupt DB row, and we fail loudly rather
   *     than silently NPE-ing into a permanent unsynced state.
   */
  public static GroupConfig buildGroupConfig(Group entity) {
    if (entity.getName() == null || entity.getName().isBlank()) {
      throw new IllegalStateException(
          "Cannot build GroupConfig for group id="
              + entity.getId()
              + ": name is null or blank. This indicates a corrupt DB row; "
              + "the API enforces non-blank names on create/update.");
    }

    GroupConfig groupConfig = new GroupConfig();

    if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
      groupConfig.setId(entity.getExternalId());
    }

    groupConfig.setName(entity.getName());

    if (entity.getParentGroup() != null && entity.getParentGroup().getExternalId() != null) {
      groupConfig.setParentId(entity.getParentGroup().getExternalId());
    }

    return groupConfig;
  }

  @Override
  protected void updateExternalId(Group entity, String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      entity.setExternalId(externalId);
    }
  }

  @Override
  protected Topics resolveTopic(String operation) {
    return switch (operation.toLowerCase()) {
      case "create" -> Topics.GROUP_CREATED;
      case "update" -> Topics.GROUP_UPDATED;
      case "delete" -> Topics.GROUP_DELETED;
      default -> throw new IllegalArgumentException("Unknown operation for Group: " + operation);
    };
  }

  @Override
  protected String getTargetComponent() {
    return "group";
  }

  @Override
  protected String getRealm(Group entity) {
    return targetRealm;
  }

  @Override
  protected String getConfigPath() {
    return "/groups";
  }

  @Override
  protected UUID getEntityId(Group entity) {
    return entity.getId();
  }

  @Override
  protected GroupRepository getRepository() {
    return groupRepository;
  }

  @Override
  protected GroupMapper getMapper() {
    return groupMapper;
  }

  @Override
  protected String getEntityName() {
    return Group.class.getSimpleName();
  }
}
