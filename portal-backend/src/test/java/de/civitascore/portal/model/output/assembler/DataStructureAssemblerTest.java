package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataStructureOutputDTO;
import de.civitascore.portal.model.output.summary.DataStructureVersionUsageSummaryDTO;
import de.civitascore.portal.service.ArtifactUsageLookup;
import de.civitascore.portal.service.ArtifactUsageLookup.ArtifactUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.DataStructureUsage;
import de.civitascore.portal.service.ArtifactUsageLookup.ReferrerKind;
import de.civitascore.portal.service.ArtifactUsageLookup.ReleasedReferrer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataStructureAssembler")
class DataStructureAssemblerTest {

  @Mock private DataStructureMapper dataStructureMapper;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;
  @Mock private ArtifactUsageLookup artifactUsageLookup;

  @Test
  @DisplayName("Each version row carries its own usage and the structure carries its own")
  void enrichDto_setsUsagePerVersionRowAndOnStructure() {
    DataStructureVersion releasedUse = versionWithRow();
    DataStructureVersion sharingLine = versionWithRow();
    DataStructure structure = new DataStructure();
    structure.setDataStructureVersions(Set.of(releasedUse, sharingLine));
    ArtifactUsage released =
        new ArtifactUsage(
            true, List.of(), List.of(new ReleasedReferrer(ReferrerKind.DATA_SOURCE, "ref")));
    when(artifactUsageLookup.ofStructureAndVersions(structure))
        .thenReturn(
            new DataStructureUsage(
                released,
                Map.of(
                    releasedUse.getId(),
                    released,
                    sharingLine.getId(),
                    new ArtifactUsage(true, List.of("pin"), List.of()))));

    DataStructureOutputDTO dto =
        new DataStructureAssembler(
                dataStructureMapper, dataStructureVersionMapper, artifactUsageLookup)
            .enrichDto(new DataStructureOutputDTO(), structure);

    assertThat(dto.getDataStructureVersions())
        .extracting(
            DataStructureVersionUsageSummaryDTO::getId,
            DataStructureVersionUsageSummaryDTO::isInUse,
            DataStructureVersionUsageSummaryDTO::isInUseByReleased)
        .containsExactlyInAnyOrder(
            tuple(releasedUse.getId(), true, true), tuple(sharingLine.getId(), true, false));
    assertThat(dto.isInUse()).isTrue();
    assertThat(dto.isInUseByReleased()).isTrue();
  }

  @Test
  @DisplayName("The structure is not in use when only its versions hold each other")
  void enrichDto_takesTheStructureFlagFromTheStructureUsage() {
    DataStructureVersion first = versionWithRow();
    DataStructureVersion second = versionWithRow();
    DataStructure structure = new DataStructure();
    structure.setDataStructureVersions(Set.of(first, second));
    when(artifactUsageLookup.ofStructureAndVersions(structure))
        .thenReturn(
            new DataStructureUsage(
                new ArtifactUsage(false, List.of(), List.of()),
                Map.of(
                    first.getId(),
                    new ArtifactUsage(true, List.of("second pin"), List.of()),
                    second.getId(),
                    new ArtifactUsage(true, List.of("first pin"), List.of()))));

    DataStructureOutputDTO dto =
        new DataStructureAssembler(
                dataStructureMapper, dataStructureVersionMapper, artifactUsageLookup)
            .enrichDto(new DataStructureOutputDTO(), structure);

    assertThat(dto.getDataStructureVersions())
        .allMatch(DataStructureVersionUsageSummaryDTO::isInUse);
    assertThat(dto.isInUse()).isFalse();
  }

  private DataStructureVersion versionWithRow() {
    DataStructureVersion version = new DataStructureVersion();
    version.setId(UUID.randomUUID());
    DataStructureVersionUsageSummaryDTO row = new DataStructureVersionUsageSummaryDTO();
    row.setId(version.getId());
    when(dataStructureVersionMapper.toUsageSummary(version)).thenReturn(row);
    return version;
  }
}
