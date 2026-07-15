package de.civitascore.modelforge.adminui.seed;

import java.io.Serializable;

/**
 * A bundled seed model set discoverable on the classpath under {@code seed/<id>/}. Each bundle is a
 * folder of {@code *.schema.json} Elements that form a cross-referenced example model (e.g. the OGC
 * SensorThings API set under {@code seed/sta/}).
 *
 * @param id              folder name under {@code seed/}, e.g. {@code sta}
 * @param title           human-readable name for the UI
 * @param locationPattern classpath ant pattern resolving the bundle's schema files
 * @param count           number of {@code *.schema.json} files in the bundle
 */
public record SeedBundle(String id, String title, String locationPattern, int count) implements Serializable {
}
