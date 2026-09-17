package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * How the graph classifies a node it reached by an edge: the registry decides whether an artifact is
 * held, so an artifact the search page did not happen to list must not read as missing.
 */
@DisplayName("GraphPage node grouping")
class GraphPageTest extends AbstractWicketPageTest {

    private static final String LISTED =
        "urn:core:platform:civitas:element:common:Listed:aaaaaaaaaa:1.0.0";
    private static final String UNLISTED_STRUCTURE =
        "urn:core:platform:civitas:datastructure:common:Unlisted:bbbbbbbbbb:1.0.0";
    private static final String GHOST =
        "urn:core:platform:civitas:element:common:Ghost:cccccccccc:1.0.0";

    /** The search page lists only {@code LISTED}, standing in for its row limit. */
    private void searchListsOnlyTheOneArtifact() {
        when(modelForge.search(any(ArtifactSearchQuery.class)))
            .thenReturn(List.of(new ArtifactSummary(
                new ArtifactId(LISTED), "element", "Listed", "1.0.0", "json-schema")));
    }

    /** {@code LISTED} depends on {@code target}, so the graph reaches a node search never listed. */
    private void listedDependsOn(String target) {
        when(modelForge.dependencies(any(DependencyQuery.class)))
            .thenReturn(new DependencyGraphView(
                List.of(
                    new DependencyGraphView.Node(new ArtifactId(LISTED), "Listed"),
                    new DependencyGraphView.Node(new ArtifactId(target), "Target")),
                List.of(new DependencyGraphView.Edge(
                    new ArtifactId(LISTED), new ArtifactId(target), "depends-on"))));
        when(modelForge.mapsTo(any(DependencyQuery.class)))
            .thenReturn(new DependencyGraphView(List.of(), List.of()));
    }

    private String renderGraph() {
        tester.startPage(GraphPage.class);
        tester.assertRenderedPage(GraphPage.class);
        return tester.getLastResponseAsString();
    }

    private static String logical(String versionedUrn) {
        return new ArtifactId(versionedUrn).logicalUrn();
    }

    @Test
    @DisplayName("a reference target the registry does not hold renders as unresolved")
    void unknownTargetIsUnresolved() {
        searchListsOnlyTheOneArtifact();
        listedDependsOn(GHOST);
        when(modelForge.existing(any())).thenReturn(Set.of());

        String rendered = renderGraph();

        assertThat(rendered).contains(logical(GHOST));
        assertThat(rendered).contains("\"group\":\"unresolved\"");
        assertThat(rendered)
            .as("the edge is kept: it still states what the document said")
            .contains(logical(LISTED));
    }

    @Test
    @DisplayName("a held reference target the search page did not list is grouped by its kind")
    void heldTargetBeyondTheSearchPageIsNotUnresolved() {
        searchListsOnlyTheOneArtifact();
        listedDependsOn(UNLISTED_STRUCTURE);
        when(modelForge.existing(any()))
            .thenReturn(Set.of(new ArtifactId(logical(UNLISTED_STRUCTURE))));

        String rendered = renderGraph();

        assertThat(rendered)
            .as("the registry holds it, so it is not a missing reference")
            .doesNotContain("\"group\":\"unresolved\"");
        assertThat(rendered).contains("\"group\":\"datastructure\"");
    }
}
