package de.civitascore.portal.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.civitascore.portal.PortalBackendApplication;
import de.civitascore.portal.util.KeycloakTokenHelper;
import de.civitascore.portal.util.TestContainerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PortalBackendApplication.class)
@ActiveProfiles("test-integration")
@Testcontainers
@Import(TestContainerConfiguration.class)
@Slf4j
public abstract class BaseKeycloakIntegrationTest {

  protected static final PostgreSQLContainer<?> POSTGRES;
  protected static final KeycloakContainer KEYCLOAK;

  static {
    POSTGRES =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("iot_schema")
            .withUsername("iot")
            .withPassword("iot");

    KEYCLOAK =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.3.4")
            .withRealmImportFile("keycloak/iot-realm.json");

    POSTGRES.start();
    KEYCLOAK.start();

    log.info("Test Containers Started");
    log.debug("PostgreSQL URL: " + POSTGRES.getJdbcUrl());
    log.debug("Keycloak URL: " + KEYCLOAK.getAuthServerUrl());
  }

  @Autowired protected TestRestTemplate restTemplate;

  protected KeycloakTokenHelper tokenHelper;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Register container properties after they're started
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

    String authServerUrl = KEYCLOAK.getAuthServerUrl();
    String issuerUri = authServerUrl + "/realms/iot";
    String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";

    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkSetUri);
  }

  @BeforeEach
  void setUp() {
    if (!POSTGRES.isRunning() || !KEYCLOAK.isRunning()) {
      throw new IllegalStateException("Test containers stopped unexpectedly!");
    }

    tokenHelper = new KeycloakTokenHelper(KEYCLOAK);

    log.info("PostgreSQL running at " + POSTGRES.getJdbcUrl());
    log.info("Keycloak running at " + KEYCLOAK.getAuthServerUrl());
  }

  @AfterAll
  static void tearDown() {
    log.info("Test Containers will be stopped automatically by Testcontainers");
  }

  protected String getValidAccessToken() {
    return tokenHelper.getAccessToken("testuser", "password");
  }

  protected String getValidAccessToken(String username, String password) {
    return tokenHelper.getAccessToken(username, password);
  }
}
