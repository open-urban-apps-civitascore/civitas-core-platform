package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.model.input.MappingInputDTO;
import de.civitascore.portal.model.input.MappingNodePosition;
import de.civitascore.portal.model.output.MappingOutputDTO;
import de.civitascore.portal.model.output.MappingSummaryDTO;
import de.civitascore.portal.service.MappingService.MappingSummary;
import de.civitascore.portal.service.MappingService.StoredMapping;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Converts between the typed Mapping DTOs and the Mapping document that Model Forge stores. */
@Component
@RequiredArgsConstructor
public class MappingAssembler {

  private static final TypeReference<Map<String, Object>> DOCUMENT = new TypeReference<>() {};
  private static final TypeReference<Map<String, MappingNodePosition>> POSITIONS =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  /** The Mapping document of the input, without the fields the input leaves out. */
  public Map<String, Object> toDocument(MappingInputDTO input) {
    return objectMapper.convertValue(input, DOCUMENT);
  }

  public MappingOutputDTO toOutput(StoredMapping stored) {
    Map<String, Object> document = stored.document();
    return new MappingOutputDTO(
        stored.mappingId(),
        stored.pin().logicalUrn(),
        stored.pin().versionedUrn(),
        stored.pin().version(),
        stringOf(document, "title"),
        stringOf(document, "description"),
        stringOf(document, "source"),
        stringOf(document, "target"),
        convert(document.get("fields"), DOCUMENT),
        convert(document.get("positions"), POSITIONS));
  }

  public MappingSummaryDTO toSummary(MappingSummary summary) {
    return new MappingSummaryDTO(summary.mappingId(), summary.logicalUrn(), summary.title());
  }

  private <T> T convert(Object value, TypeReference<T> type) {
    return value == null ? null : objectMapper.convertValue(value, type);
  }

  private static String stringOf(Map<String, Object> document, String key) {
    return document.get(key) instanceof String value ? value : null;
  }
}
