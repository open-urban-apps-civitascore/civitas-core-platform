package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.model.entity.Installation;
import de.civitascore.portal.model.entity.InstalledArtifact;
import de.civitascore.portal.model.output.InstallationOutputDTO;
import de.civitascore.portal.model.output.InstalledArtifactOutputDTO;
import org.springframework.stereotype.Component;

/** Renders an {@link Installation} with its artifact lines. */
@Component
public class InstallationAssembler {

  public InstallationOutputDTO toOutput(Installation installation) {
    InstallationOutputDTO output = new InstallationOutputDTO();
    output.setId(installation.getId());
    output.setPackageId(installation.getPackageId());
    output.setPackageVersion(installation.getPackageVersion());
    output.setDataSetId(installation.getDataSetId());
    output.setDataSetName(installation.getDataSetName());
    output.setCreatedAt(installation.getCreatedAt());
    output.setCreatedBy(installation.getCreatedBy());
    installation.getArtifacts().forEach(line -> output.getArtifacts().add(toOutput(line)));
    return output;
  }

  private InstalledArtifactOutputDTO toOutput(InstalledArtifact line) {
    InstalledArtifactOutputDTO output = new InstalledArtifactOutputDTO();
    output.setId(line.getId());
    output.setArtifactType(line.getArtifactType());
    output.setName(line.getName());
    output.setShellId(line.getShellId());
    output.setUrn(line.getUrn());
    output.setVersionedUrn(line.getVersionedUrn());
    output.setOrigin(line.getOrigin());
    output.setAction(line.getAction());
    return output;
  }
}
