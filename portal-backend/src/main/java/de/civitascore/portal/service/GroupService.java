package de.civitascore.portal.service;

import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService extends BaseService<Group, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final AssignmentFactory assignmentFactory;

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getContactUserId() != null) {
      entity.setContactUser(userService.findByIdOrThrow(input.getContactUserId()));
    } else {
      entity.setContactUser(null);
    }

    // TODO: implement in V2.1
    // if (input.getParentGroupId() != null) {
    //   entity.setParentGroup(findByIdOrThrow(input.getParentGroupId()));
    // } else {
    //   entity.setParentGroup(null);
    // }

    // For collections, use findAllById for efficient batch loading
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

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * Group along with contactUser, parentGroup, members and roles in a single JOIN query
   */
  @Override
  public Optional<Group> findById(UUID id) {
    Optional<Group> entity = groupRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }
}
