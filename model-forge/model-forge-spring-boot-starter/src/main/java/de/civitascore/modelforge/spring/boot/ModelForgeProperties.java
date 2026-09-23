package de.civitascore.modelforge.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "model-forge")
public record ModelForgeProperties(
    Urn urn,
    Registry registry
) {
    private static final String DEFAULT_SCOPE = "platform";
    private static final String DEFAULT_OWNER = "civitas";
    private static final String DEFAULT_DOMAIN = "common";
    private static final String DEFAULT_VERSION = "1.0.0";
    private static final String DEFAULT_SCHEMA = "model_forge";
    private static final String DEFAULT_MIGRATION_TABLE = "model_forge_schema_history";

    public ModelForgeProperties {
        urn = urn == null ? new Urn(null, null, null, null) : urn;
        registry = registry == null ? new Registry(null, null) : registry;
    }

    public record Urn(
        String scope,
        String owner,
        String domain,
        String defaultVersion
    ) {
        public Urn {
            scope = valueOrDefault(scope, DEFAULT_SCOPE);
            owner = valueOrDefault(owner, DEFAULT_OWNER);
            domain = valueOrDefault(domain, DEFAULT_DOMAIN);
            defaultVersion = valueOrDefault(defaultVersion, DEFAULT_VERSION);
        }
    }

    public record Registry(
        String schema,
        String migrationTable
    ) {
        public Registry {
            schema = valueOrDefault(schema, DEFAULT_SCHEMA);
            migrationTable = valueOrDefault(migrationTable, DEFAULT_MIGRATION_TABLE);
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
