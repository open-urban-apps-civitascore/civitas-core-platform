package de.civitascore.portal;

import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.configuration.SagaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {"de.civitascore.portal"})
@EntityScan(basePackages = "de.civitascore.portal")
@EnableConfigurationProperties({
  KeycloakProperties.class,
  EventProperties.class,
  SagaProperties.class
})
public class PortalBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(PortalBackendApplication.class, args);
  }
}
