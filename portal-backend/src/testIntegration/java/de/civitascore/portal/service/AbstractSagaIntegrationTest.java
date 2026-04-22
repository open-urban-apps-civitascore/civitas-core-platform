package de.civitascore.portal.service;

import com.github.dockerjava.api.model.ContainerNetwork;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Abstract base for saga integration tests. Provides factory methods for creating the common
 * container set (postgis, frost, kafka, redpandaConnect, mosquitto) with standard configuration,
 * and a utility to resolve the FROST URL using the Docker network gateway IP (required so FROST is
 * reachable from both the host test process and containers on the same saga network).
 *
 * <p>Each concrete subclass creates its own container instances by calling the factory methods,
 * keeping independent lifecycles.
 */
@Tag("saga")
abstract class AbstractSagaIntegrationTest extends BaseKeycloakIntegrationTest {

  static final String FROST_IMAGE = "hylkevds/frost-http-projects:latest";
  static final String KAFKA_IMAGE = "apache/kafka:3.8.0";
  static final String REDPANDA_CONNECT_IMAGE = "redpandadata/connect:4";
  static final String POSTGIS_IMAGE = "postgis/postgis:16-3.4-alpine";
  static final String MOSQUITTO_IMAGE = "eclipse-mosquitto:2.0.20";

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createPostgis(Network network) {
    return new GenericContainer<>(POSTGIS_IMAGE)
        .withNetwork(network)
        .withNetworkAliases("database")
        .withEnv("POSTGRES_DB", "sensorthings")
        .withEnv("POSTGRES_USER", "sensorthings")
        .withEnv("POSTGRES_PASSWORD", "ChangeMe")
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
  }

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createFrost(Network network, GenericContainer<?> postgis) {
    return new GenericContainer<>(FROST_IMAGE)
        .withNetwork(network)
        .withNetworkAliases("frost-server")
        .withExposedPorts(8080)
        .dependsOn(postgis)
        .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
        .withEnv("plugins_modelLoader_enable", "true")
        .withEnv("plugins_multiDatastream_enable", "false")
        .withEnv("plugins_actuation_enable", "false")
        .withEnv("persistence_db_driver", "org.postgresql.Driver")
        .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
        .withEnv("persistence_db_username", "sensorthings")
        .withEnv("persistence_db_password", "ChangeMe")
        .withEnv("persistence_autoUpdateDatabase", "true")
        .withEnv("plugins_modelLoader_securityPath", "")
        .withEnv("plugins_modelLoader_securityFiles", "")
        .waitingFor(
            Wait.forHttp("/FROST-Server/v1.1/Projects")
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
  }

  protected static KafkaContainer createKafka() {
    return new KafkaContainer(KAFKA_IMAGE);
  }

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createRedpandaConnect(Network network) {
    return new GenericContainer<>(REDPANDA_CONNECT_IMAGE)
        .withNetwork(network)
        .withNetworkAliases("redpanda-connect")
        .withExposedPorts(4195)
        .withCommand("streams")
        .withEnv("FROST_BASE", "http://frost-server:8080/FROST-Server/v1.1")
        .waitingFor(
            Wait.forHttp("/ready").forPort(4195).withStartupTimeout(Duration.ofSeconds(60)));
  }

  @SuppressWarnings("resource")
  protected static GenericContainer<?> createMosquitto(Network network) {
    return new GenericContainer<>(MOSQUITTO_IMAGE)
        .withNetwork(network)
        .withNetworkAliases("mqtt-broker")
        .withExposedPorts(1883)
        .withCopyFileToContainer(
            MountableFile.forHostPath(
                Path.of("src/testIntegration/resources/mosquitto/mosquitto.conf").toAbsolutePath()),
            "/mosquitto/config/mosquitto.conf")
        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
  }

  /**
   * Resolves the FROST base URL using the Docker network gateway IP. This ensures FROST is
   * reachable from both the host (test process via mapped port) and containers on the same network
   * (Redpanda Connect), where {@code localhost} would resolve to the container's own loopback.
   */
  protected static String resolveGatewayFrostUrl(GenericContainer<?> frost, Network sagaNetwork) {
    String gatewayIp =
        frost.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
            .filter(net -> sagaNetwork.getId().equals(net.getNetworkID()))
            .findFirst()
            .map(ContainerNetwork::getGateway)
            .orElse(frost.getHost());
    return "http://" + gatewayIp + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
  }
}
