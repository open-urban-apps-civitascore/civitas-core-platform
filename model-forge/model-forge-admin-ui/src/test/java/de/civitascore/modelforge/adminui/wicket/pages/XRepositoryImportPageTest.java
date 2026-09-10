package de.civitascore.modelforge.adminui.wicket.pages;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.wicket.AbstractWicketPageTest;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportXRepositoryCommand;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.XRepositoryHit;
import de.civitascore.modelforge.contract.XRepositorySearchQuery;
import java.util.List;
import java.util.Map;
import org.apache.wicket.util.tester.FormTester;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for {@link XRepositoryImportPage}: {@code importFromXRepository} now either
 * returns one valid {@link ImportResult} or throws {@link ValidationFailedException} — there is
 * no more "succeeded but produced zero artifacts" gap to fall through unguarded.
 */
class XRepositoryImportPageTest extends AbstractWicketPageTest {

    private static final XRepositoryHit HIT =
        new XRepositoryHit("xmeld.1.0", null, "XMeld", "1.0", "desc", "standard", "released");

    @Test
    void importFailureShowsTheValidationMessageInsteadOfCrashing() {
        when(modelForge.searchXRepository(any(XRepositorySearchQuery.class))).thenReturn(List.of(HIT));
        when(modelForge.importFromXRepository(any(ImportXRepositoryCommand.class)))
            .thenThrow(new ValidationFailedException("XRepository import validation failed", List.of()));

        tester.startPage(XRepositoryImportPage.class);
        FormTester form = tester.newFormTester("searchForm");
        form.setValue("query", "xmeld");
        form.submit();
        tester.assertNoErrorMessage();

        tester.clickLink("importForm:resultsContainer:rows:0:importJson");

        tester.assertRenderedPage(XRepositoryImportPage.class);
        tester.assertContains("Import failed: XRepository import validation failed");
    }

    @Test
    void importProducingAnArtifactNavigatesToItsViewPage() {
        ArtifactId rootId = new ArtifactId("urn:core:platform:civitas:element:xoev:XMeld:1.0.0");
        when(modelForge.searchXRepository(any(XRepositorySearchQuery.class))).thenReturn(List.of(HIT));
        when(modelForge.importFromXRepository(any(ImportXRepositoryCommand.class)))
            .thenReturn(new ImportResult(rootId, List.of(rootId), Map.of()));

        tester.startPage(XRepositoryImportPage.class);
        FormTester form = tester.newFormTester("searchForm");
        form.setValue("query", "xmeld");
        form.submit();

        tester.clickLink("importForm:resultsContainer:rows:0:importJson");

        tester.assertRenderedPage(ArtifactViewPage.class);
        tester.assertNoErrorMessage();
    }
}
