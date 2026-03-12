package de.civitascore.portal.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModelAtlasConfig.class)
public class RestClientConfig {

  @Bean
  public RestClient.Builder restClientBuilder(ModelAtlasConfig modelAtlasConfig) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(modelAtlasConfig.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(modelAtlasConfig.getReadTimeoutMs()));
    return RestClient.builder().requestFactory(factory);
  }
}
