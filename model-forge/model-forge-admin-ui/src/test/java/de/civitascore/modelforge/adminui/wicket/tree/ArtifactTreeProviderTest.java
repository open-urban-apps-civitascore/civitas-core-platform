package de.civitascore.modelforge.adminui.wicket.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the actual new logic behind the tree redesign: lazy one-hop expansion along each
 * relation axis, and — the part most worth pinning down with a test — cycle safety, since a real
 * dependency/mapping graph is not guaranteed to be acyclic and an unguarded expansion would let the
 * tree grow forever along a cycle.
 */
class ArtifactTreeProviderTest {

    private static final String A = "urn:core:platform:civitas:element:common:A:1.0.0";
    private static final String B = "urn:core:platform:civitas:element:common:B:1.0.0";
    private static final String C = "urn:core:platform:civitas:element:common:C:1.0.0";

    private ModelForge modelForge;
    private ArtifactTreeProvider provider;

    @BeforeEach
    void setUp() {
        modelForge = mock(ModelForge.class);
        provider = new ArtifactTreeProvider(modelForge);
    }

    @Test
    void reportsEmptyForAnEmptyRegistry() {
        when(modelForge.search(any(ArtifactSearchQuery.class))).thenReturn(List.of());

        assertThat(provider.isEmpty()).isTrue();
        assertThat(list(provider.getRoots())).isEmpty();
    }

    @Test
    void groupsRootArtifactsByType() {
        when(modelForge.search(any(ArtifactSearchQuery.class))).thenReturn(List.of(
            summary(A, "element"),
            summary(B, "mapping")
        ));

        List<ArtifactTreeNode> roots = list(provider.getRoots());

        assertThat(roots).hasSize(2);
        // TYPE_ORDER lists "element" before "mapping" regardless of search-result order.
        assertThat(((ArtifactTreeNode.TypeGroup) roots.get(0)).type()).isEqualTo("element");
        assertThat(((ArtifactTreeNode.TypeGroup) roots.get(0)).label()).contains("Elements").contains("1");
        assertThat(((ArtifactTreeNode.TypeGroup) roots.get(1)).type()).isEqualTo("mapping");
    }

    @Test
    void expandingATypeGroupListsItsArtifacts() {
        when(modelForge.search(any(ArtifactSearchQuery.class))).thenReturn(List.of(summary(A, "element")));
        ArtifactTreeNode.TypeGroup group = (ArtifactTreeNode.TypeGroup) list(provider.getRoots()).get(0);

        List<ArtifactTreeNode> children = list(provider.getChildren(group));

        assertThat(children).hasSize(1);
        ArtifactTreeNode.Artifact artifact = (ArtifactTreeNode.Artifact) children.get(0);
        assertThat(artifact.urn()).isEqualTo(A);
        assertThat(artifact.ancestorUrns()).isEmpty();
        assertThat(artifact.cyclic()).isFalse();
    }

    @Test
    void artifactWithNoRelationsShowsAMessageLeaf() {
        stubNoRelations(A);

        ArtifactTreeNode.Artifact artifact = rootArtifact(A);
        List<ArtifactTreeNode> children = list(provider.getChildren(artifact));

        assertThat(children).hasSize(1);
        assertThat(children.get(0)).isInstanceOf(ArtifactTreeNode.Message.class);
        assertThat(provider.hasChildren(artifact)).isTrue(); // the Message leaf itself
    }

    @Test
    void aSelfReferencingMappingEdgeIsFilteredOut() {
        // The facade includes a "self" node in its 1-hop views (per EmbeddedModelForgeOperations);
        // fetchRelated must not surface that as a related artifact.
        ArtifactId self = new ArtifactId(C);
        when(modelForge.dependencies(eq(new DependencyQuery(self)))).thenReturn(emptyGraph());
        when(modelForge.dependents(eq(new DependencyQuery(self)))).thenReturn(emptyGraph());
        when(modelForge.mappedFrom(eq(new DependencyQuery(self)))).thenReturn(emptyGraph());
        when(modelForge.mapsTo(eq(new DependencyQuery(self))))
            .thenReturn(new DependencyGraphView(List.of(new DependencyGraphView.Node(self, "C")), List.of()));

        ArtifactTreeNode.Artifact artifact =
            new ArtifactTreeNode.Artifact(C, "C", "1.0.0", "element", List.of(), false);
        List<ArtifactTreeNode> children = list(provider.getChildren(artifact));

        assertThat(children).singleElement().isInstanceOf(ArtifactTreeNode.Message.class);
    }

