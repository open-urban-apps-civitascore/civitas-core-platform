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
        List<Runnable> graphUpdates = new ArrayList<>();
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
                deleteWholeArtifact(line, graphUpdates);
            } else {
                deleteVersions(logical, line.versions(), graphUpdates);
                deleteOwned(line, graphUpdates);
            }
            return null;
        });
        registry.afterCommit(() -> graphUpdates.forEach(Runnable::run));
    }

    /**
     * The existing whole-artifact delete, so cascade and Data Set unlinking follow its rules. A
     * grouping holding the Element goes first, or it would refuse the Element's delete.
     */
    private void deleteWholeArtifact(Line line, List<Runnable> graphUpdates) {
        if (line.grouping() != null && !line.groupingVersions().isEmpty()) {
            if (line.groupingVersions().containsAll(registry.versionsOf(line.grouping()))) {
                artifacts.delete(line.grouping(), true, false);
            } else {
                deleteVersions(line.grouping(), line.groupingVersions(), graphUpdates);
                deleteMembers(line.freedMembers(), graphUpdates);
            }
        }
        if (!registry.versionsOf(line.logical()).isEmpty()) {
            artifacts.delete(line.logical(), true, false);
        }
    }

    private void deleteOwned(Line line, List<Runnable> graphUpdates) {
        if (line.grouping() != null && !line.groupingVersions().isEmpty()) {
            deleteVersions(line.grouping(), line.groupingVersions(), graphUpdates);
        }
        deleteMembers(line.freedMembers(), graphUpdates);
    }

    private void deleteMembers(Collection<String> memberPins, List<Runnable> graphUpdates) {
        Map<String, List<String>> versionsByMember = new LinkedHashMap<>();
        memberPins.forEach(pin -> versionsByMember
            .computeIfAbsent(UrnParser.logicalUrn(pin), member -> new ArrayList<>())
            .add(UrnParser.versionFromUrn(pin)));
        versionsByMember.forEach((member, versions) -> deleteVersions(member, versions, graphUpdates));
    }

    /** Deletes the versions, or the artifact when they are all it has. */
    private void deleteVersions(String logical, Collection<String> versions, List<Runnable> graphUpdates) {
        if (versions.containsAll(registry.versionsOf(logical))) {
            List<String> memberships = registry.dataSetMemberships(logical);
            registry.deleteArtifact(logical);
            memberships.forEach(dataSet -> artifacts.unlinkFromDataSet(dataSet, logical));
            graphUpdates.add(() -> graph.remove(logical));
            return;
        }
        registry.deleteVersions(logical, versions);
        versions.forEach(version -> graphUpdates.add(() -> graph.removeVersion(UrnParser.withVersion(logical, version))));
    }

    /**
     * Asked of every version the delete removes. A reference held from inside the removed set does
     * not count, and neither does one from an Element of any version of the same Data Structure:
     * its {@code $ref} names no version, so it holds this line's member as much as its own.
     */
    private List<String> blockersOf(Line line) {
        Set<String> exempt = new HashSet<>();
        exempt.add(line.logical());
        Stream.of(line.logical(), line.grouping())
            .filter(urn -> urn != null && DATA_STRUCTURE.equals(UrnParser.artifactTypeFromUrn(urn)))
            .forEach(dataStructure -> {
                exempt.add(dataStructure);
                exempt.addAll(registry.dataStructureMembersOfAnyVersion(dataStructure));
            });
        return Stream.of(line.removedPins(), line.freedMembers())
            .flatMap(Collection::stream)
            .flatMap(pin -> registry.liveNonDataSetBlockingDependents(pin).stream())
            .filter(referrer -> !exempt.contains(UrnParser.logicalUrn(referrer)))
            .distinct()
            .toList();
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
        List<String> groupingVersions = grouping == null ? List.of() : versions.stream()
            .flatMap(version -> registry.versionOwners(UrnParser.withVersion(logical, version)).stream())
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
        return removed.stream()
            .flatMap(owner -> registry.ownedMemberUrns(owner, UrnParser.versionFromUrn(owner)).stream())
            .filter(VersionLineDeletion::isPinned)
            .filter(member -> !lineArtifacts.contains(UrnParser.logicalUrn(member)))
            .distinct()
            .filter(member -> removed.containsAll(registry.versionOwners(member)))
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
