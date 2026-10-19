package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/**
 * Directed dependency graph for artifacts known to Model Forge.
 */
public record DependencyGraphView(List<Node> nodes, List<Edge> edges) {

    public DependencyGraphView {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }

    public record Node(ArtifactId artifactId, String label) {

        public Node {
            Objects.requireNonNull(artifactId, "artifactId");
        }
    }

    public record Edge(ArtifactId source, ArtifactId target, String relation) {

        public Edge {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(relation, "relation");
            if (relation.isBlank()) {
                throw new IllegalArgumentException("relation must not be blank");
            }
        }
    }
}
