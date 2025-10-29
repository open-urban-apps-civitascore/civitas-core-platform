package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.output.RoleOutputDTO;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Minimal Role assembler using your mapper + basic enrichments. */
@Component
@RequiredArgsConstructor
public class RoleAssembler implements EntityAssembler<Role, RoleOutputDTO, String> {

  private final RoleMapper roleMapper;
  private final PermissionMapper permissionMapper;

  @Override
  public RoleOutputDTO mapToBaseDto(Role entity) {
    RoleOutputDTO output = roleMapper.toOutput(entity);

    // Map permissions
    if (entity.getPermissions() != null && !entity.getPermissions().isEmpty()) {
      output.setPermissions(
          entity.getPermissions().stream()
              .map(permissionMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return output;
  }
}
