package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Minimal Permission assembler using your mapper + basic enrichments. */
@Component
@RequiredArgsConstructor
public class PermissionAssembler
    implements EntityAssembler<Permission, PermissionOutputDTO, String> {

  private final PermissionMapper permissionMapper;

  @Override
  public PermissionOutputDTO mapToBaseDto(Permission entity) {
    return permissionMapper.toOutput(entity); // Your mapper's basic field mapping
  }
}
