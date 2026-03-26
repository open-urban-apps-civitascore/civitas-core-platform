package de.civitascore.portal.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security configuration properties bound from the {@code security.*} namespace. Defines URL paths
 * that are publicly accessible without authentication.
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(List<String> permitPaths) {

  public SecurityProperties {
    if (permitPaths == null) {
      permitPaths = List.of();
    }
  }
}
