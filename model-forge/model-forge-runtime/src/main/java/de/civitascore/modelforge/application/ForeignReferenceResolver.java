package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.contract.ForeignReference;
import de.civitascore.modelforge.contract.ForeignReference.Kind;
import de.civitascore.modelforge.contract.ForeignReferencesQuery;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers {@link de.civitascore.modelforge.facade.ModelForge#foreignReferences}. It reads the
 * targets a write stores as reference edges, so no edge escapes the answer.
 *
 * <p>An Element is own when the DataStructure minted it, which its UUID shows. Listing is not
 * enough: a bare {@code $defs} reference lists another DataStructure's Element.
 */
final class ForeignReferenceResolver {

    private static final SchemaRefExtractor EXTRACTOR = new SchemaRefExtractor();

    private final ArtifactRegistry registry;
    private final UrnService urns;

    ForeignReferenceResolver(ArtifactRegistry registry, UrnService urns) {
        this.registry = registry;
        this.urns = urns;
    }

    List<ForeignReference> resolve(ForeignReferencesQuery query) {
        Ownership ownership = new Ownership(query.dataStructureId(), query.document());
        Set<Occurrence> occurrences;
        if (query.document() == null) {
            String stored = query.stored().value();
            if (!registry.heldUrns(List.of(stored)).contains(stored)) {
                throw new IllegalArgumentException("No model " + stored + " in the registry");
            }
            occurrences = storedOccurrences(stored, ownership);
        } else {
            occurrences = occurrencesOf(query.document());
            occurrences.removeIf(occurrence -> ownership.isDerived(occurrence.target()));
            if (!occurrences.isEmpty() && query.stored() != null) {
                occurrences.removeAll(storedOccurrences(query.stored().value(), ownership));
            }
        }
        Map<String, List<Occurrence>> byTarget = new LinkedHashMap<>();
        occurrences.forEach(occurrence ->
            byTarget.computeIfAbsent(occurrence.target(), target -> new ArrayList<>()).add(occurrence));
        List<ForeignReference> foreign = new ArrayList<>();
        byTarget.forEach((target, found) -> {
            if (!ownership.isOwn(target)) {
                foreign.add(foreignReference(target, found, ownership.mintedBy(target)));
            }
        });
        return foreign;
    }

    private static ForeignReference foreignReference(String target, List<Occurrence> found, String mintedBy) {
        List<Occurrence> embeddings = found.stream().filter(Occurrence::embedding).toList();
        Kind kind = embeddings.isEmpty() ? Kind.LINKED
            : embeddings.stream().allMatch(occurrence -> isPinned(occurrence.urn())) ? Kind.EMBEDDED_PINNED
            : Kind.EMBEDDED_FLOATING;
        return new ForeignReference(target, kind, mintedBy);
    }

    private static boolean isPinned(String urn) {
        String version = UrnParser.versionFromUrn(urn);
        return version != null && !UrnParser.LATEST.equals(version);
    }

    private static Set<Occurrence> occurrencesOf(JsonNode document) {
        Set<Occurrence> occurrences = new LinkedHashSet<>();
        EXTRACTOR.extractRefs(document).forEach(ref -> occurrences.add(new Occurrence(ref, true)));
        EXTRACTOR.extractCoreRefTargets(document).forEach(link -> occurrences.add(new Occurrence(link, false)));
        return occurrences;
    }

    /** The references of a stored version and of every own Element it reaches. */
    private Set<Occurrence> storedOccurrences(String stored, Ownership ownership) {
        Set<Occurrence> found = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>(List.of(stored));
        Deque<String> pending = new ArrayDeque<>(visited);
        while (!pending.isEmpty()) {
            String urn = pending.pop();
            registry.fetch(urn).ifPresent(content -> {
                ownership.deriveMembersIfHost(urn, content);
                for (Occurrence occurrence : occurrencesOf(content)) {
                    found.add(occurrence);
                    if (occurrence.embedding()
                        && "element".equals(UrnParser.artifactTypeFromUrn(occurrence.urn()))
                        && ownership.isOwn(occurrence.target())
                        && visited.add(occurrence.urn())) {
                        pending.push(occurrence.urn());
                    }
                }
            });
        }
        return found;
    }

    /** One reference as written; {@code embedding} for a {@code $ref}, not for an {@code x-core-ref}. */
    private record Occurrence(String urn, boolean embedding) {

        String target() {
            return UrnParser.logicalUrn(urn);
        }
    }

    private final class Ownership {

        private final UUID dataStructureId;
        private final String grouping;
        private final Set<String> derived = new HashSet<>();
        private final Map<String, Optional<String>> minters = new HashMap<>();
        private Set<String> listed;

        Ownership(UUID dataStructureId, JsonNode document) {
            this.dataStructureId = dataStructureId;
            grouping = UrnParser.logicalUrn(urns.dataStructure(dataStructureId));
            derived.add(grouping);
            derived.add(UrnParser.logicalUrn(urns.element(dataStructureId)));
            if (document != null) {
                deriveMembers(document);
            }
        }

        /** A stored version of the host lists its members under the same keys a document would. */
        void deriveMembersIfHost(String urn, JsonNode content) {
            if (UrnParser.logicalUrn(urn).equals(grouping)) {
                deriveMembers(content);
            }
        }

        private void deriveMembers(JsonNode document) {
            document.path("$defs").propertyNames()
                .forEach(key -> derived.add(urns.member(dataStructureId, key)));
        }

        boolean isDerived(String target) {
            return derived.contains(target);
        }

        boolean isOwn(String target) {
            if (derived.contains(target)) {
                return true;
            }
            String minter = mintedBy(target);
            return minter != null ? minter.equals(grouping) : listed().contains(target);
        }

        String mintedBy(String target) {
            if (!minters.containsKey(target)) {
                minters.put(target, minterOf(target));
            }
            return minters.get(target).orElse(null);
        }

        private Optional<String> minterOf(String target) {
            String type = UrnParser.artifactTypeFromUrn(target);
            if ("datastructure".equals(type)) {
                return held(target) ? Optional.of(target) : Optional.empty();
            }
            if (!"element".equals(type)) {
                return Optional.empty();
            }
            String sameId = UrnParser.withArtifactType(target, "datastructure");
            if (held(sameId)) {
                return Optional.of(sameId);
            }
            UUID elementId = UrnParser.idFromUrn(target);
            for (String lister : registry.dataStructuresListing(target)) {
                UUID listerId = UrnParser.idFromUrn(lister);
                if (registry.dataStructureMemberKeys(lister).stream()
                    .anyMatch(key -> UrnParser.memberId(listerId, key).equals(elementId))) {
                    return Optional.of(lister);
                }
            }
            return Optional.empty();
        }

        private Set<String> listed() {
            if (listed == null) {
                listed = new HashSet<>(registry.dataStructureMembersOfAnyVersion(grouping));
            }
            return listed;
        }

        private boolean held(String urn) {
            return registry.heldUrns(List.of(urn)).contains(urn);
        }
    }
}
