package de.civitascore.portal.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Model Atlas service integration, bound from the {@code
 * model-atlas.*} namespace. Defines the base URL, scope, stage, and HTTP timeout settings.
 */
@Configuration
@ConfigurationProperties(prefix = "model-atlas")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class ModelAtlasConfig {

  /** Base URL of the Model Atlas service. Must be an http or https URL. */
  @NotBlank @URL(message = "model-atlas.baseUrl must be a valid URL")
  private String baseUrl;

  /** Scope of the model atlas to be used for requests */
  @NotBlank private String scope;

  /**
   * Stage of the model atlas to be used for requests. For now, only a single stage is supported,
   * this may change in the future with transitions between stages.
   */
  @NotBlank private String stage;

  /** HTTP connect timeout in milliseconds for Model Atlas requests (default: 5 s). */
  private int connectTimeoutMs = 5000;

  /** HTTP read timeout in milliseconds for Model Atlas requests (default: 30 s). */
  private int readTimeoutMs = 30000;
}
