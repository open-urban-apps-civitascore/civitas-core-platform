package de.civitascore.modelforge.adminui.wicket;

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
import org.apache.wicket.Page;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;

/**
 * Wicket application bootstrap. Runs inside the Spring Boot embedded servlet container; page
 * classes get their {@link de.civitascore.modelforge.facade.ModelForge} via {@code @SpringBean}.
 */
public class AdminWicketApplication extends WebApplication {

    @Override
    protected void init() {
        super.init();
        getComponentInstantiationListeners().add(new SpringComponentInjector(this));

        // Wicket's default strict CSP scopes style-src to a per-request nonce only. That covers
        // stylesheets/style elements Wicket renders through its header API, but vis-network (the
        // /graph page) sets inline style="" attributes at runtime, which can never carry a
        // request-time nonce and get blocked by browsers. Drop the nonce from style-src and allow
        // 'unsafe-inline' instead; script-src stays on Wicket's strict-dynamic+nonce default. This
        // is scoped to the admin-ui, which is a standalone debug/inspection host, not production.
        getCspSettings()
                .blocking()
                .remove(CSPDirective.STYLE_SRC)
                .add(CSPDirective.STYLE_SRC, CSPDirectiveSrcValue.SELF, CSPDirectiveSrcValue.UNSAFE_INLINE);

        mountPage("/artifacts", ArtifactListPage.class);
        mountPage(ArtifactViewPage.MOUNT_PATH, ArtifactViewPage.class);
        mountPage("/artifacts/edit", ArtifactEditPage.class);
        mountPage("/artifacts/views", SchemaViewComparisonPage.class);
        mountPage("/import", ImportPage.class);
        mountPage("/seed", SeedPage.class);
        mountPage("/import/smart-data-models", SmartDataModelImportPage.class);
        mountPage("/import/xrepository", XRepositoryImportPage.class);
        mountPage("/validate", ValidatePage.class);
        mountPage("/graph", GraphPage.class);
    }

    @Override
    public Class<? extends Page> getHomePage() {
        return ArtifactListPage.class;
    }
}
