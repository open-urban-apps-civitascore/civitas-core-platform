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
  @DisplayName("Each version row carries its own usage and the structure carries their union")
  void enrichDto_setsUsagePerVersionRowAndOnStructure() {
    DataStructureVersion releasedUse = versionWithRow();
    DataStructureVersion draftUse = versionWithRow();
    DataStructure structure = new DataStructure();
    structure.setDataStructureVersions(Set.of(releasedUse, draftUse));
    when(artifactUsageLookup.ofEachVersion(structure))
        .thenReturn(
            Map.of(
                releasedUse.getId(),
                new ArtifactUsage(
                    true, List.of(new ReleasedReferrer(ReferrerKind.DATA_SOURCE, "ref"))),
                draftUse.getId(),
                new ArtifactUsage(true, List.of())));

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
            tuple(releasedUse.getId(), true, true),
            tuple(draftUse.getId(), true, false));
    assertThat(dto.isInUse()).isTrue();
    assertThat(dto.isInUseByReleased()).isTrue();
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
