package de.civitascore.modelforge.adminui.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the bundled OGC SensorThings API (STA) example schemas into the registry on startup, so a
 * fresh admin-ui has a real, cross-referenced model to explore. Delegates to {@link SeedImporter},
 * the same logic the {@code SeedPage} UI uses.
 *
 * <p>Enabled with {@code model-forge.admin-ui.seed.enabled=true}. The admin-ui's
 * {@code application.yml} deliberately defaults it to {@code false}, so pointing the UI at
 * portal-backend's registry never seeds the bundled examples into it; turn it on only for a
 * standalone instance with its own database. Idempotent: an Element whose logical URN already
 * resolves is skipped, and a single failed import is logged without aborting startup.
 */
@Configuration
public class StaSeedConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StaSeedConfiguration.class);
    private static final String STA_BUNDLE = "classpath*:seed/sta/*.schema.json";

    @Bean
    @ConditionalOnProperty(prefix = "model-forge.admin-ui.seed", name = "enabled", havingValue = "true")
    ApplicationRunner staExampleSeeder(SeedImporter seedImporter) {
        return args -> {
            var result = seedImporter.importClasspath(STA_BUNDLE);
            log.info("STA example seed: {} imported, {} already present, {} failed",
                result.imported(), result.present(), result.failed());
        };
    }
}
