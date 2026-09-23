package de.civitascore.modelforge.adminui.wicket.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTree;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import java.util.List;
import java.util.Optional;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ArtifactViewPage} already guards a blank/missing {@code urn} correctly — this locks that
 * "not found" behaviour in as a regression test — and exercises the happy path with all four
 * relation axes populated (dependencies, dependents, maps-to, mapped-from) plus the delete action.
 */
class ArtifactViewPageTest extends AbstractWicketPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URN = "urn:core:platform:civitas:element:common:Thing:1.0.0";

    @Test
    void rendersNotFoundForABlankUrn() {
        tester.startPage(ArtifactViewPage.class, new PageParameters());

        tester.assertRenderedPage(ArtifactViewPage.class);
        tester.assertNoErrorMessage();
        tester.assertInvisible("editLink");
        tester.assertInvisible("deleteLink");
        tester.assertInvisible("copyUrnButton");
        tester.assertComponent("notFound", org.apache.wicket.markup.html.WebMarkupContainer.class);
    }

    @Test
    void copyUrnButtonCarriesTheFullUrnForAnExistingArtifact() {
        ArtifactId self = new ArtifactId(URN);
        when(modelForge.getArtifact(eq(self)))
            .thenReturn(Optional.of(new ArtifactView(self, JSON.readTree("{\"type\":\"object\"}"))));
        when(modelForge.dependencies(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.dependents(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mapsTo(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mappedFrom(any(DependencyQuery.class))).thenReturn(empty());

        tester.startPage(ArtifactViewPage.class, new PageParameters().add("urn", URN));

        tester.assertVisible("copyUrnButton");
        assertThat(tester.getLastResponseAsString()).contains("data-urn=\"" + URN + "\"");
    }

    @Test
    void rendersAnExistingArtifactWithAllFourRelationAxes() {
        ArtifactId self = new ArtifactId(URN);
        when(modelForge.getArtifact(eq(self)))
            .thenReturn(Optional.of(new ArtifactView(self, JSON.readTree("{\"type\":\"object\"}"))));
        when(modelForge.dependencies(any(DependencyQuery.class))).thenReturn(oneRelated("Sensor"));
        when(modelForge.dependents(any(DependencyQuery.class))).thenReturn(oneRelated("Dataset"));
        when(modelForge.mapsTo(any(DependencyQuery.class))).thenReturn(oneRelated("MappingA"));
        when(modelForge.mappedFrom(any(DependencyQuery.class))).thenReturn(oneRelated("MappingB"));

        tester.startPage(ArtifactViewPage.class, new PageParameters().add("urn", URN));

        tester.assertRenderedPage(ArtifactViewPage.class);
        tester.assertNoErrorMessage();
        tester.assertVisible("editLink");
        tester.assertVisible("deleteLink");
        tester.assertContains("Sensor");
        tester.assertContains("Dataset");
        tester.assertContains("MappingA");
        tester.assertContains("MappingB");
    }

    @Test
    void deletingNavigatesBackToTheListPage() {
        ArtifactId self = new ArtifactId(URN);
        when(modelForge.getArtifact(eq(self)))
            .thenReturn(Optional.of(new ArtifactView(self, JSON.readTree("{\"type\":\"object\"}"))));
        when(modelForge.dependencies(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.dependents(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mapsTo(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mappedFrom(any(DependencyQuery.class))).thenReturn(empty());

        tester.startPage(ArtifactViewPage.class, new PageParameters().add("urn", URN));
        tester.clickLink("deleteLink");

        tester.assertRenderedPage(ArtifactListPage.class);
    }

    @Test
    void theSidebarMarksTheOpenArtifactActive() {
        ArtifactId self = new ArtifactId(URN);
        when(modelForge.getArtifact(eq(self)))
            .thenReturn(Optional.of(new ArtifactView(self, JSON.readTree("{\"type\":\"object\"}"))));
        when(modelForge.dependencies(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.dependents(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mapsTo(any(DependencyQuery.class))).thenReturn(empty());
        when(modelForge.mappedFrom(any(DependencyQuery.class))).thenReturn(empty());
        // The sidebar tree is populated from search(), so the open artifact is one of its nodes.
        when(modelForge.search(any(ArtifactSearchQuery.class)))
            .thenReturn(List.of(new ArtifactSummary(self, "element", "Thing", "1.0.0", "json-schema")));

        tester.startPage(ArtifactViewPage.class, new PageParameters().add("urn", URN));

        tester.assertNoErrorMessage();
        // Artifact nodes are lazy children of their type group, so the "active" class is not in the
        // initial markup — assert the tree was handed the open artifact instead. This is null when
        // currentUrn() is read from the base-page constructor, before the subclass assigns its urn.
        ArtifactTree tree = (ArtifactTree) tester.getLastRenderedPage().get("artifactTree");
        assertThat(tree.currentUrn()).isEqualTo(URN);
    }

    private static DependencyGraphView oneRelated(String name) {
        ArtifactId related = new ArtifactId("urn:core:platform:civitas:element:common:" + name + ":1.0.0");
        return new DependencyGraphView(List.of(new DependencyGraphView.Node(related, name)), List.of());
    }

    private static DependencyGraphView empty() {
        return new DependencyGraphView(List.of(), List.of());
    }
}
