package de.civitascore.portal.migration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.datasink.FrostSinkPort;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.service.initializer.FrostSinkPortMigration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the port migration against a real registry.
 *
 * <p>The unit test mocks {@code ModelRegistryGateway}, so it cannot see the step that actually
 * decides whether a Dataset survives the upgrade: Model Forge validates every artifact write
 * against {@code datasink.schema.json}, and that document rejects a member it does not publish. A
 * migration that writes a field the schema does not carry fails on the operator's first start, with
 * every FROST Dataset already in place. That is what this test covers — the write goes through the
 * real facade and the real schema.
 *
 * <p>The migration is an {@code ApplicationRunner} and has already run (over an empty set) when the
 * context came up, so the test seeds its sinks and invokes it, which is also what a restart does.
 */
@DisplayName("FROST sink port migration — against the real model registry")
class FrostSinkPortMigrationIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory factory;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private ModelRegistryGateway modelRegistryGateway;
  @Autowired private FrostSinkPortMigration migration;

  @Test
  @DisplayName("Gives a sink that predates the port the ThingTree port, and the schema accepts it")
  void migratesASinkWithoutAPort() {
    // A FROST sink without a Mapping stores no field of its own. The seed must be what an
    // installation carries today: no port at all.
    DataSink sink = frostSink(Map.of());
    assertThat(configurationOf(sink.getId())).doesNotContainKey("port");

    migration.run(null);

    Map<String, Object> configuration = configurationOf(sink.getId());
    assertThat(configuration).containsEntry("port", FrostSinkPort.THING_TREE.label());
    // The connection type is the host's to supply; without it the stored document does not satisfy
    // the schema, and the write above would have thrown.
    assertThat(configuration).containsEntry("connectionType", "frost");
  }

  @Test
  @DisplayName("Leaves a sink that already carries a port alone")
  void leavesAPortedSinkAlone() {
    DataSink sink = frostSink(Map.of("port", FrostSinkPort.OBSERVATIONS.label()));
    String pinned = dataSinkRepository.findById(sink.getId()).orElseThrow().getConfigurationUrn();

    migration.run(null);

    // The migration runs on every start. A modeller who chose a port must keep it — and the pin
    // must not move either, or the Dataset would look changed on every restart.
    assertThat(dataSinkRepository.findById(sink.getId()).orElseThrow().getConfigurationUrn())
        .isEqualTo(pinned);
    assertThat(configurationOf(sink.getId()))
        .containsEntry("port", FrostSinkPort.OBSERVATIONS.label());
  }

  @Test
  @DisplayName("Runs twice without writing a second version")
  void isIdempotent() {
    DataSink sink = frostSink(Map.of());

    migration.run(null);
    String afterFirst =
        dataSinkRepository.findById(sink.getId()).orElseThrow().getConfigurationUrn();
    migration.run(null);

    assertThat(dataSinkRepository.findById(sink.getId()).orElseThrow().getConfigurationUrn())
        .isEqualTo(afterFirst);
  }

  @Test
  @DisplayName("Leaves a sink of another type alone")
  void leavesAnotherSinkTypeAlone() {
    DataSet dataSet = factory.dataSet();
    Pipeline pipeline = factory.pipeline(dataSet);
    DataSink sink =
        factory.dataSink(
            dataSet, pipeline, candidate -> candidate.setDataSinkType(DataSinkType.POSTGIS));
    DataStructureVersion version =
        factory.attachModel(
            factory.dataStructureVersion(
                factory.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE)),
                b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)),
            factory.dataStructureVersionModel("Roads"));
    sink =
        factory.attachSinkConfiguration(
            sink, Map.of("tableName", "roads", "element", version.getModelUrn()));

    migration.run(null);

    assertThat(configurationOf(sink.getId())).doesNotContainKey("port");
  }

  /** A FROST sink with the given configuration stored through the real registry. */
  private DataSink frostSink(Map<String, Object> configuration) {
    DataSet dataSet = factory.dataSet();
    Pipeline pipeline = factory.pipeline(dataSet);
    DataSink sink = factory.dataSink(dataSet, pipeline);
    return factory.attachSinkConfiguration(sink, configuration);
  }

  private Map<String, Object> configurationOf(UUID dataSinkId) {
    return dataSinkRepository
        .findById(dataSinkId)
        .map(DataSink::getConfigurationUrn)
        .flatMap(modelRegistryGateway::fetchPayload)
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElseThrow(() -> new AssertionError("the sink carries no configuration"));
  }
}
