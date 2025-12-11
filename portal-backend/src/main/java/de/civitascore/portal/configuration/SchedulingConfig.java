package de.civitascore.portal.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled task execution for the application.
 *
 * <p>This is required for the {@link de.civitascore.portal.service.event.OutboxProcessor} to
 * periodically process pending events from the outbox table.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
