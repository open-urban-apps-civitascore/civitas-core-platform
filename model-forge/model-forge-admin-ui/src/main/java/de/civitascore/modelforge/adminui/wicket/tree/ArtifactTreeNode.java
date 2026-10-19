package de.civitascore.modelforge.adminui.wicket.tree;

import java.io.Serializable;
import java.util.List;

/**
 * Node model for the sidebar's lazily-expanding artifact tree ({@link ArtifactTreeProvider}). Four
 * kinds, forming a strictly-alternating structure as the user drills down:
 *
 * <pre>
 * TypeGroup (Elements, DataStructures, ...)
 *   └─ Artifact (a concrete artifact)
 *        └─ RelationGroup (Dependencies / Dependents / Maps To / Mapped From)
 *             └─ Artifact (a related artifact — expandable again, unless {@code cyclic})
 *                  └─ RelationGroup ...  (unbounded depth)
 * </pre>
 *
 * <p>{@link Artifact#ancestorUrns()} carries the chain of ancestor artifact URNs from the root down
 * to (but not including) this node, purely so {@link ArtifactTreeProvider} can detect a relation
 * edge pointing back into that chain (a real possibility along dependency/mapping edges) and mark
 * the resulting node {@link Artifact#cyclic()} — non-expandable, so a genuine cycle in the
 * underlying graph can never make the tree expand forever. Two {@code Artifact} nodes for the same
 * URN reached via different branches are intentionally distinct tree nodes (different
 * {@code ancestorUrns}), each with its own independent expand/collapse state.
 */
public sealed interface ArtifactTreeNode extends Serializable {

    /** Top-level grouping by artifact type (element, datastructure, ...), as in the old flat list. */
    record TypeGroup(String type, String label) implements ArtifactTreeNode {
    }

    /** A concrete artifact, reached either as a top-level search hit or via a relation edge. */
    record Artifact(
        String urn, String name, String version, String type, List<String> ancestorUrns, boolean cyclic)
        implements ArtifactTreeNode {

        public Artifact {
            ancestorUrns = List.copyOf(ancestorUrns);
        }
    }

    /** One relation axis under an artifact: Dependencies, Dependents, Maps To, or Mapped From. */
    record RelationGroup(RelationKind kind, String ownerUrn, List<String> ancestorUrns)
        implements ArtifactTreeNode {

        public RelationGroup {
            ancestorUrns = List.copyOf(ancestorUrns);
        }
    }

    /** A non-expandable informational leaf ("No artifacts", "No relations"). */
    record Message(String text) implements ArtifactTreeNode {
    }

    /** The four relation axes exposed by {@link de.civitascore.modelforge.facade.ModelForge}. */
    enum RelationKind {
        DEPENDENCIES("Dependencies"),
        DEPENDENTS("Dependents"),
        MAPS_TO("Maps To"),
        MAPPED_FROM("Mapped From");

        private final String label;

        RelationKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
