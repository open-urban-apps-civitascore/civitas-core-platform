package de.civitascore.modelforge.adminui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone debug/admin host application. Embeds Model Forge via the Spring Boot starter and
 * exposes a Wicket UI for browsing, editing and importing artifacts through the public
 * {@link de.civitascore.modelforge.facade.ModelForge} facade only.
 */
@SpringBootApplication
public class AdminUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminUiApplication.class, args);
    }
}
