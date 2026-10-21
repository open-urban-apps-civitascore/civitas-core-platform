package de.civitascore.portal.configuration;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestTopics;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection test properties bound from the {@code connection-test.*} namespace. The topics must
 * match the ones config-adapter uses.
 */
@Validated
@ConfigurationProperties(prefix = "connection-test")
public record ConnectionTestProperties(
    @NotBlank @DefaultValue(ConnectionTestTopics.REQUEST) String requestTopic,
    @NotBlank @DefaultValue(ConnectionTestTopics.RESULT) String resultTopic,
    @Positive @DefaultValue("20") int waitSeconds,
    @Positive @DefaultValue("4") int maxConcurrent) {

  public Duration waitTime() {
    return Duration.ofSeconds(waitSeconds);
  }
}
