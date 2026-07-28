package de.civitascore.portal.config;

import de.civitascore.portal.configuration.KeycloakProperties;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

/**
 * Registers the in-process config adapter ({@link ConfigAdapterTestHelper}) as a context bean.
 *
 * <p>Being a bean matters for ordering: the adapter's Kafka consumer is subscribed during context
 * refresh, so it is already listening when the {@code init} profile initializers publish IDM events
 * from {@code ApplicationReadyEvent}. Constructing the helper in {@code @BeforeEach} instead leaves
 * those startup events unconsumed until every publish has run into its full result timeout.
 *
 * <p>The target realm is created here for the same reason — the adapter cannot create users in a
 * realm that does not exist yet.
 */
@TestConfiguration
@Slf4j
public class ConfigAdapterTestConfiguration {

  /**
   * @param broker injected to force the embedded broker to start before the adapter subscribes
   */
  @Bean
  ConfigAdapterTestHelper configAdapterTestHelper(
      EmbeddedKafkaBroker broker,
      KafkaTemplate<String, String> eventKafkaTemplate,
      KeycloakProperties keycloakProperties) {
    ensureTargetRealmExists(keycloakProperties.targetRealm());
    return new ConfigAdapterTestHelper(
        BaseKeycloakIntegrationTest.KEYCLOAK, broker.getBrokersAsString(), eventKafkaTemplate);
  }

  private void ensureTargetRealmExists(String realmName) {
    try (Keycloak admin =
        KeycloakBuilder.builder()
            .serverUrl(BaseKeycloakIntegrationTest.KEYCLOAK.getAuthServerUrl())
            .realm("master")
            .username(BaseKeycloakIntegrationTest.KEYCLOAK.getAdminUsername())
            .password(BaseKeycloakIntegrationTest.KEYCLOAK.getAdminPassword())
            .clientId("admin-cli")
            .build()) {
      try {
        admin.realm(realmName).toRepresentation();
        log.debug("{} realm already exists", realmName);
        return;
      } catch (Exception e) {
        log.info("Creating {} realm in Keycloak", realmName);
      }
      RealmRepresentation realm = new RealmRepresentation();
      realm.setRealm(realmName);
      realm.setEnabled(true);
      realm.setDisplayName("Civitas Core Test Realm");
      realm.setEditUsernameAllowed(true);
      admin.realms().create(realm);
    } catch (Exception e) {
      log.error("Failed to ensure {} realm exists: {}", realmName, e.getMessage(), e);
    }
  }
}
