package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/**
 * The transitive dependency closure of one artifact, with the members the registry does not hold
 * called out separately — everything an artifact participates in, and what of it is missing.
 *
 * <p><b>The root is never a member.</b> {@code closure} lists the artifacts reachable <em>from</em>
 * {@code root}, never {@code root} itself — not even when a reference cycle leads back to it, which
 * the underlying walk does surface. Membership is decided by logical URN, so no version of the root
 * artifact appears. A caller that must judge the root judges it directly; it holds it already.
 *
 * <p><b>URN form.</b> A member the registry resolved is a <em>versioned</em> URN, because the
 * dependency graph's nodes are versioned. A member it could not resolve — a pin whose version was
 * never stored, or a target whose artifact is gone — is carried <em>verbatim as authored</em>, so it
 * may be a pin, a logical URN or a {@code :latest} token. An entry in {@code unresolved} is
 * therefore the reference exactly as some artifact spells it, which is what a caller needs in order
 * to report or repair it.
 *
 * <p>{@code unresolved} is a subset of {@code closure}, and both keep the walk's discovery order.
 *
 * <p><b>The bound may hide members.</b> {@code truncated} says an artifact lies immediately beyond
 * the query's depth bound, so {@code closure} is what the walk reached rather than everything the
 * root participates in. A truncated answer means "not known", not "nothing found".
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
