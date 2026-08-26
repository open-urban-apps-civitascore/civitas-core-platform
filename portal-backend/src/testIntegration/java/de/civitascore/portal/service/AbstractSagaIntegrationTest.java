package de.civitascore.portal.service;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.util.TestContainerImages;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Abstract base for saga integration tests. Owns the shared container set (postgis, frost, kafka,
 * mosquitto) on a common Docker network. Containers are started once and shared across all
 * subclasses; each subclass uses {@code @DirtiesContext(AFTER_CLASS)} to rebuild its Spring context
 * while the containers stay alive.
 *
 * <p>Subclasses that need additional containers (e.g. a datasource PostgreSQL for E2E tests) can
 * declare them as their own static fields on the shared {@link #sagaNetwork}.
 */
@Tag("saga")
@Slf4j
abstract class AbstractSagaIntegrationTest extends BaseKeycloakIntegrationTest {

  // ---------------------------------------------------------------------------
  // Shared containers — started once, reused by all saga test subclasses
  // ---------------------------------------------------------------------------

  static final Network sagaNetwork = Network.newNetwork();

  static final GenericContainer<?> postgis = createPostgis(sagaNetwork);
  static final GenericContainer<?> frost = createFrost(sagaNetwork, postgis);
  static final KafkaContainer kafka = createKafka();
  static final GenericContainer<?> mosquitto = createMosquitto(sagaNetwork);

  static final String frostExternalUrl;

  static {
    postgis.start();
    frost.start();
    kafka.start();
    mosquitto.start();

    frostExternalUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";

    log.info("Saga containers started:");
    log.info("  FROST at {}", frostExternalUrl);
    log.info("  Kafka at {}", kafka.getBootstrapServers());
  }

  // ---------------------------------------------------------------------------
  // Container factory methods
  // ---------------------------------------------------------------------------

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createPostgis(Network network) {
    return new GenericContainer<>(TestContainerImages.POSTGIS)
        .withNetwork(network)
        .withNetworkAliases("database")
        .withEnv("POSTGRES_DB", "sensorthings")
        .withEnv("POSTGRES_USER", "sensorthings")
        .withEnv("POSTGRES_PASSWORD", "ChangeMe")
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
  }

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createFrost(Network network, GenericContainer<?> postgis) {
    return new GenericContainer<>(TestContainerImages.FROST)
        .withNetwork(network)
        .withNetworkAliases("frost-server")
        .withExposedPorts(8080)
        .dependsOn(postgis)
        .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
        .withEnv("plugins_projects_enable", "true")
        .withEnv("plugins_projects_enableDefaultRules", "false")
        .withEnv("plugins_modelLoader_enable", "true")
        .withEnv("plugins_multiDatastream_enable", "false")
        .withEnv("plugins_actuation_enable", "false")
        .withEnv("persistence_db_driver", "org.postgresql.Driver")
        .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
        .withEnv("persistence_db_username", "sensorthings")
        .withEnv("persistence_db_password", "ChangeMe")
        .withEnv("persistence_autoUpdateDatabase", "true")
        .waitingFor(
            Wait.forHttp("/FROST-Server/v1.1/Projects")
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
  }

  protected static KafkaContainer createKafka() {
    return new KafkaContainer(TestContainerImages.KAFKA);
  }

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createMosquitto(Network network) {
    return new GenericContainer<>(TestContainerImages.MOSQUITTO)
        .withNetwork(network)
        .withNetworkAliases("mqtt-broker")
        .withExposedPorts(1883)
        .withCopyFileToContainer(
            MountableFile.forHostPath(
                Path.of("src/testIntegration/resources/mosquitto/mosquitto.conf").toAbsolutePath()),
            "/mosquitto/config/mosquitto.conf")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
  }
}
