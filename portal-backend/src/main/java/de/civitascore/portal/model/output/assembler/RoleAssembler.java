package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.output.RoleOutputDTO;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleAssembler implements BaseAssembler<Role, RoleOutputDTO, UUID> {

  private final RoleMapper roleMapper;
  private final PermissionMapper permissionMapper;

  @Override
  public RoleOutputDTO mapToBaseDto(Role entity) {
    RoleOutputDTO output = roleMapper.toOutput(entity);

    if (entity.getPermissions() != null && !entity.getPermissions().isEmpty()) {
      output.setPermissions(
          entity.getPermissions().stream()
              .map(permissionMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Role entity) {
    return (I) roleMapper.toInput(entity);
  }
}
