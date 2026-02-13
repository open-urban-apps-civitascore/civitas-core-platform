package de.civitascore.portal.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Slf4j
public class GatewayConfig {

  @Bean
  @ConfigurationProperties(prefix = "gateway")
  @Validated
  GatewayConfigProperties gatewayConfigProperties() {
    return new GatewayConfigProperties();
  }

  @Getter
  @Setter
  public static class GatewayConfigProperties {
    private String baseUrl = "http://localhost:8080";
  }
}
