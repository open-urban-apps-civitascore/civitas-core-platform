package de.civitascore.portal.util;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfiguration {

  private static final String POSTGRES_IMAGE = "postgres:15";
  private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.3.4";
  private static final String REALM_NAME = "iot";
  private static final String REALM_IMPORT_FILE = "keycloak/iot-realm.json";

  @Bean
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
        .withDatabaseName("iot_schema")
        .withUsername("iot")
        .withPassword("iot")
        .waitingFor(
            Wait.forHttp("/realms/iot/.well-known/openid-configuration")
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
  }

  @Bean
  KeycloakContainer keycloakContainer() {
    return new KeycloakContainer(DockerImageName.parse(KEYCLOAK_IMAGE).toString())
        .withRealmImportFile(REALM_IMPORT_FILE)
        .withEnv("KC_HEALTH_ENABLED", "true")
        .withEnv("KC_METRICS_ENABLED", "true")
        .waitingFor(Wait.forHttp("/health").forPort(8080))
        .withStartupTimeout(Duration.ofMinutes(5));
  }
}
