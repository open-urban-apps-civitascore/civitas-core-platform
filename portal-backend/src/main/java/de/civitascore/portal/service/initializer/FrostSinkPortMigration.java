package de.civitascore.portal.service.initializer;

import de.civitascore.portal.model.datasink.FrostSinkPort;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives every FROST DataSink that predates the port a {@code ThingTree} port.
 *
 * <p>The write logic of the sink used to follow from the Mapping. It is a field now, and a sink
 * without one does not publish — so an untouched Dataset would stop at its next publication. Every
 * Pipeline that exists today uses the logic the {@code ThingTree} port applies, which makes the
 * assignment loss-free. The modeller can change it afterwards on the sink node.
 *
 * <p>This is not a Flyway migration, because the configuration is not a column: it is a registry
 * artifact behind the Model Forge facade, and the host reads model content only through that
 * facade. A SQL migration would have to read across the schema boundary.
 *
 * <p>It runs on every start and is idempotent: a sink that carries a port is left alone, so a
 * modeller's later choice is never overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(FrostSinkPortMigration.ORDER)
public class FrostSinkPortMigration implements ApplicationRunner {

  /** After the permission and role initializers, which the rest of the application depends on. */
  static final int ORDER = 100;

  private static final String PORT_FIELD = "port";
  private static final String CONNECTION_TYPE_FIELD = "connectionType";

  private final DataSinkRepository dataSinkRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<DataSink> sinks =
        dataSinkRepository.findAll().stream().filter(FrostSinkPortMigration::isFrost).toList();
    int migrated = 0;
    for (DataSink sink : sinks) {
      if (migrate(sink)) {
        migrated++;
      }
    }
    if (migrated > 0) {
      log.info(
          "Gave {} FROST data sink(s) the {} port", migrated, FrostSinkPort.THING_TREE.label());
    }
  }

  private static boolean isFrost(DataSink sink) {
    return sink.getDataSinkType() == DataSinkType.FROST;
  }

  /** Writes the port onto one sink. Answers whether it changed anything. */
  private boolean migrate(DataSink sink) {
    Map<String, Object> configuration = currentConfiguration(sink);
    if (configuration.get(PORT_FIELD) instanceof String port && !port.isBlank()) {
      return false;
    }

    Map<String, Object> payload = new LinkedHashMap<>(configuration);
    payload.put(PORT_FIELD, FrostSinkPort.THING_TREE.label());
    // Model Forge stamps the artifact's self-description, but the connection type is the host's to
    // supply — a configuration stored without it does not satisfy the datasink schema.
    payload.putIfAbsent(CONNECTION_TYPE_FIELD, DataSinkType.FROST.name().toLowerCase(Locale.ROOT));

    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DATA_SINK,
            Optional.ofNullable(sink.getConfigurationLogicalUrn()),
            DataSinkType.FROST.name(),
            payload,
            null);
    if (sink.getConfigurationLogicalUrn() == null) {
      sink.setConfigurationLogicalUrn(pin.logicalUrn());
    }
    sink.setConfigurationUrn(pin.versionedUrn());
    dataSinkRepository.save(sink);
    return true;
  }

  /**
   * The stored configuration, or an empty one. A sink that carries no artifact yet is migrated too:
   * without a port it would not publish, and the port is what the Pipeline behind it already does.
   */
  private Map<String, Object> currentConfiguration(DataSink sink) {
    if (sink.getConfigurationUrn() == null) {
      return Map.of();
    }
    return modelRegistryGateway
        .fetchPayload(sink.getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElse(Map.of());
  }
}
