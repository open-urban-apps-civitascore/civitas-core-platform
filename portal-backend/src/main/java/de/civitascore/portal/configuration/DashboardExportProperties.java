package de.civitascore.portal.configuration;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportTopics;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Dashboard export properties bound from the {@code dashboard-export.*} namespace. The topics must
 * match config-adapter's {@code dashboardexport.kafka.*-topic}.
 */
@Validated
@ConfigurationProperties(prefix = "dashboard-export")
public record DashboardExportProperties(
    @NotBlank @DefaultValue(DashboardExportTopics.REQUEST) String requestTopic,
    @NotBlank @DefaultValue(DashboardExportTopics.RESULT) String resultTopic,
    @Positive @DefaultValue("30") int waitSeconds,
    @Positive @DefaultValue("2") int maxConcurrent) {

  public Duration waitTime() {
    return Duration.ofSeconds(waitSeconds);
  }
}
