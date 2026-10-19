package de.civitascore.modelforge.adminui.wicket;

import de.civitascore.modelforge.contract.ArtifactKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the JSON Schema that should drive editor autocomplete/validation for a given artifact
 * kind. Two sources:
 *
 * <ul>
 *   <li><b>Artifact-type documents</b> (Mapping, Pipeline, DataSet, DataSource, DataSink,
 *       DataStructure) — the canonical {@code *.schema.json} shipped in {@code model-forge-runtime}'s
 *       resources, on the admin-ui classpath via the starter&rarr;runtime dependency.</li>
 *   <li><b>Elements</b> — which <em>are</em> JSON Schemas themselves — get a curated JSON Schema
 *       2020-12 authoring aid ({@code schema/json-schema-2020-12.json}) so completion suggests
 *       schema keywords ({@code type}, {@code properties}, {@code $ref}, …).</li>
 * </ul>
 *
 * <p>Results are cached; the schemas are static classpath resources. Reading is best-effort — a
 * missing resource yields {@link Optional#empty()} and the editor degrades to plain JSON.
 */
public final class SchemaCatalog {

    private static final Logger log = LoggerFactory.getLogger(SchemaCatalog.class);

    /** Classpath locations (root of model-forge-runtime resources) keyed by artifact kind. */
    private static final Map<ArtifactKind, String> RESOURCE_BY_KIND = Map.of(
        ArtifactKind.DATA_STRUCTURE, "/datastructure.schema.json",
        ArtifactKind.DATA_SET, "/dataset.schema.json",
        ArtifactKind.MAPPING, "/mapping.schema.json",
        ArtifactKind.PIPELINE, "/pipeline.schema.json",
        ArtifactKind.DATA_SOURCE, "/datasource.schema.json",
        ArtifactKind.DATA_SINK, "/datasink.schema.json"
    );

    private static final String META_SCHEMA = "/schema/json-schema-2020-12.json";

    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private SchemaCatalog() {}

    /**
     * The schema to author documents of this kind against: the type schema for the manifest kinds,
     * the JSON Schema meta-schema for Elements.
     */
    public static Optional<String> schemaForKind(ArtifactKind kind) {
        if (kind == ArtifactKind.ELEMENT) {
            return metaSchema();
        }
        String resource = RESOURCE_BY_KIND.get(kind);
        return resource == null ? Optional.empty() : read(resource);
    }

    /** The JSON Schema 2020-12 authoring aid (for editing Elements / raw JSON Schemas). */
    public static Optional<String> metaSchema() {
        return read(META_SCHEMA);
    }

    private static Optional<String> read(String classpathResource) {
        return CACHE.computeIfAbsent(classpathResource, path -> {
            try (InputStream in = SchemaCatalog.class.getResourceAsStream(path)) {
                if (in == null) {
                    log.warn("Editor schema resource not found on classpath: {}", path);
                    return Optional.empty();
                }
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("Could not read editor schema resource {}: {}", path, e.getMessage());
                return Optional.empty();
            }
        });
    }
}
