package de.civitascore.portal.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.civitascore.portal.PortalBackendApplication;
import de.civitascore.portal.util.KeycloakTokenHelper;
import de.civitascore.portal.util.TestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PortalBackendApplication.class)
@ActiveProfiles("test")
@Testcontainers
@Import(TestContainerConfiguration.class)
public abstract class BaseKeycloakIntegrationTest {

  @Container static PostgreSQLContainer<?> postgres;

  @Container static KeycloakContainer keycloak;

  @Autowired protected TestRestTemplate restTemplate;

  protected KeycloakTokenHelper tokenHelper;

  static {
    // Initialize containers from beans
    postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("iot_schema")
            .withUsername("iot")
            .withPassword("iot");

    keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.3.4")
            .withRealmImportFile("keycloak/iot-realm.json");

    postgres.start();
    keycloak.start();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Configure DataSource properties
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    String authServerUrl = keycloak.getAuthServerUrl();
    String issuerUri = authServerUrl + "/realms/iot";
    String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";

    System.out.println("Keycloak Auth Server URL: " + authServerUrl);
    System.out.println("JWT Issuer URI: " + issuerUri);
    System.out.println("JWK Set URI: " + jwkSetUri);

    // Configure Keycloak JWT properties with ACTUAL container URLs
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkSetUri);

    // Configure Keycloak properties
  }

  @BeforeEach
  void setUp() {
    tokenHelper = new KeycloakTokenHelper(keycloak);
    System.out.println(
        " PostgreSQL running: " + postgres.isRunning() + " at " + postgres.getJdbcUrl());
    System.out.println(
        " Keycloak running: " + keycloak.isRunning() + " at " + keycloak.getAuthServerUrl());
  }

  protected String getValidAccessToken() {
    return tokenHelper.getAccessToken("testuser", "password");
  }

  protected String getValidAccessToken(String username, String password) {
    return tokenHelper.getAccessToken(username, password);
  }
}
