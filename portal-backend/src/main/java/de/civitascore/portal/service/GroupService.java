package de.civitascore.portal.service;

import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupService extends TenantAwareService<Group, String, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final RoleService roleService;

  @Override
  protected Group preSave(Group entity) {
    // Validate unique constraint: title + tenant_id
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(Group entity) {
    groupRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              // If it's an update and the existing entity is the same, skip validation
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "Group", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    // Set contact user
    if (Objects.nonNull(input.getContactUserId())) {
      entity.setContactUser(userService.findById(input.getContactUserId()));
    }

    // Set parent group
    if (Objects.nonNull(input.getParentGroupId())) {
      groupRepository.findById(input.getParentGroupId()).ifPresent(entity::setParentGroup);
    }

    // Set members
    if (Objects.nonNull(input.getMemberIds())) {
      entity.setMembers(new HashSet<>());
      if (!input.getMemberIds().isEmpty()) {
        input
            .getMemberIds()
            .forEach(
                userId ->
                    userService
                        .getRepository()
                        .findById(userId)
                        .ifPresent(entity.getMembers()::add));
      }
    }

    // Set system roles
    if (Objects.nonNull(input.getSystemRoleIds())) {
      entity.setSystemRoles(new HashSet<>());
      if (!input.getSystemRoleIds().isEmpty()) {
        input
            .getSystemRoleIds()
            .forEach(
                roleId ->
                    roleService
                        .getRepository()
                        .findById(roleId)
                        .ifPresent(entity.getSystemRoles()::add));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected TenantAwareRepository<Group, String> getRepository() {
    return groupRepository;
  }

  @Override
  protected GroupMapper getMapper() {
    return groupMapper;
  }

  @Override
  protected String getEntityName() {
    return "Group";
  }
}
