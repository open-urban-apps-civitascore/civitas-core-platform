package de.civitascore.modelforge.adminui.wicket.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import java.util.List;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.util.tester.FormTester;
import org.junit.jupiter.api.Test;

class ArtifactListPageTest extends AbstractWicketPageTest {

    @Test
    void rendersWithNoResultsInitially() {
        tester.startPage(ArtifactListPage.class);

        tester.assertRenderedPage(ArtifactListPage.class);
        tester.assertNoErrorMessage();
    }

    @Test
    void statsStripIsHiddenForAnEmptyRegistry() {
        tester.startPage(ArtifactListPage.class);

        WebMarkupContainer statsStrip =
            (WebMarkupContainer) tester.getLastRenderedPage().get("statsStrip");
        assertThat(statsStrip.isVisible()).isFalse();
    }

    @Test
    void statsStripShowsTotalAndPerTypeCountsInCatalogOrder() {
        when(modelForge.search(any(ArtifactSearchQuery.class))).thenReturn(List.of(
            summary("A", "element"),
            summary("B", "element"),
            summary("C", "mapping")
        ));

        tester.startPage(ArtifactListPage.class);
        tester.assertNoErrorMessage();

        WebMarkupContainer statsStrip =
            (WebMarkupContainer) tester.getLastRenderedPage().get("statsStrip");
        assertThat(statsStrip.isVisible()).isTrue();
        assertThat(statsStrip.get("totalCount").getDefaultModelObjectAsString()).isEqualTo("3");

        var statRows = (org.apache.wicket.markup.html.list.ListView<?>) statsStrip.get("statRows");
        List<?> rows = statRows.getList();
        // TYPE_ORDER lists "element" before "mapping" regardless of search-result order.
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).toString()).contains("element", "Elements", "2");
        assertThat(rows.get(1).toString()).contains("mapping", "Mappings", "1");
    }

    private static ArtifactSummary summary(String name, String type) {
        return new ArtifactSummary(
            new ArtifactId("urn:core:platform:civitas:element:common:" + name + ":1.0.0"),
            type, name, "1.0.0", "json-schema");
    }

    @Test
    void searchingRendersMatchingRows() {
        ArtifactId id = new ArtifactId("urn:core:platform:civitas:element:common:Thing:1.0.0");
        when(modelForge.search(any(ArtifactSearchQuery.class)))
            .thenReturn(List.of(new ArtifactSummary(id, "element", "Thing", "1.0.0", "json-schema")));

        tester.startPage(ArtifactListPage.class);
        FormTester form = tester.newFormTester("searchForm");
        form.setValue("text", "Thing");
        form.submit();

        tester.assertNoErrorMessage();
        tester.assertContains("Thing");
        tester.assertContains(id.value());
    }
}
