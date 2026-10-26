package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ForeignReference;
import de.civitascore.modelforge.contract.ForeignReferencesQuery;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.civitascore.modelforge.contract.ForeignReference.Kind.EMBEDDED_FLOATING;
import static de.civitascore.modelforge.contract.ForeignReference.Kind.EMBEDDED_PINNED;
import static de.civitascore.modelforge.contract.ForeignReference.Kind.LINKED;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForeignReferenceResolverTest {

    private static final UUID HOST = UUID.fromString("2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID BRAVO = UUID.fromString("7f6e5d4c-3b2a-4190-8f7e-6d5c4b3a2918");

    private final ObjectMapper mapper = new ObjectMapper();
    private final UrnService urns = new UrnService("platform", "civitas", "common", "1.0.0");

    private final String hostGrouping = UrnParser.logicalUrn(urns.dataStructure(HOST));
    private final String bravoGrouping = UrnParser.logicalUrn(urns.dataStructure(BRAVO));
    private final String bravoMember = urns.member(BRAVO, "BravoInner");

    private ArtifactRegistry registry;
    private Set<String> held;
    private ForeignReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        registry = mock(ArtifactRegistry.class);
        held = new HashSet<>(Set.of(bravoGrouping, bravoMember, hostGrouping));
        when(registry.heldUrns(anyCollection())).thenAnswer(call -> {
            Set<String> found = new LinkedHashSet<>();
            for (Object urn : call.getArgument(0, Collection.class)) {
                if (held.contains(UrnParser.logicalUrn((String) urn))) {
                    found.add((String) urn);
                }
            }
            return found;
        });
        when(registry.dataStructuresListing(bravoMember)).thenReturn(List.of(bravoGrouping));
        when(registry.dataStructureMemberKeys(bravoGrouping)).thenReturn(Set.of("BravoRoot", "BravoInner"));
        resolver = new ForeignReferenceResolver(registry, urns);
    }

    private JsonNode json(String text) {
        return mapper.readTree(text);
    }

    private List<ForeignReference> ofDocument(String document) {
        return resolver.resolve(new ForeignReferencesQuery(HOST, json(document), null));
    }

    private static String library(String rootProperties, String extraDefs) {
        return """
            {"$ref": "#/$defs/Root",
             "$defs": {"Root": {"type": "object", "properties": %s}%s}}
            """.formatted(rootProperties, extraDefs);
    }

    @Test
    void aModelOfLocalPointersAndGeoJsonReferencesNothingForeignAndReadsNoRegistry() {
        var references = ofDocument(library(
            "{\"geo\": {\"$ref\": \"https://geojson.org/schema/Point.json\"}, \"x\": {\"$ref\": \"#/$defs/Other\"}}",
            ", \"Other\": {\"type\": \"object\"}"));

        assertThat(references).isEmpty();
        verify(registry, never()).dataStructuresListing(anyString());
        verify(registry, never()).dataStructureMembersOfAnyVersion(anyString());
    }

    @Test
    void theDataStructuresOwnGroupingRootAndDerivedMembersAreNotForeign() {
        String ownMember = urns.member(HOST, "Shared") + ":1.0.0";
        var references = ofDocument("""
            {"$ref": "%s",
             "$defs": {"Shared": {"$ref": "%s"}},
             "properties": {"self": {"$ref": "%s"}}}
            """.formatted(ownMember, ownMember, UrnParser.logicalUrn(urns.element(HOST))));

        assertThat(references).isEmpty();
    }

    @Test
    void aPropertyReferenceToAnotherDataStructuresMemberNamesItsMinter() {
        var references = ofDocument(library("{\"x\": {\"$ref\": \"" + bravoMember + ":1.0.0\"}}", ""));

        assertThat(references).containsExactly(new ForeignReference(bravoMember, EMBEDDED_PINNED, bravoGrouping));
    }

    @Test
    void aBareDefsMemberOfAnotherDataStructureIsForeign() {
        var references = ofDocument(library("{}", ", \"Stolen\": {\"$ref\": \"" + bravoMember + ":1.0.0\"}"));

        assertThat(references).containsExactly(new ForeignReference(bravoMember, EMBEDDED_PINNED, bravoGrouping));
    }

    @Test
    void aTopLevelReferenceToAnotherDataStructureIsMintedByThatDataStructure() {
        var references = ofDocument("{\"$ref\": \"" + bravoGrouping + ":1.0.0\"}");

        assertThat(references).containsExactly(new ForeignReference(bravoGrouping, EMBEDDED_PINNED, bravoGrouping));
    }

    @Test
    void aLogicalOrLatestReferenceIsNotPinned() {
        var references = ofDocument(library(
            "{\"a\": {\"$ref\": \"" + bravoMember + "\"}, \"b\": {\"$ref\": \"" + bravoGrouping + ":latest\"}}", ""));

        assertThat(references)
            .extracting(ForeignReference::target, ForeignReference::kind)
            .containsExactlyInAnyOrder(
                tuple(bravoMember, EMBEDDED_FLOATING),
                tuple(bravoGrouping, EMBEDDED_FLOATING));
    }

    @Test
    void oneUnpinnedReferenceAmongPinnedOnesMakesTheTargetUnpinned() {
        var references = ofDocument(library(
            "{\"a\": {\"$ref\": \"" + bravoMember + ":1.0.0\"}, \"b\": {\"$ref\": \"" + bravoMember + "\"}}", ""));

        assertThat(references).singleElement().extracting(ForeignReference::kind).isEqualTo(EMBEDDED_FLOATING);
    }

    /** A link embeds nothing, so its target is reported as linked only. */
    @Test
    void anXCoreRefLinkIsForeignButNotEmbedded() {
        var references = ofDocument(library(
            "{\"x\": {\"type\": \"string\", \"x-core-ref\": {\"type\": \"" + bravoMember + "\"}}}", ""));

        assertThat(references).containsExactly(new ForeignReference(bravoMember, LINKED, bravoGrouping));
    }

    @Test
    void anXCoreRefCategoryMarkerAndAnIdAreNoReferences() {
        var references = ofDocument(library(
            "{\"x\": {\"type\": \"string\", \"x-core-ref\": {\"type\": \"urn:core:type:Element\"}}}",
            ", \"Hijack\": {\"$id\": \"" + bravoMember + "\", \"type\": \"object\"}"));

        assertThat(references).isEmpty();
    }

    @Test
    void aTargetTheRegistryDoesNotHoldHasNoMinter() {
        String unknown = "urn:core:platform:civitas:element:common:" + UUID.randomUUID() + ":1.0.0";

        var references = ofDocument(library("{\"x\": {\"$ref\": \"" + unknown + "\"}}", ""));

        assertThat(references).containsExactly(
            new ForeignReference(UrnParser.logicalUrn(unknown), EMBEDDED_PINNED, null));
    }

    /** A bare reference makes the host list another Data structure's member; listing does not mint it. */
    @Test
    void anElementTheHostListsButAnotherDataStructureMintedStaysForeign() {
        when(registry.dataStructureMembersOfAnyVersion(hostGrouping)).thenReturn(List.of(bravoMember));
        when(registry.dataStructuresListing(bravoMember)).thenReturn(List.of(bravoGrouping, hostGrouping));

        var references = ofDocument(library("{\"x\": {\"$ref\": \"" + bravoMember + ":1.0.0\"}}", ""));

        assertThat(references).containsExactly(new ForeignReference(bravoMember, EMBEDDED_PINNED, bravoGrouping));
    }

    @Test
    void anElementTheHostListsAndNoDataStructureMintedIsOwn() {
        String renamed = "urn:core:platform:civitas:element:common:" + UUID.randomUUID();
        held.add(renamed);
        when(registry.dataStructureMembersOfAnyVersion(hostGrouping)).thenReturn(List.of(renamed));
        when(registry.dataStructuresListing(renamed)).thenReturn(List.of(hostGrouping));
        when(registry.dataStructureMemberKeys(hostGrouping)).thenReturn(Set.of("Renamed-2"));

        assertThat(ofDocument(library("{\"x\": {\"$ref\": \"" + renamed + ":1.0.0\"}}", ""))).isEmpty();
    }

    @Test
    void aMemberOfAnEarlierVersionOfTheHostIsOwn() {
        String earlier = urns.member(HOST, "OnlyInVersionOne");
        held.add(earlier);
        when(registry.dataStructuresListing(earlier)).thenReturn(List.of(hostGrouping));
        when(registry.dataStructureMemberKeys(hostGrouping)).thenReturn(Set.of("OnlyInVersionOne"));

        assertThat(ofDocument(library("{\"x\": {\"$ref\": \"" + earlier + "\"}}", ""))).isEmpty();
    }

    /** The host as the registry stores it: its member holds the reference to Bravo's member. */
    private String storedHostReferencingBravo() {
        String stored = hostGrouping + ":1.0.0";
        String root = urns.member(HOST, "Root") + ":1.0.0";
        when(registry.fetch(stored)).thenReturn(Optional.of(json("""
            {"$ref": "%1$s", "$defs": {"Root": {"$ref": "%1$s"}}}
            """.formatted(root))));
        when(registry.fetch(root)).thenReturn(Optional.of(json("""
            {"type": "object", "properties": {"x": {"$ref": "%s:1.0.0"}}}
            """.formatted(bravoMember))));
        return stored;
    }

    @Test
    void aReferenceTheStoredVersionAlreadyHoldsIsLeftOut() {
        String stored = storedHostReferencingBravo();

        var references = resolver.resolve(new ForeignReferencesQuery(HOST,
            json(library("{\"x\": {\"$ref\": \"" + bravoMember + ":1.0.0\"}}", "")), new ArtifactId(stored)));

        assertThat(references).isEmpty();
    }

    /** Pinning an already stored reference to another version reads another version, so it counts as new. */
    @Test
    void aReferenceRepinnedToAnotherVersionIsNew() {
        String stored = storedHostReferencingBravo();

        var references = resolver.resolve(new ForeignReferencesQuery(HOST,
            json(library("{\"x\": {\"$ref\": \"" + bravoMember + ":2.0.0\"}}", "")), new ArtifactId(stored)));

        assertThat(references).extracting(ForeignReference::target).containsExactly(bravoMember);
    }

    /** A stored grouping holds bare references only; its members' content holds the rest. */
    @Test
    void aStoredVersionIsAskedAboutTogetherWithTheOwnElementsItReaches() {
        String stored = hostGrouping + ":1.0.0";
        String root = urns.member(HOST, "Root") + ":1.0.0";
        String part = urns.member(HOST, "Part") + ":1.0.0";
        held.add(UrnParser.logicalUrn(stored));
        when(registry.fetch(stored)).thenReturn(Optional.of(json("""
            {"$ref": "%s", "$defs": {"Root": {"$ref": "%s"}, "Part": {"$ref": "%s"}}}
            """.formatted(root, root, part))));
        when(registry.fetch(root)).thenReturn(Optional.of(json("""
            {"type": "object", "properties": {"part": {"$ref": "%s"}}}
            """.formatted(UrnParser.logicalUrn(part)))));
        when(registry.fetch(UrnParser.logicalUrn(part))).thenReturn(Optional.of(json("""
            {"type": "object", "properties": {"x": {"$ref": "%s:1.0.0"}, "back": {"$ref": "%s"}}}
            """.formatted(bravoMember, UrnParser.logicalUrn(root)))));
        when(registry.fetch(UrnParser.logicalUrn(root))).thenReturn(Optional.of(json("{\"type\": \"object\"}")));

        var references = resolver.resolve(new ForeignReferencesQuery(HOST, null, new ArtifactId(stored)));

        assertThat(references).containsExactly(new ForeignReference(bravoMember, EMBEDDED_PINNED, bravoGrouping));
        verify(registry, never()).fetch(bravoMember + ":1.0.0");
    }

    @Test
    void aStoredVersionTheRegistryDoesNotHoldIsRefused() {
        String stored = hostGrouping + ":9.0.0";
        held.remove(hostGrouping);
        when(registry.fetch(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(new ForeignReferencesQuery(HOST, null, new ArtifactId(stored))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
