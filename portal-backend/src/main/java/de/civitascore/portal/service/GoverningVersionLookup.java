package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers which version record governs a pinned model, for every caller that has to ask.
 *
 * <p>Found by structure and major, not by the pinned string: editing a draft version's model
 * advances its pin inside its own major, so the string a flow recorded stops naming any record
 * while the record that owns it is still there. A later major is a record of its own and stays out
 * of reach. A model the platform keeps no record of — an element, a mapping, a sink configuration —
 * is governed by nothing.
 */
@Component
@RequiredArgsConstructor
public class GoverningVersionLookup {

  /** How many structures one lookup may ask for, bounded by what a statement can carry. */
  private static final int BATCH_SIZE = 500;

  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** The record governing one pinned model, empty when the platform keeps none. */
  public Optional<DataStructureVersion> governing(String pinnedUrn) {
    if (pinnedUrn == null || pinnedUrn.isBlank()) {
      return Optional.empty();
    }
    return governingAll(List.of(pinnedUrn)).getOrDefault(pinnedUrn, List.of()).stream().findFirst();
  }

  /**
   * The records governing each of these pinned models, keyed by the pin. Several records can pin
   * one model, because the registry returns the version it already holds for identical content.
   */
  public Map<String, List<DataStructureVersion>> governingAll(Collection<String> pinnedUrns) {
    if (pinnedUrns == null || pinnedUrns.isEmpty()) {
      return Map.of();
    }
    Map<String, UUID> structureByPin = new HashMap<>();
    for (String pin : pinnedUrns) {
      modelRegistryGateway.modelIdOf(pin).ifPresent(id -> structureByPin.put(pin, id));
    }
    if (structureByPin.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<DataStructureVersion>> byStructure =
        recordsOfStructures(List.copyOf(new LinkedHashSet<>(structureByPin.values()))).stream()
            .collect(Collectors.groupingBy(version -> version.getDataStructure().getId()));

    Map<String, List<DataStructureVersion>> governing = new HashMap<>();
    structureByPin.forEach(
        (pin, structureId) -> {
          List<DataStructureVersion> candidates = byStructure.get(structureId);
          if (candidates == null) {
            return;
          }
          String major = majorOf(pin, modelRegistryGateway.logicalUrn(pin));
          List<DataStructureVersion> owning =
              major == null
                  ? candidates
                  : candidates.stream().filter(version -> major.equals(majorOf(version))).toList();
          if (!owning.isEmpty()) {
            governing.put(pin, owning);
          }
        });
    return governing;
  }

  private List<DataStructureVersion> recordsOfStructures(List<UUID> structureIds) {
    if (structureIds.size() <= BATCH_SIZE) {
      return dataStructureVersionRepository.findAllByDataStructure_IdIn(structureIds);
    }
    List<DataStructureVersion> records = new ArrayList<>();
    for (int from = 0; from < structureIds.size(); from += BATCH_SIZE) {
      records.addAll(
          dataStructureVersionRepository.findAllByDataStructure_IdIn(
              structureIds.subList(from, Math.min(from + BATCH_SIZE, structureIds.size()))));
    }
    return records;
  }

  /** The major a pin names, or null when it names no version. */
  private static String majorOf(String pinnedUrn, String logicalUrn) {
    if (logicalUrn == null || pinnedUrn.length() <= logicalUrn.length() + 1) {
      return null;
    }
    return majorOf(pinnedUrn.substring(logicalUrn.length() + 1));
  }

  private static String majorOf(DataStructureVersion version) {
    return majorOf(version.getVersion());
  }

  private static String majorOf(String version) {
    if (version == null || version.isBlank()) {
      return null;
    }
    int dot = version.indexOf('.');
    return dot < 0 ? version : version.substring(0, dot);
  }
}
