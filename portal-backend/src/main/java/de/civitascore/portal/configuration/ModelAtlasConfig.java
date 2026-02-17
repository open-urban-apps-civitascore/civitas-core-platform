package de.civitascore.portal.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "model-atlas")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class ModelAtlasConfig {
  /** Base URL of the Model Atlas service */
  private String baseUrl;

  /** Scope of the model atlas to be used for requests */
  private String scope;

  /**
   * Stage of the model atlas to be used for requests. For now, only a single stage is supported,
   * this may change in the future with transitions between stages.
   */
  private String stage;
}
