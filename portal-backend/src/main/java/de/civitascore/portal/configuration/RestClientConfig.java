package de.civitascore.portal.configuration;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient.Builder} bean with connection and read timeouts derived from
 * {@link ModelAtlasProperties}.
 */
@Configuration
public class RestClientConfig {

  @Bean
  public RestClient.Builder restClientBuilder(ModelAtlasProperties modelAtlasProperties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(modelAtlasProperties.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(modelAtlasProperties.readTimeoutMs()));
    return RestClient.builder().requestFactory(factory);
  }
}
