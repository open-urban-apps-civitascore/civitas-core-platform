package com.civitas.configadapter.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AppConfig {

    private final Properties properties;

    public AppConfig(Properties properties) {
		this.properties = properties;
    }

    public AppConfig(String configFile) {
        this.properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + configFile);
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load configuration", ex);
        }
    }

    public List<String> getAdapterClasses() {
        List<String> adapterClasses = new ArrayList<>();
        String adaptersProperty = properties.getProperty("adapters");

        if (adaptersProperty != null && !adaptersProperty.trim().isEmpty()) {
            String[] adapters = adaptersProperty.split(",");
            for (String adapter : adapters) {
                String trimmed = adapter.trim();
                if (!trimmed.isEmpty()) {
                    adapterClasses.add(trimmed);
                }
            }
        }
        return adapterClasses;
    }

    public String getEventHandlerClass() {
        return properties.getProperty("eventhandler.class");
    }

    public String getEventConsumerClass() {
        return properties.getProperty("eventconsumer.class");
    }

    public String getEventPublisherClass() {
        return properties.getProperty("eventpublisher.class");
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public Properties getProperties() {
        return properties;
    }
}
