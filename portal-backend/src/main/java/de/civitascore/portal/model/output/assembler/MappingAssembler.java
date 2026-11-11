package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.model.input.MappingInputDTO;
import de.civitascore.portal.model.output.MappingOutputDTO;
import de.civitascore.portal.model.output.MappingSummaryDTO;
import de.civitascore.portal.service.MappingService.MappingSummary;
import de.civitascore.portal.service.MappingService.StoredMapping;
import org.springframework.stereotype.Component;

@Component
public class MappingAssembler {

  public MappingOutputDTO toOutput(StoredMapping stored) {
    MappingInputDTO mapping = stored.mapping();
    return new MappingOutputDTO(
        stored.pin().id(),
        stored.pin().logicalUrn(),
        stored.pin().versionedUrn(),
        stored.pin().version(),
        mapping.getTitle(),
        mapping.getDescription(),
        mapping.getSource(),
        mapping.getTarget(),
        mapping.getFields(),
        mapping.getPositions());
  }

  public MappingSummaryDTO toSummary(MappingSummary summary) {
    return new MappingSummaryDTO(summary.mappingId(), summary.logicalUrn(), summary.title());
  }
}
