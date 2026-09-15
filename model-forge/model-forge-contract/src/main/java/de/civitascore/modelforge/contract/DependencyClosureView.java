package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/**
 * The transitive dependency closure of one artifact, with the members the registry does not hold
 * called out separately — everything an artifact participates in, and what of it is missing.
 *
 * <p><b>The root is never a member.</b> {@code closure} lists the artifacts reachable <em>from</em>
 * {@code root}, never {@code root} itself, not even when a cycle leads back to it. Membership is
 * decided by logical URN, so no version of the root appears.
 *
 * <p><b>URN form.</b> A resolved member is a <em>versioned</em> URN. An unresolved one is carried
 * <em>verbatim as authored</em> — a pin, a logical URN or a {@code :latest} token — because that is
 * how some artifact spells the reference a caller has to repair.
 *
 * <p>{@code unresolved} is a subset of {@code closure}, and both keep the walk's discovery order.
 *
 * <p><b>The bound may hide members.</b> {@code truncated} says an artifact lies beyond the query's
 * depth bound, so the answer means "not known", not "nothing found".
 *
 * @param root the artifact the closure was taken from, as the query named it
 * @param closure every artifact reachable from {@code root} within the query's depth bound
 * @param unresolved the members of {@code closure} the registry does not hold
 * @param truncated whether the depth bound stopped the walk short of the full closure
 */
public record DependencyClosureView(
    ArtifactId root,
    List<ArtifactId> closure,
    List<ArtifactId> unresolved,
    boolean truncated
) {

    public DependencyClosureView {
        Objects.requireNonNull(root, "root");
        closure = List.copyOf(Objects.requireNonNull(closure, "closure"));
        unresolved = List.copyOf(Objects.requireNonNull(unresolved, "unresolved"));
    }

    /** Whether every member of the closure resolves — the question most callers ask of it. */
    public boolean complete() {
        return unresolved.isEmpty();
    }
}
