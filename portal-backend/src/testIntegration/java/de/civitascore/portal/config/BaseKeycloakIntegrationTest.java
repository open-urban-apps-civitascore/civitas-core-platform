package de.civitascore.portal.config;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import de.civitascore.portal.PortalBackendApplication;
import de.civitascore.portal.util.KeycloakTokenHelper;
import de.civitascore.portal.util.TestContainerConfiguration;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PortalBackendApplication.class)
@ActiveProfiles("test-integration")
@Testcontainers
@Import(TestContainerConfiguration.class)
public abstract class BaseKeycloakIntegrationTest {

  @Container protected static PostgreSQLContainer<?> postgres;

  @Container protected static KeycloakContainer keycloak;

  @Autowired protected TestRestTemplate restTemplate;

  protected KeycloakTokenHelper tokenHelper;

  private static volatile boolean containersInitialized = false;
  private static final Object LOCK = new Object();

  static {
    synchronized (LOCK) {
      if (!containersInitialized) {
        // Initialize containers from beans
        postgres =
            new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("iot_schema")
                .withUsername("iot")
                .withPassword("iot")
                .withReuse(true); // Reuse for better performance and consistency

        keycloak =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.3.4")
                .withRealmImportFile("keycloak/iot-realm.json")
                .withReuse(true); // Reuse for better performance and consistency

        postgres.start();
        keycloak.start();

        containersInitialized = true;

        System.out.println("=== Test Containers Started ===");
        System.out.println("PostgreSQL URL: " + postgres.getJdbcUrl());
        System.out.println("Keycloak URL: " + keycloak.getAuthServerUrl());
      }
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Ensure containers are running
    if (!postgres.isRunning() || !keycloak.isRunning()) {
      throw new IllegalStateException("Test containers are not running!");
    }

    // Configure DataSource properties
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

    // Get Keycloak URLs - use the actual running instance
    String authServerUrl = keycloak.getAuthServerUrl();
    String issuerUri = authServerUrl + "/realms/iot";
    String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";

    System.out.println("=== Configuring Spring Security OAuth2 ===");
    System.out.println("Keycloak Auth Server URL: " + authServerUrl);
    System.out.println("JWT Issuer URI: " + issuerUri);
    System.out.println("JWK Set URI: " + jwkSetUri);

    // Configure Keycloak JWT properties with ACTUAL container URLs
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkSetUri);
  }

  @BeforeEach
  void setUp() {
    // Verify containers are still running
    if (!postgres.isRunning() || !keycloak.isRunning()) {
      throw new IllegalStateException("Test containers stopped unexpectedly!");
    }

    tokenHelper = new KeycloakTokenHelper(keycloak);
    System.out.println(
        "✓ PostgreSQL running: " + postgres.isRunning() + " at " + postgres.getJdbcUrl());
    System.out.println(
        "✓ Keycloak running: " + keycloak.isRunning() + " at " + keycloak.getAuthServerUrl());
  }

  @AfterAll
  static void tearDown() {
    // Containers will be stopped automatically by Testcontainers
    // Only log the shutdown
    System.out.println("=== Test Containers Cleanup ===");
    System.out.println("Containers will be stopped by Testcontainers framework");
  }

  protected String getValidAccessToken() {
    return tokenHelper.getAccessToken("testuser", "password");
  }

  protected String getValidAccessToken(String username, String password) {
    return tokenHelper.getAccessToken(username, password);
  }
}
