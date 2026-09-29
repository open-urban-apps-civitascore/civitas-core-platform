package de.civitascore.portal.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.civitascore.portal.PortalBackendApplication;
import de.civitascore.portal.util.KeycloakTokenHelper;
import de.civitascore.portal.util.TestContainerImages;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PortalBackendApplication.class)
@ActiveProfiles("test-integration")
@AutoConfigureTestRestTemplate
@Testcontainers
@Import({
  PortalTestDataFactory.class,
  InfraTestDataFactory.class,
  OmitNullRequestFieldsConfiguration.class
})
@Slf4j(access = AccessLevel.PROTECTED)
public abstract class BaseKeycloakIntegrationTest {

  protected static final PostgreSQLContainer POSTGRES;
  protected static final KeycloakContainer KEYCLOAK;

  static {
    POSTGRES =
        new PostgreSQLContainer(TestContainerImages.POSTGRES)
            .withDatabaseName("iot_schema")
            .withUsername("iot")
            .withPassword("iot")
            .withReuse(true);

    KEYCLOAK =
        new KeycloakContainer(TestContainerImages.KEYCLOAK)
            .withRealmImportFile("keycloak/iot-realm.json")
            .withReuse(true);

    POSTGRES.start();
    KEYCLOAK.start();

    log.info("Test Containers Started");
    log.debug("PostgreSQL URL: " + POSTGRES.getJdbcUrl());
    log.debug("Keycloak URL: " + KEYCLOAK.getAuthServerUrl());
  }

  @Autowired protected TestRestTemplate restTemplate;

  protected KeycloakTokenHelper tokenHelper;
  protected Keycloak keycloakAdminClient;
  protected static final String REALM_NAME = "iot";

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

    // KeycloakProperties (@NotBlank authServerUrl/realm) is required at context load; wire it from
    // the started container so the Keycloak integration tests are self-contained locally.
    registry.add("keycloak.auth-server-url", KEYCLOAK::getAuthServerUrl);
    registry.add("keycloak.realm", () -> REALM_NAME);
  }

  @BeforeEach
  void baseSetUp() {
    if (!POSTGRES.isRunning() || !KEYCLOAK.isRunning()) {
      throw new IllegalStateException("Test containers stopped unexpectedly!");
    }

    tokenHelper = new KeycloakTokenHelper(KEYCLOAK);

    keycloakAdminClient =
        KeycloakBuilder.builder()
            .serverUrl(KEYCLOAK.getAuthServerUrl())
            .realm("master")
            .username(KEYCLOAK.getAdminUsername())
            .password(KEYCLOAK.getAdminPassword())
            .clientId("admin-cli")
            .build();

    log.info("PostgreSQL running at " + POSTGRES.getJdbcUrl());
    log.info("Keycloak running at " + KEYCLOAK.getAuthServerUrl());
  }

  @AfterEach
  void baseCleanup() {
    if (keycloakAdminClient != null) {
      try {
        keycloakAdminClient.close();
      } catch (Exception e) {
        log.warn("Failed to close Keycloak admin client: {}", e.getMessage());
      }
    }
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

  protected RealmResource getRealmResource() {
    return keycloakAdminClient.realm(REALM_NAME);
  }

  protected UsersResource getUsersResource() {
    return getRealmResource().users();
  }
}
