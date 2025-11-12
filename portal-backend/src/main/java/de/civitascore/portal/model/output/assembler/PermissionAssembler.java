package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionAssembler implements BaseAssembler<Permission, PermissionOutputDTO, String> {

  private final PermissionMapper permissionMapper;

  @Override
  public PermissionOutputDTO mapToBaseDto(Permission entity) {
    return permissionMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Permission entity) {
    return (I) permissionMapper.toInput(entity);
  }
}
