package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.UseCase;
import de.civitascore.portal.model.entity.UseCaseArtifact;
import de.civitascore.portal.model.output.UseCaseArtifactOutputDTO;
import de.civitascore.portal.model.output.UseCaseOutputDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper from a {@link UseCase} and its {@link UseCaseArtifact} lines to the output DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UseCaseMapper {

  UseCaseOutputDTO toOutput(UseCase entity);

  UseCaseArtifactOutputDTO toOutput(UseCaseArtifact line);
}