    @Test
    void aDependencyCycleTerminatesInsteadOfExpandingForever() {
        ArtifactId idA = new ArtifactId(A);
        ArtifactId idB = new ArtifactId(B);
        stubNoRelations(A);
        stubNoRelations(B);
        when(modelForge.dependencies(eq(new DependencyQuery(idA))))
            .thenReturn(new DependencyGraphView(List.of(new DependencyGraphView.Node(idB, "B")), List.of()));
        when(modelForge.dependencies(eq(new DependencyQuery(idB))))
            .thenReturn(new DependencyGraphView(List.of(new DependencyGraphView.Node(idA, "A")), List.of()));

        ArtifactTreeNode.Artifact artifactA =
            new ArtifactTreeNode.Artifact(A, "A", "1.0.0", "element", List.of(), false);

        // A -> [Dependencies] -> B (not cyclic, still expandable)
        ArtifactTreeNode.RelationGroup depsOfA = onlyRelationGroup(provider.getChildren(artifactA));
        ArtifactTreeNode.Artifact bUnderA = onlyArtifact(provider.getChildren(depsOfA));
        assertThat(bUnderA.urn()).isEqualTo(B);
        assertThat(bUnderA.cyclic()).isFalse();
        assertThat(provider.hasChildren(bUnderA)).isTrue();

        // B -> [Dependencies] -> A, but A is already an ancestor on this branch: cyclic, and a
        // dead end — no further children, so the tree cannot expand along this cycle forever.
        ArtifactTreeNode.RelationGroup depsOfB = onlyRelationGroup(provider.getChildren(bUnderA));
        ArtifactTreeNode.Artifact aUnderB = onlyArtifact(provider.getChildren(depsOfB));
        assertThat(aUnderB.urn()).isEqualTo(A);
        assertThat(aUnderB.cyclic()).isTrue();
        assertThat(provider.hasChildren(aUnderB)).isFalse();
        assertThat(list(provider.getChildren(aUnderB))).isEmpty();
    }

    @Test
    void twoOccurrencesOfTheSameArtifactOnDifferentBranchesAreIndependentNodes() {
        // Same urn, different ancestor chains -> distinct node identity, per the equals/hashCode
        // records already give us for free, but worth pinning down explicitly since the tree's
        // expand/collapse state correctness depends on it.
        ArtifactTreeNode.Artifact viaX = new ArtifactTreeNode.Artifact(C, "C", "1.0.0", "element", List.of("X"), false);
        ArtifactTreeNode.Artifact viaY = new ArtifactTreeNode.Artifact(C, "C", "1.0.0", "element", List.of("Y"), false);

        assertThat(viaX).isNotEqualTo(viaY);
    }

    private ArtifactTreeNode.Artifact rootArtifact(String urn) {
        when(modelForge.search(any(ArtifactSearchQuery.class))).thenReturn(List.of(summary(urn, "element")));
        ArtifactTreeNode.TypeGroup group = (ArtifactTreeNode.TypeGroup) list(provider.getRoots()).get(0);
        return (ArtifactTreeNode.Artifact) list(provider.getChildren(group)).get(0);
    }

    private void stubNoRelations(String urn) {
        ArtifactId id = new ArtifactId(urn);
        DependencyQuery query = new DependencyQuery(id);
        when(modelForge.dependencies(eq(query))).thenReturn(emptyGraph());
        when(modelForge.dependents(eq(query))).thenReturn(emptyGraph());
        when(modelForge.mapsTo(eq(query))).thenReturn(emptyGraph());
        when(modelForge.mappedFrom(eq(query))).thenReturn(emptyGraph());
    }

    private static ArtifactTreeNode.RelationGroup onlyRelationGroup(java.util.Iterator<? extends ArtifactTreeNode> it) {
        List<ArtifactTreeNode> children = list(it);
        assertThat(children).singleElement().isInstanceOf(ArtifactTreeNode.RelationGroup.class);
        return (ArtifactTreeNode.RelationGroup) children.get(0);
    }

    private static ArtifactTreeNode.Artifact onlyArtifact(java.util.Iterator<? extends ArtifactTreeNode> it) {
        List<ArtifactTreeNode> children = list(it);
        assertThat(children).singleElement().isInstanceOf(ArtifactTreeNode.Artifact.class);
        return (ArtifactTreeNode.Artifact) children.get(0);
    }

    private static ArtifactSummary summary(String urn, String type) {
        return new ArtifactSummary(new ArtifactId(urn), type, urn, "1.0.0", "json-schema");
    }

    private static DependencyGraphView emptyGraph() {
        return new DependencyGraphView(List.of(), List.of());
    }

    private static List<ArtifactTreeNode> list(java.util.Iterator<? extends ArtifactTreeNode> it) {
        List<ArtifactTreeNode> result = new java.util.ArrayList<>();
        it.forEachRemaining(result::add);
        return result;
    }
}
