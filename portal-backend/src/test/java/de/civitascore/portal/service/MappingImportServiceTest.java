package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.input.MappingImportInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.service.MappingImportService.MappingResolution;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link MappingImportService}: the create / reuse / conflict turnstile a bundled
 * mapping goes through, and the guard on its authored URN. Registry and mapping service are mocked
 * — these tests pin the bundle semantics, not the storage.
 */
@ExtendWith(MockitoExtension.class)
class MappingImportServiceTest {

  private static final String LOGICAL_URN =
      "urn:core:standard:openurbanapps:mapping:mobility:zaehlungtoobservation:default";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";
  private static final Map<String, Object> DOCUMENT =
      Map.of("fields", Map.of("$.result", Map.of("op", "toInt", "input", "$.vehicleCount")));

  @Mock private MappingService mappingService;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @InjectMocks private MappingImportService mappingImportService;

  @Captor private ArgumentCaptor<String> urnCaptor;

  private static MappingImportInputDTO input(String mappingUrn) {
    MappingImportInputDTO dto = new MappingImportInputDTO();
    dto.setName("Zählung → Observation");
    dto.setMappingUrn(mappingUrn);
    dto.setDocument(DOCUMENT);
    return dto;
  }

  @Test
  void importOrReuse_whenUrnUnknown_storesAtTheAuthoredUrnAndReportsCreated() {
    when(modelRegistryGateway.isMappingUrn(LOGICAL_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(LOGICAL_URN)).thenReturn(LOGICAL_URN);
    when(mappingService.exists(LOGICAL_URN)).thenReturn(false);
    when(mappingService.store(eq(LOGICAL_URN), any()))
        .thenReturn(new ModelPin(LOGICAL_URN, VERSIONED_URN, "1.0.0"));

    MappingResolution resolution = mappingImportService.importOrReuse(input(LOGICAL_URN));

    assertThat(resolution.reused()).isFalse();
    assertThat(resolution.logicalUrn()).isEqualTo(LOGICAL_URN);
    // The authored identity is what reaches the registry — anything else and a re-install would
    // mint a duplicate instead of resolving to the same mapping.
    verify(mappingService).store(urnCaptor.capture(), eq(DOCUMENT));
    assertThat(urnCaptor.getValue()).isEqualTo(LOGICAL_URN);
  }

  @Test
  void importOrReuse_whenInstalledWithIdenticalContent_reusesWithoutWriting() {
    when(modelRegistryGateway.isMappingUrn(LOGICAL_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(LOGICAL_URN)).thenReturn(LOGICAL_URN);
    when(mappingService.exists(LOGICAL_URN)).thenReturn(true);
    when(mappingService.isUnchanged(LOGICAL_URN, DOCUMENT)).thenReturn(true);
    when(mappingService.currentVersionedUrn(LOGICAL_URN))
        .thenReturn(Optional.of(LOGICAL_URN + ":1.0.0"));

    MappingResolution resolution = mappingImportService.importOrReuse(input(LOGICAL_URN));

    assertThat(resolution.reused()).isTrue();
    assertThat(resolution.logicalUrn()).isEqualTo(LOGICAL_URN);
    verify(mappingService, never()).store(any(), any());
  }

  @Test
  void importOrReuse_whenInstalledWithDifferentContent_throwsConflict() {
    when(modelRegistryGateway.isMappingUrn(LOGICAL_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(LOGICAL_URN)).thenReturn(LOGICAL_URN);
    when(mappingService.exists(LOGICAL_URN)).thenReturn(true);
    when(mappingService.isUnchanged(LOGICAL_URN, DOCUMENT)).thenReturn(false);

    assertThatThrownBy(() -> mappingImportService.importOrReuse(input(LOGICAL_URN)))
        .isInstanceOf(UniqueConstraintViolationException.class)
        .hasMessageContaining("already installed with different content");
    verify(mappingService, never()).store(any(), any());
  }

  @Test
  void importOrReuse_whenUrnIsNotAMappingUrn_rejects() {
    String structureUrn = "urn:core:standard:openurbanapps:datastructure:mobility:zaehlung:default";
    when(modelRegistryGateway.isMappingUrn(structureUrn)).thenReturn(false);

    assertThatThrownBy(() -> mappingImportService.importOrReuse(input(structureUrn)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("mappingUrn");
    verify(mappingService, never()).store(any(), any());
  }

  /** A caller who pasted a versioned URN is handled, not rejected — Model Forge owns versions. */
  @Test
  void importOrReuse_whenUrnIsVersioned_normalisesToLogicalBeforeStoring() {
    when(modelRegistryGateway.isMappingUrn(VERSIONED_URN)).thenReturn(true);
    when(modelRegistryGateway.logicalUrn(VERSIONED_URN)).thenReturn(LOGICAL_URN);
    when(mappingService.exists(LOGICAL_URN)).thenReturn(false);
    when(mappingService.store(eq(LOGICAL_URN), any()))
        .thenReturn(new ModelPin(LOGICAL_URN, VERSIONED_URN, "1.0.0"));

    MappingResolution resolution = mappingImportService.importOrReuse(input(VERSIONED_URN));

    assertThat(resolution.logicalUrn()).isEqualTo(LOGICAL_URN);
    verify(mappingService).store(LOGICAL_URN, DOCUMENT);
  }
}
