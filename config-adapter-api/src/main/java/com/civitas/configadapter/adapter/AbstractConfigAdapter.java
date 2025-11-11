package com.civitas.configadapter.adapter;

import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;

/**
 * Abstract base class for ConfigAdapter implementations.
 * Provides common infrastructure including configuration and event publishing capabilities.
 */
public abstract class AbstractConfigAdapter implements ConfigAdapter {

    protected final AppConfig config;
    protected EventPublisher eventPublisher;

    /**
     * Constructor that injects required dependencies.
     *
     * @param config The application configuration containing adapter-specific settings
     * @throws IllegalArgumentException if config is null
     */
    protected AbstractConfigAdapter(AppConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AppConfig cannot be null");
        }
        this.config = config;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    /**
     * Gets the configured EventPublisher.
     * Subclasses should check if eventPublisher is not null before using it.
     *
     * @return the EventPublisher instance, may be null if not yet set
     */
    protected EventPublisher getEventPublisher() {
        return eventPublisher;
    }

    /**
     * Gets the application configuration.
     *
     * @return the AppConfig instance
     */
    protected AppConfig getConfig() {
        return config;
    }
}
