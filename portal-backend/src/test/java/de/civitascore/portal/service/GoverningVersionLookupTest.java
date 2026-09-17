package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("which version record governs a pinned model")
class GoverningVersionLookupTest {

  private static final String LOGICAL =
      "urn:core:platform:civitas:element:common:Sensor:bbbbbbbbbb";

  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  private GoverningVersionLookup lookup;

  @BeforeEach
  void setUp() {
    lookup = new GoverningVersionLookup(dataStructureVersionRepository, modelRegistryGateway);
    lenient()
        .when(modelRegistryGateway.logicalUrn(anyString()))
        .thenAnswer(
            call -> {
              String urn = call.getArgument(0);
              return urn.substring(0, urn.lastIndexOf(':'));
            });
  }

  private void records(DataStructureVersion... versions) {
    when(dataStructureVersionRepository.findAllByDataStructure_ModelLogicalUrnIn(any()))
        .thenReturn(List.of(versions));
  }

  private static DataStructureVersion record(String version, DataStructureVersionStatus status) {
    DataStructure parent = new DataStructure();
    parent.setId(UUID.randomUUID());
    parent.setDataStructureStatus(DataStructureStatus.AVAILABLE);
    parent.setModelLogicalUrn(LOGICAL);
    DataStructureVersion record = new DataStructureVersion();
    record.setId(UUID.randomUUID());
    record.setModelUrn(LOGICAL + ":" + version);
    record.setVersion(version);
    record.setDataStructure(parent);
    record.setDataStructureVersionStatus(status);
    return record;
  }

  @Test
  @DisplayName("a pin still resolves after the draft it names advanced inside its major")
  void aPinResolvesAfterItsVersionAdvanced() {
    records(record("1.1.0", DataStructureVersionStatus.DRAFT));

    assertThat(lookup.governing(LOGICAL + ":1.0.0"))
        .as("editing the draft moved its pin, but the record that owns the major is the same")
        .hasValueSatisfying(found -> assertThat(found.getVersion()).isEqualTo("1.1.0"));
  }

  @Test
  @DisplayName("a later major is a record of its own and never answers for an earlier pin")
  void aLaterMajorNeverAnswersForAnEarlierPin() {
    records(
        record("1.1.0", DataStructureVersionStatus.DRAFT),
        record("2.0.0", DataStructureVersionStatus.AVAILABLE));

    assertThat(lookup.governing(LOGICAL + ":1.0.0"))
        .hasValueSatisfying(found -> assertThat(found.getVersion()).isEqualTo("1.1.0"));
    assertThat(lookup.governing(LOGICAL + ":2.0.0"))
        .hasValueSatisfying(found -> assertThat(found.getVersion()).isEqualTo("2.0.0"));
  }

  @Test
  @DisplayName("a model the platform keeps no record of is governed by nothing")
  void anUnrecordedModelIsGovernedByNothing() {
    records();

    assertThat(lookup.governing(LOGICAL + ":1.0.0")).isEmpty();
  }

  @Test
  @DisplayName("a pin naming no version is governed by whatever the structure holds")
  void anUnversionedPinTakesWhateverIsHeld() {
    records(record("1.0.0", DataStructureVersionStatus.AVAILABLE));
    when(modelRegistryGateway.logicalUrn(LOGICAL)).thenReturn(LOGICAL);

    assertThat(lookup.governing(LOGICAL)).isPresent();
  }
}
