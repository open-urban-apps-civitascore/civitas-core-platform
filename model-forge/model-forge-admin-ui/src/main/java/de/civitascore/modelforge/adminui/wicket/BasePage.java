package de.civitascore.modelforge.adminui.wicket;

import de.civitascore.modelforge.adminui.wicket.pages.ArtifactEditPage;
import de.civitascore.modelforge.adminui.wicket.pages.ArtifactListPage;
import de.civitascore.modelforge.adminui.wicket.pages.GraphPage;
import de.civitascore.modelforge.adminui.wicket.pages.ImportPage;
import de.civitascore.modelforge.adminui.wicket.pages.SeedPage;
import de.civitascore.modelforge.adminui.wicket.pages.SmartDataModelImportPage;
import de.civitascore.modelforge.adminui.wicket.pages.ValidatePage;
import de.civitascore.modelforge.adminui.wicket.pages.XRepositoryImportPage;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTree;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeProvider;
import de.civitascore.modelforge.facade.ModelForge;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Common layout for every admin-ui page: a top nav bar, a persistent left sidebar that lazily
 * browses the whole registry — by artifact type, then, per artifact, along its dependencies,
 * dependents, and both directions of mapping usage, to unbounded depth — and the page body (via
 * {@code <wicket:extend>}).
 *
 * <p>The sidebar is the primary way to navigate the model. The top-level type grouping re-queries
 * {@link ModelForge#search} on every render (cheap enough for a development registry, matches the
 * old flat sidebar's cost) so newly imported artifacts appear immediately; everything below that —
 * an artifact's relations — is fetched from {@link ArtifactTreeProvider} only when the user actually
 * expands that node. A small client-side filter narrows whatever is currently expanded/rendered
 * without a round-trip.
 *
 * <p>Styling lives in {@code BasePage.css} (plus {@code ArtifactTree.css}, contributed by the tree
 * component itself), rendered via {@link #renderHead} because the host CSP allows only
 * Wicket-rendered (nonce-carrying) header contributions plus {@code 'unsafe-inline'} for styles.
 */
public abstract class BasePage extends WebPage {

    private static final PackageResourceReference STYLESHEET =
        new PackageResourceReference(BasePage.class, "BasePage.css");

    @SpringBean
    private ModelForge modelForge;

    protected BasePage() {
        add(new BookmarkablePageLink<>("navArtifacts", ArtifactListPage.class));
        add(new BookmarkablePageLink<>("navImport", ImportPage.class));
        add(new BookmarkablePageLink<>("navSeed", SeedPage.class));
        add(new BookmarkablePageLink<>("navSmartDataModels", SmartDataModelImportPage.class));
        add(new BookmarkablePageLink<>("navXRepository", XRepositoryImportPage.class));
        add(new BookmarkablePageLink<>("navValidate", ValidatePage.class));
        add(new BookmarkablePageLink<>("navGraph", GraphPage.class));
        add(new BookmarkablePageLink<>("navNew", ArtifactEditPage.class));
        add(new FeedbackPanel("feedback"));
    }

    /**
     * Builds the sidebar here rather than in the constructor: it reads {@link #currentUrn()}, which
     * subclasses override off their own fields, and those are still unassigned while the base
     * constructor runs. Wicket calls this once the whole construction chain has completed.
     */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        buildSidebar();
    }

    /**
     * The URN of the artifact this page is focused on, or {@code null}. Detail/edit pages override
     * this so the sidebar can highlight the open artifact.
     */
    protected String currentUrn() {
        return null;
    }

    private void buildSidebar() {
        ArtifactTreeProvider provider = new ArtifactTreeProvider(modelForge);
        boolean empty = provider.isEmpty();

        WebMarkupContainer emptyNote = new WebMarkupContainer("emptyNote");
        emptyNote.setVisible(empty);
        add(emptyNote);

        ArtifactTree tree = new ArtifactTree("artifactTree", provider, currentUrn());
        tree.setVisible(!empty);
        add(tree);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // Runs immediately (not deferred to DOM-ready) and before the stylesheet reference below,
        // so [data-theme] is set on <html> before the browser's first paint — no flash of the
        // wrong theme.
        response.render(JavaScriptHeaderItem.forScript(THEME_INIT_JS, "mf-theme-init"));
        response.render(CssHeaderItem.forReference(STYLESHEET));
        response.render(OnDomReadyHeaderItem.forScript(THEME_TOGGLE_JS));
        response.render(OnDomReadyHeaderItem.forScript(SIDEBAR_FILTER_JS));
        response.render(OnDomReadyHeaderItem.forScript(SIDEBAR_QUICKJUMP_JS));
    }

    /** Sets [data-theme] from localStorage, falling back to the OS-level color-scheme preference. */
    private static final String THEME_INIT_JS =
        "(function(){var saved=localStorage.getItem('mf-theme');"
        + "var theme=saved||((window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light');"
        + "document.documentElement.setAttribute('data-theme',theme);})();";

    /** Wires the nav bar's dark/light toggle button, persisting the choice for next time. */
    private static final String THEME_TOGGLE_JS =
        "(function(){var btn=document.getElementById('mf-theme-toggle');if(!btn)return;"
        + "function sync(){var cur=document.documentElement.getAttribute('data-theme')||'light';"
        + "btn.textContent=cur==='dark'?'Light':'Dark';btn.setAttribute('aria-pressed',cur==='dark'?'true':'false');}"
        + "sync();btn.addEventListener('click',function(){"
        + "var next=(document.documentElement.getAttribute('data-theme')||'light')==='dark'?'light':'dark';"
        + "document.documentElement.setAttribute('data-theme',next);localStorage.setItem('mf-theme',next);sync();});})();";

    /**
     * Client-side filter: hides currently-rendered tree branches with no matching text anywhere in
     * their own row or descendants — collapsed (not-yet-loaded) branches aren't in the DOM to
     * search, same "already-rendered content only" limitation the old flat-list filter had.
     */
    private static final String SIDEBAR_FILTER_JS =
        "(function(){var f=document.getElementById('mf-sidebar-filter');if(!f)return;"
        + "f.addEventListener('input',function(){var q=f.value.toLowerCase();"
        + "document.querySelectorAll('.mf-tree .tree-branch').forEach(function(b){"
        + "if(!q){b.classList.remove('mf-hidden');return;}"
        + "var match=false;b.querySelectorAll('.mf-tree-text').forEach(function(t){"
        + "if(t.textContent.toLowerCase().indexOf(q)>=0)match=true;});"
        + "b.classList.toggle('mf-hidden',!match);});});})();";

    /**
     * Keyboard-driven navigation: "/" (when not typing elsewhere) focuses the sidebar filter;
     * Enter inside it jumps straight to the first visible, actually-navigable (i.e. a real
     * artifact, not a type/relation group heading) match.
     */
    private static final String SIDEBAR_QUICKJUMP_JS =
        "(function(){var f=document.getElementById('mf-sidebar-filter');if(!f)return;"
        + "document.addEventListener('keydown',function(e){"
        + "if(e.key!=='/')return;var ae=document.activeElement;"
        + "var typing=ae&&(ae.tagName==='INPUT'||ae.tagName==='TEXTAREA'||ae.isContentEditable);"
        + "if(typing)return;e.preventDefault();f.focus();});"
        + "f.addEventListener('keydown',function(e){"
        + "if(e.key!=='Enter')return;e.preventDefault();"
        + "var link=document.querySelector('.mf-tree .tree-branch:not(.mf-hidden) a.mf-tree-label[href]');"
        + "if(link)link.click();});})();";
}
