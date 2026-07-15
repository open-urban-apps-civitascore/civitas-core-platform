package de.civitascore.modelforge.adminui.wicket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.modelforge.adminui.seed.SeedImporter;
import de.civitascore.modelforge.adminui.wicket.pages.ArtifactEditPage;
import de.civitascore.modelforge.adminui.wicket.pages.ArtifactListPage;
import de.civitascore.modelforge.adminui.wicket.pages.ArtifactViewPage;
import de.civitascore.modelforge.adminui.wicket.pages.GraphPage;
import de.civitascore.modelforge.adminui.wicket.pages.ImportPage;
import de.civitascore.modelforge.adminui.wicket.pages.SchemaViewComparisonPage;
import de.civitascore.modelforge.adminui.wicket.pages.SeedPage;
import de.civitascore.modelforge.adminui.wicket.pages.SmartDataModelImportPage;
import de.civitascore.modelforge.adminui.wicket.pages.ValidatePage;
import de.civitascore.modelforge.adminui.wicket.pages.XRepositoryImportPage;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.List;
import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.spring.test.ApplicationContextMock;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared {@link WicketTester} setup for admin-ui page tests: a real {@link WebApplication} (so
 * every page mount/link resolves exactly as in production) wired to a Mockito mock of {@link
 * ModelForge} via {@link SpringComponentInjector}'s explicit-context constructor — the same
 * dependency-injection mechanism {@link AdminWicketApplication} uses, just pointed at a lightweight
 * fake {@link ApplicationContextMock} instead of a real Spring context, so no Spring Boot startup
 * or database is needed to unit-test the Wicket layer.
 *
 * <p>{@link ModelForge#search} is stubbed to return an empty list by default because {@code
 * BasePage}'s sidebar tree queries it on every page render (via {@code ArtifactTreeProvider}) —
 * without a default, every single page test would otherwise fail on a {@code null} return from an
 * unstubbed mock before the test's own assertions even run. Individual tests override this (and any
 * other method) with their own {@code when(...)} as needed; Mockito's "last matching stub wins"
 * rule makes that a plain, order-independent override.
 */
public abstract class AbstractWicketPageTest {

    protected WicketTester tester;
    protected ModelForge modelForge;
    protected SeedImporter seedImporter;

    @BeforeEach
    void setUpWicket() {
        modelForge = mock(ModelForge.class);
        when(modelForge.search(org.mockito.ArgumentMatchers.any(ArtifactSearchQuery.class)))
            .thenReturn(List.of());

        // SeedPage calls this straight from its constructor — every page test needs a sane
        // default, not just tests that actually exercise SeedPage.
        seedImporter = mock(SeedImporter.class);
        when(seedImporter.discoverBundles()).thenReturn(List.of());

        ApplicationContextMock context = new ApplicationContextMock();
        context.putBean("modelForge", modelForge);
        context.putBean("seedImporter", seedImporter);

        WebApplication application = new WebApplication() {
            @Override
            public Class<? extends Page> getHomePage() {
                return ArtifactListPage.class;
            }

            @Override
            protected void init() {
                super.init();
                getComponentInstantiationListeners().add(new SpringComponentInjector(this, context));

                mountPage("/artifacts", ArtifactListPage.class);
                mountPage("/artifacts/view", ArtifactViewPage.class);
                mountPage("/artifacts/edit", ArtifactEditPage.class);
                mountPage("/artifacts/views", SchemaViewComparisonPage.class);
                mountPage("/import", ImportPage.class);
                mountPage("/seed", SeedPage.class);
                mountPage("/import/smart-data-models", SmartDataModelImportPage.class);
                mountPage("/import/xrepository", XRepositoryImportPage.class);
                mountPage("/validate", ValidatePage.class);
                mountPage("/graph", GraphPage.class);
            }
        };
        tester = new WicketTester(application);
    }

    @AfterEach
    void tearDownWicket() {
        tester.destroy();
    }
}
