package de.civitascore.portal.service.initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FROST Sink Port Migration Tests")
class FrostSinkPortMigrationTest {

  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  @InjectMocks private FrostSinkPortMigration migration;

  @Test
  @DisplayName("Should give a FROST sink without a port the ThingTree port")
  void shouldGiveAFrostSinkWithoutAPortTheThingTreePort() {
    DataSink sink = frostSink("urn:sink:1", "urn:sink:1:2");
    when(dataSinkRepository.findAll()).thenReturn(List.of(sink));
    when(modelRegistryGateway.fetchPayload("urn:sink:1:2"))
        .thenReturn(
            Optional.of(
                new ModelRegistryGateway.RegistryDocument(
                    Map.of("connectionType", "frost", "element", "urn:element:9"), Map.of())));
    when(modelRegistryGateway.storePayload(any(), any(), any(), any(), any()))
        .thenReturn(new ModelRegistryGateway.ModelPin("urn:sink:1", "urn:sink:1:3", "3"));

    migration.run(null);

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.captor();
    verify(modelRegistryGateway)
        .storePayload(
            eq(PayloadKind.DATA_SINK),
            eq(Optional.of("urn:sink:1")),
            any(),
            payload.capture(),
            any());
    assertEquals("ThingTree", payload.getValue().get("port"));
    // The rest of the configuration survives: the migration adds a field, it does not rewrite one.
    assertEquals("urn:element:9", payload.getValue().get("element"));
    assertEquals("urn:sink:1:3", sink.getConfigurationUrn(), "the sink points at the new version");
  }

  @Test
  @DisplayName("Should leave a sink that already carries a port alone")
  void shouldLeaveASinkThatAlreadyCarriesAPortAlone() {
    DataSink sink = frostSink("urn:sink:1", "urn:sink:1:2");
    when(dataSinkRepository.findAll()).thenReturn(List.of(sink));
    when(modelRegistryGateway.fetchPayload("urn:sink:1:2"))
        .thenReturn(
            Optional.of(
                new ModelRegistryGateway.RegistryDocument(
                    Map.of("connectionType", "frost", "port", "Observations"), Map.of())));

    migration.run(null);

    // The migration runs on every start. Overwriting here would undo the modeller's own choice.
    verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any());
    assertEquals("urn:sink:1:2", sink.getConfigurationUrn());
  }

  @Test
  @DisplayName("Should leave a sink of another type alone")
  void shouldLeaveASinkOfAnotherTypeAlone() {
    DataSink postgis = new DataSink();
    postgis.setDataSinkType(DataSinkType.POSTGIS);
    when(dataSinkRepository.findAll()).thenReturn(List.of(postgis));

    migration.run(null);

    verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should migrate a FROST sink that carries no configuration artifact yet")
  void shouldMigrateAFrostSinkThatCarriesNoConfigurationArtifactYet() {
    DataSink sink = frostSink(null, null);
    when(dataSinkRepository.findAll()).thenReturn(List.of(sink));
    when(modelRegistryGateway.storePayload(any(), any(), any(), any(), any()))
        .thenReturn(new ModelRegistryGateway.ModelPin("urn:sink:7", "urn:sink:7:1", "1"));

    migration.run(null);

    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.captor();
    verify(modelRegistryGateway)
        .storePayload(
            eq(PayloadKind.DATA_SINK), eq(Optional.empty()), any(), payload.capture(), any());
    assertEquals("ThingTree", payload.getValue().get("port"));
    // The host supplies the connection type; a payload without it does not satisfy the schema.
    assertEquals("frost", payload.getValue().get("connectionType"));
    assertEquals("urn:sink:7", sink.getConfigurationLogicalUrn());
  }

  private static DataSink frostSink(String logicalUrn, String versionedUrn) {
    DataSink sink = new DataSink();
    sink.setDataSinkType(DataSinkType.FROST);
    sink.setConfigurationLogicalUrn(logicalUrn);
    sink.setConfigurationUrn(versionedUrn);
    return sink;
  }
}
