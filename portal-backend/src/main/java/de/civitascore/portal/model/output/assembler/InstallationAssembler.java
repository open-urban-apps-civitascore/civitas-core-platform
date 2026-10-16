package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.InstallationMapper;
import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Installation} entities to {@link InstallationOutputDTO}.
 * Participates in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class InstallationAssembler
    implements BaseAssembler<Installation, InstallationOutputDTO, UUID> {

  private final InstallationMapper installationMapper;

  /** {@inheritDoc} Delegates to the {@link InstallationMapper}, which maps the lines too. */
  @Override
  public InstallationOutputDTO mapToBaseDto(Installation entity) {
    return installationMapper.toOutput(entity);
  }
}
