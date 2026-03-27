package de.civitascore.portal.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient.Builder} bean with connection and read timeouts derived from
 * {@link ModelAtlasConfig}.
 */
@Configuration
@EnableConfigurationProperties(ModelAtlasConfig.class)
public class RestClientConfig {

  /**
   * Creates a pre-configured {@link RestClient.Builder} with connect and read timeouts from the
   * Model Atlas configuration.
   *
   * @param modelAtlasConfig the Model Atlas configuration properties
   * @return a builder with configured timeouts
   */
  @Bean
  public RestClient.Builder restClientBuilder(ModelAtlasConfig modelAtlasConfig) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(modelAtlasConfig.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(modelAtlasConfig.getReadTimeoutMs()));
    return RestClient.builder().requestFactory(factory);
  }
}
