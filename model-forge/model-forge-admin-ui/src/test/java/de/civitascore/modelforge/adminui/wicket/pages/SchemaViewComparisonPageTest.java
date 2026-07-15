package de.civitascore.modelforge.adminui.wicket.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import java.util.Optional;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Regression coverage for the bug reported against this page: {@link SchemaViewComparisonPage}
 * used to build {@code new ArtifactId(urn)} unconditionally from the (possibly absent) {@code urn}
 * page parameter, which throws {@link IllegalArgumentException} for a blank value — crashing page
 * construction outright instead of rendering a normal "not found" state, unlike every other
 * urn-driven page in this module.
 */
class SchemaViewComparisonPageTest extends AbstractWicketPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rendersGracefullyWithoutAUrnParameter() {
        // The exact scenario that used to crash: hitting the mounted path with no "urn" at all,
        // e.g. a stale bookmark or a hand-typed "/artifacts/views".
        tester.startPage(SchemaViewComparisonPage.class, new PageParameters());

        tester.assertRenderedPage(SchemaViewComparisonPage.class);
        tester.assertNoErrorMessage();
        tester.assertContains("no artifact specified");
        tester.assertContains("\\(not found\\)");
    }

    @Test
    void showsBundledAndInlinedContentForAnExistingArtifact() {
        String urn = "urn:core:platform:civitas:element:common:Thing:1.0.0";
        var id = new ArtifactId(urn);
        when(modelForge.getBundledView(any(SchemaViewQuery.class)))
            .thenReturn(Optional.of(new ArtifactView(id, JSON.readTree("{\"$ref\":\"#/$defs/Thing\"}"))));
        when(modelForge.getInlinedView(any(SchemaViewQuery.class)))
            .thenReturn(Optional.of(new ArtifactView(id, JSON.readTree("{\"type\":\"object\"}"))));

        tester.startPage(SchemaViewComparisonPage.class, new PageParameters().add("urn", urn));

        tester.assertRenderedPage(SchemaViewComparisonPage.class);
        tester.assertNoErrorMessage();
        tester.assertContains(urn);
        tester.assertContains("different");
        // Rendered inside a <pre>, so JSON quotes come through HTML-escaped (&quot;) — assert on
        // the unescaped substrings instead of literal JSON syntax.
        assertThat(tester.getLastResponseAsString()).contains("$defs/Thing").contains("object");
    }
}
