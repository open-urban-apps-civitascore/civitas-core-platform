package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.output.PermissionOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Permission} entities to {@link PermissionOutputDTO}. Participates
 * in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class PermissionAssembler implements BaseAssembler<Permission, PermissionOutputDTO, UUID> {

  private final PermissionMapper permissionMapper;

  /** {@inheritDoc} Delegates to the {@link PermissionMapper} for basic field mapping. */
  @Override
  public PermissionOutputDTO mapToBaseDto(Permission entity) {
    return permissionMapper.toOutput(entity);
  }
}
