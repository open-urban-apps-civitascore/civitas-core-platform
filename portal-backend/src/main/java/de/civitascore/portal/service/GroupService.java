package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService extends BaseService<Group, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final AssignmentBuilderService assignmentBuilderService;
  private final ObjectMapper objectMapper;

  @Override
  protected Group preSave(Group entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Group entity) {
    groupRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    Group.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getContactUserId() != null) {
      entity.setContactUser(userService.findByIdOrThrow(input.getContactUserId()));
    } else {
      entity.setContactUser(null);
    }

    if (input.getParentGroupId() != null) {
      entity.setParentGroup(findByIdOrThrow(input.getParentGroupId()));
    } else {
      entity.setParentGroup(null);
    }

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
  public Group replaceAssignments(UUID groupId, List<AssignmentGroupInputDTO> assignmentInputs) {
    Group group = findByIdOrThrow(groupId);

    Set<Assignment> newAssignments =
        assignmentInputs.stream().map(assignmentBuilderService::build).collect(Collectors.toSet());

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

  @Override
  protected GroupInputDTO preProcessUpdateInput(GroupInputDTO input, Group existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", existingEntity.getId(), "Name cannot be null or blank");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}
