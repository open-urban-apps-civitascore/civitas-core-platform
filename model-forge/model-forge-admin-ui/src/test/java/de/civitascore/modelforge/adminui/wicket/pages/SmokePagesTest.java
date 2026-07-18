package de.civitascore.modelforge.adminui.wicket.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactView;
import java.util.Optional;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Render-without-exception coverage for the pages that don't otherwise have a bug-specific test:
 * every page here has non-trivial construction logic (facade calls, editor bootstrap, form state)
 * that a plain compile check would not exercise, so each still gets a smoke assertion.
 */
class SmokePagesTest extends AbstractWicketPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void importPageRenders() {
        tester.startPage(ImportPage.class);
        tester.assertRenderedPage(ImportPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void seedPageRenders() {
        tester.startPage(SeedPage.class);
        tester.assertRenderedPage(SeedPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void smartDataModelImportPageRenders() {
        tester.startPage(SmartDataModelImportPage.class);
        tester.assertRenderedPage(SmartDataModelImportPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void validatePageRenders() {
        tester.startPage(ValidatePage.class);
        tester.assertRenderedPage(ValidatePage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void graphPageRendersWithAnEmptyRegistry() {
        // modelForge.search(...) defaults to List.of() (AbstractWicketPageTest) — GraphPage never
        // reaches its per-artifact dependencies()/mapsTo() calls in that case.
        tester.startPage(GraphPage.class);
        tester.assertRenderedPage(GraphPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void graphPageWiresClickToNavigateToTheArtifactViewMountPath() {
        tester.startPage(GraphPage.class);

        String body = tester.getLastResponseAsString();
        assertThat(body).contains("network.on('click'");
        assertThat(body).contains(ArtifactViewPage.MOUNT_PATH + "?urn=");
        // Dark-mode-aware label color: read at runtime, not hardcoded to the light-mode value.
        assertThat(body).contains("document.documentElement.getAttribute('data-theme')");
    }

    @Test
    void everyPageRendersTheThemeToggleInItsInitialLightModeState() {
        tester.startPage(ImportPage.class);

        String body = tester.getLastResponseAsString();
        assertThat(body).contains("id=\"mf-theme-toggle\"");
        // The button's *markup* always starts as "Dark" (the label to switch TO) — THEME_INIT_JS
        // corrects it client-side from localStorage/OS preference on actual page load, which
        // WicketTester's plain HTTP-level rendering does not execute.
        assertThat(body).contains(">Dark<");
    }

    @Test
    void everyPageWiresTheSidebarQuickJumpShortcut() {
        tester.startPage(ImportPage.class);

        String body = tester.getLastResponseAsString();
        assertThat(body).contains("e.key!=='/'");
        assertThat(body).contains("mf-sidebar-filter");
    }

    @Test
    void artifactEditPageRendersBlankForANewArtifact() {
        tester.startPage(ArtifactEditPage.class);
        tester.assertRenderedPage(ArtifactEditPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void artifactEditPageRendersExistingContentWhenEditing() {
        String urn = "urn:core:platform:civitas:element:common:Thing:1.0.0";
        ArtifactId id = new ArtifactId(urn);
        when(modelForge.getArtifact(eq(id)))
            .thenReturn(Optional.of(new ArtifactView(id, JSON.readTree("{\"type\":\"object\"}"))));

        tester.startPage(ArtifactEditPage.class, new PageParameters().add("urn", urn));

        tester.assertRenderedPage(ArtifactEditPage.class);
        tester.assertNoErrorMessage();
        // The pre-loaded content renders inside the CodeEditorPanel's textarea/viewer, where Wicket
        // HTML-escapes the JSON quotes ({"type":"object"} → {&quot;type&quot;:&quot;object&quot;}).
        tester.assertContains("&quot;type&quot;");
    }
}
