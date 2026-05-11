package de.civitascore.portal.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Core CIVITAS platform configuration properties bound from the {@code civitas.*} namespace.
 * Contains the master encryption key used for credential encryption and the data-plane domain used
 * to construct public API preview URLs.
 *
 * @param masterKey master encryption key for credential encryption
 * @param api data-plane API configuration (domain used in {@code previewUrl} construction)
 */
@Validated
@ConfigurationProperties(prefix = "civitas")
public record CivitasProperties(
    @NotBlank String masterKey, @NotNull @NestedConfigurationProperty @Valid Api api) {

  /**
   * Data-plane API configuration.
   *
   * @param domain the data-plane host (per concept #1380 + ADR #1387) used to build public preview
   *     URLs in the form {@code https://{domain}/v1/datasets/{datasetId}/{slug}}. Must point at the
   *     data-plane host, not the management host. Must be a bare host (with optional port), no
   *     scheme, no trailing slash — {@code DataSetAssembler} prepends {@code https://} and the URL
   *     path. The pattern fails at startup on common misconfigurations (e.g. {@code
   *     https://api.example.com/}).
   */
  public record Api(
      @NotBlank @Pattern(
              regexp =
                  "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?"
                      + "(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*"
                      + "(:[0-9]+)?$",
              message =
                  "Domain must be a bare host with optional port (no scheme, no trailing slash)")
          String domain) {}
}
