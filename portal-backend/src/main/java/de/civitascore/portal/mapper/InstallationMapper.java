package de.civitascore.portal.mapper;

import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.InstalledArtifactOutputDTO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper from an {@link Installation} and its {@link InstalledArtifact} lines to the
 * output DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstallationMapper {

  InstallationOutputDTO toOutput(Installation entity);

  InstalledArtifactOutputDTO toOutput(InstalledArtifact line);
}
