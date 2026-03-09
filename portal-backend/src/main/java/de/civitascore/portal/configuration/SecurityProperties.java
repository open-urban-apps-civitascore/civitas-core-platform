package de.civitascore.portal.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(List<String> permitPaths) {

  public SecurityProperties {
    if (permitPaths == null) {
      permitPaths = List.of();
    }
  }
}
