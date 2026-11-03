package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.urn.UrnParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deletes a version line: the versions of one artifact that share the major of a pin.
 *
 * <p>An Element's line also covers the versions of its own grouping, the Data Structure of the same
 * UUID, that hold one of its versions: an imported model is stored as that pair, and the grouping
 * version is what the first version of the Element was imported with.
 */
final class VersionLineDeletion {

    private static final String DATA_STRUCTURE = "datastructure";

    private final ArtifactRegistry registry;
    private final DependencyGraphService graph;
    private final ElementCommandService artifacts;

    VersionLineDeletion(ArtifactRegistry registry, DependencyGraphService graph, ElementCommandService artifacts) {
        this.registry = registry;
        this.graph = graph;
        this.artifacts = artifacts;
    }

    List<String> blockers(String pin) {
        requirePinned(pin);
        Line line = lineOf(pin);
        return line.isEmpty() ? List.of() : blockersOf(line);
    }

    void delete(String pin) {
        requirePinned(pin);
        String logical = UrnParser.logicalUrn(pin);
        registry.inTransaction(() -> {
            registry.lockForWrite(logical);
            groupingOf(logical).ifPresent(registry::lockForWrite);
            Line line = lineOf(pin);
            if (line.isEmpty()) {
                return null;
            }
            List<String> blockers = blockersOf(line);
            if (!blockers.isEmpty()) {
                throw new ArtifactInUseException(pin, blockers);
            }
            if (line.coversArtifact()) {
                deleteWholeArtifact(line);
            } else {
                deleteVersions(logical, line.versions());
                deleteOwned(line);
            }
            return null;
        });
    }

    /**
     * The existing whole-artifact delete, so cascade and Data Set unlinking follow its rules. A
     * grouping holding the Element goes first, or it would refuse the Element's delete.
     */
    private void deleteWholeArtifact(Line line) {
        if (line.grouping() != null && !line.groupingVersions().isEmpty()) {
            if (line.groupingVersions().containsAll(registry.versionsOf(line.grouping()))) {
                artifacts.delete(line.grouping(), true, false);
            } else {
                deleteVersions(line.grouping(), line.groupingVersions());
                deleteMembers(line.freedMembers());
            }
        }
        if (!registry.versionsOf(line.logical()).isEmpty()) {
            artifacts.delete(line.logical(), true, false);
        }
    }

    private void deleteOwned(Line line) {
        if (line.grouping() != null && !line.groupingVersions().isEmpty()) {
            deleteVersions(line.grouping(), line.groupingVersions());
        }
        deleteMembers(line.freedMembers());
    }

    private void deleteMembers(Collection<String> memberPins) {
        Map<String, List<String>> versionsByMember = new LinkedHashMap<>();
        memberPins.forEach(pin -> versionsByMember
            .computeIfAbsent(UrnParser.logicalUrn(pin), member -> new ArrayList<>())
            .add(UrnParser.versionFromUrn(pin)));
        versionsByMember.forEach(this::deleteVersions);
    }

    /**
     * Deletes the versions, or the artifact when they are all it has. That delete is forced because
     * {@link #blockersOf} already decided under the line's rules, which ignore Data Set membership.
     */
    private void deleteVersions(String logical, Collection<String> versions) {
        if (versions.containsAll(registry.versionsOf(logical))) {
            artifacts.delete(logical, false, true);
            return;
        }
        registry.deleteVersions(logical, versions);
        versions.forEach(version -> registry.afterCommit(() -> graph.removeVersion(UrnParser.withVersion(logical, version))));
    }

    /** References from removed versions are harmless; surviving explicit pins must stay valid. */
    private List<String> blockersOf(Line line) {
        Set<String> siblings = new HashSet<>();
        siblings.add(line.logical());
        Stream.of(line.logical(), line.grouping())
            .filter(urn -> urn != null && DATA_STRUCTURE.equals(UrnParser.artifactTypeFromUrn(urn)))
            .forEach(dataStructure -> {
                siblings.add(dataStructure);
                siblings.addAll(registry.dataStructureMembersOfAnyVersion(dataStructure));
            });
        Set<String> removed = new LinkedHashSet<>(line.removedPins());
        removed.addAll(line.freedMembers());
        return registry.versionDeletionBlockers(removed, siblings);
    }

    private Line lineOf(String pin) {
        String logical = UrnParser.logicalUrn(pin);
        String major = UrnParser.versionFromUrn(pin);
        List<String> all = registry.versionsOf(logical);
        List<String> versions = all.stream().filter(version -> sameMajor(version, major)).toList();
        if (versions.isEmpty()) {
            return Line.EMPTY;
        }
        String grouping = groupingOf(logical).orElse(null);
        List<String> groupingVersions = grouping == null ? List.of() : registry.versionOwners(versions.stream()
            .map(version -> UrnParser.withVersion(logical, version)).toList()).stream()
            .filter(holder -> grouping.equals(UrnParser.logicalUrn(holder)))
            .map(UrnParser::versionFromUrn)
            .distinct()
            .toList();
        Set<String> removed = new LinkedHashSet<>();
        versions.forEach(version -> removed.add(UrnParser.withVersion(logical, version)));
        groupingVersions.forEach(version -> removed.add(UrnParser.withVersion(grouping, version)));
        Set<String> lineArtifacts = new HashSet<>();
        lineArtifacts.add(logical);
        if (grouping != null) {
            lineArtifacts.add(grouping);
        }
        return new Line(logical, versions, versions.size() == all.size(), grouping, groupingVersions,
            List.copyOf(removed), freedMembers(removed, lineArtifacts));
    }

    /**
     * The member versions the removed versions own and no other version owns. A member named
     * without a version is held by whatever is current, so it is not one version to remove.
     */
    private List<String> freedMembers(Set<String> removed, Set<String> lineArtifacts) {
        return registry.exclusivelyOwnedMemberVersions(removed).stream()
            .filter(member -> !lineArtifacts.contains(UrnParser.logicalUrn(member)))
            .toList();
    }

    /** The grouping an imported Element was stored with, when the registry holds it. */
    private Optional<String> groupingOf(String logical) {
        if (!"element".equals(UrnParser.artifactTypeFromUrn(logical))) {
            return Optional.empty();
        }
        String grouping = UrnParser.withArtifactType(logical, DATA_STRUCTURE);
        return registry.versionsOf(grouping).isEmpty() ? Optional.empty() : Optional.of(grouping);
    }

    private static boolean sameMajor(String version, String other) {
        return majorOf(version).equals(majorOf(other));
    }

    private static String majorOf(String version) {
        int dot = version.indexOf('.');
        return dot < 0 ? version : version.substring(0, dot);
    }

    private static boolean isPinned(String urn) {
        return UrnParser.versionFromUrn(urn) != null && !UrnParser.isLatest(urn);
    }

    private static void requirePinned(String urn) {
        UrnParser.requireNoControlChars(urn);
        if (!isPinned(urn)) {
            throw new IllegalArgumentException("A version line is named by a pinned version: " + urn);
        }
    }

    /**
     * @param removedPins the versions of the artifact and of its grouping the delete removes
     * @param freedMembers the member versions only {@code removedPins} own
     */
    private record Line(
        String logical,
        List<String> versions,
        boolean coversArtifact,
        String grouping,
        List<String> groupingVersions,
        List<String> removedPins,
        List<String> freedMembers) {

        static final Line EMPTY = new Line(null, List.of(), false, null, List.of(), List.of(), List.of());

        boolean isEmpty() {
            return versions.isEmpty();
        }
    }
}
