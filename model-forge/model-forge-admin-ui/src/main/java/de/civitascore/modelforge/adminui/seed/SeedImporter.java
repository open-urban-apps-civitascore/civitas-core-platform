package de.civitascore.modelforge.adminui.seed;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared seed-import logic used both at startup ({@link StaSeedConfiguration}) and from the UI
 * ({@code SeedPage}). Imports JSON Schema Elements through the same facade entry point a real host
 * would use ({@link ModelForge#importSchema}).
 *
 * <p>Idempotent: an Element whose logical URN (from its {@code $id}) already resolves is counted as
 * <em>present</em> and skipped; a single failed import is recorded without aborting the batch.
 */
@Component
public class SeedImporter {

    private static final Logger log = LoggerFactory.getLogger(SeedImporter.class);

    /** Classpath ant pattern that finds every bundled seed set: {@code seed/<id>/*.schema.json}. */
    private static final String BUNDLE_SCAN = "classpath*:seed/*/*.schema.json";

    /** Display titles for known bundle ids; unknown ids fall back to the folder name. */
    private static final Map<String, String> BUNDLE_TITLES = Map.of(
        "sta", "OGC SensorThings API (STA)"
    );

    private final ModelForge modelForge;
    private final ObjectMapper mapper = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public SeedImporter(ModelForge modelForge) {
        this.modelForge = modelForge;
    }

    /** Outcome of a seed import, aggregated across all files in the batch. */
    public record SeedResult(int imported, int present, int failed, List<String> messages)
        implements Serializable {

        public SeedResult {
            messages = List.copyOf(messages);
        }

        public int total() {
            return imported + present + failed;
        }
    }

    /** Discovers the bundled seed sets on the classpath, ordered by id. */
    public List<SeedBundle> discoverBundles() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            for (Resource resource : resolver.getResources(BUNDLE_SCAN)) {
                String id = bundleIdOf(resource);
                if (id != null) {
                    counts.merge(id, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("Could not scan seed bundles: {}", e.getMessage());
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new SeedBundle(
                e.getKey(),
                BUNDLE_TITLES.getOrDefault(e.getKey(), e.getKey()),
                "classpath*:seed/" + e.getKey() + "/*.schema.json",
                e.getValue()))
            .toList();
    }

    /** Imports one bundle by its classpath location pattern (e.g. {@link SeedBundle#locationPattern()}). */
    public SeedResult importClasspath(String locationPattern) {
        Resource[] resources;
        try {
            resources = resolver.getResources(locationPattern);
        } catch (Exception e) {
            return new SeedResult(0, 0, 1, List.of("Could not resolve " + locationPattern + ": " + e.getMessage()));
        }
        // Deterministic order; irrelevant to correctness (refs are cyclic) but nice for logs.
        List<Resource> sorted = new ArrayList<>(List.of(resources));
        sorted.sort(Comparator.comparing(r -> Objects.toString(r.getFilename(), "")));

        Accumulator acc = new Accumulator();
        for (Resource resource : sorted) {
            String name = Objects.toString(resource.getFilename(), "?");
            try (InputStream in = resource.getInputStream()) {
                apply(name, mapper.readTree(in), acc);
            } catch (Exception e) {
                acc.fail(name, e);
            }
        }
        return acc.toResult();
    }

    /** Imports uploaded raw schema files ({@code name -> bytes}). */
    public SeedResult importRaw(Map<String, byte[]> files) {
        Accumulator acc = new Accumulator();
        files.forEach((name, bytes) -> {
            try {
                apply(name, mapper.readTree(bytes), acc);
            } catch (Exception e) {
                acc.fail(name, e);
            }
        });
        return acc.toResult();
    }

    private void apply(String name, JsonNode schema, Accumulator acc) {
        // Probe EVERY identity the bundle declares, not just one. A pure $defs container (no shape
        // of its own) declares one per member, and each member is imported in its own transaction —
        // so probing a single id reports "already present" while the rest are missing, and no re-run
        // can ever repair the set.
        List<String> ids = declaredLogicalUrns(schema);
        if (!ids.isEmpty()
            && ids.stream().allMatch(urn -> modelForge.getArtifact(new ArtifactId(urn)).isPresent())) {
            acc.present();
            return;
        }
        try {
            modelForge.importSchema(new ImportSchemaCommand(schema));
            acc.imported();
        } catch (Exception e) {
            acc.fail(name, e);
        }
    }

    /**
     * Every logical CORE URN the document claims as an identity — its own {@code $id} plus one per
     * {@code $defs} member. Ids that are not CORE URNs are skipped: they carry no identity to probe.
     */
    private static List<String> declaredLogicalUrns(JsonNode schema) {
        List<String> urns = new ArrayList<>();
        addIfCoreUrn(schema.path("$id").asText(null), urns);
        JsonNode defs = schema.path("$defs");
        if (defs.isObject()) {
            for (JsonNode def : defs) {
                addIfCoreUrn(def.path("$id").asText(null), urns);
            }
        }
        return urns;
    }

    private static void addIfCoreUrn(String id, List<String> target) {
        if (id == null) return;
        String logical = UrnParser.logicalUrn(id);
        if (UrnParser.isUrn(logical)) {
            target.add(logical);
        }
    }

    /** Derives the bundle id (folder under {@code seed/}) from a resource URL. */
    private static String bundleIdOf(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int seed = url.indexOf("/seed/");
            if (seed < 0) {
                return null;
            }
            String rest = url.substring(seed + "/seed/".length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Accumulator {
        private int imported;
        private int present;
        private int failed;
        private final List<String> messages = new ArrayList<>();

        void imported() {
            imported++;
        }

        void present() {
            present++;
        }

        void fail(String name, Exception e) {
            failed++;
            String msg = name + ": " + e.getMessage();
            messages.add(msg);
            log.warn("Seed import failed for {}", msg);
        }

        SeedResult toResult() {
            return new SeedResult(imported, present, failed, List.copyOf(messages));
        }
    }
}
