package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.UseCaseMapper;
import de.civitascore.portal.model.entity.UseCase;
import de.civitascore.portal.model.output.UseCaseOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link UseCase} entities to {@link UseCaseOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class UseCaseAssembler implements BaseAssembler<UseCase, UseCaseOutputDTO> {

  private final UseCaseMapper useCaseMapper;

  /** {@inheritDoc} Delegates to the {@link UseCaseMapper}, which maps the lines too. */
  @Override
  public UseCaseOutputDTO mapToBaseDto(UseCase entity) {
    return useCaseMapper.toOutput(entity);
  }
}
