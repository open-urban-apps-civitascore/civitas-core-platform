package de.civitascore.portal.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code civitas.*} namespace.
 * Contains the master encryption key used for credential encryption and the data-plane base URL
 * used to construct public API preview URLs.
 *
 * @param masterKey master encryption key for credential encryption
 * @param api data-plane API configuration (base URL used in {@code previewUrl} construction)
 */
@Validated
@ConfigurationProperties(prefix = "civitas")
public record CivitasProperties(
    @NotBlank String masterKey, @NotNull @NestedConfigurationProperty @Valid Api api) {

  /**
   * Data-plane API configuration.
   *
   * @param baseUrl the data-plane base URL used to build public preview URLs in the form {@code
   *     {baseUrl}/v1/datasets/{datasetId}/{slug}}. Fully-qualified HTTPS URL with no path and no
   *     trailing slash — e.g. {@code https://api.example.com} or {@code
   *     https://api.example.com:8443}.
   */
  public record Api(
      @NotBlank @URL(
              protocol = "https",
              regexp = "^https://[^/]+$",
              message =
                  "civitas.api.base-url must be a fully-qualified HTTPS URL with no path or trailing slash")
          String baseUrl) {}
}
