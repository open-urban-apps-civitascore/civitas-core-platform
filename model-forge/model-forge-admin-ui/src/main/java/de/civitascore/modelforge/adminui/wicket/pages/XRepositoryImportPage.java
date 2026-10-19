package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportXRepositoryCommand;
import de.civitascore.modelforge.contract.XRepositoryHit;
import de.civitascore.modelforge.contract.XRepositorySearchQuery;
import de.civitascore.modelforge.facade.ModelForge;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Searches the XRepository (xOEV) catalog and imports a hit either converted to JSON Schema
 * Elements, or as a raw XSD artifact.
 */
public class XRepositoryImportPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    private List<Hit> results = new ArrayList<>();

    /**
     * Opt-in, off by default: when set, the XÖV/XRepository version is adopted verbatim as the
     * stored artifact version instead of Model Forge's usual version authority. A page field (not
     * per-row) — it applies uniformly to whichever import link is clicked, read via the import
     * form's model at submit time (see {@link #importLink}).
     */
    private boolean preserveUpstreamVersion;

    public XRepositoryImportPage() {
        SearchState state = new SearchState();
        state.setQuery("xmeld");

        Form<Void> searchForm = new Form<>("searchForm") {
            @Override
            protected void onSubmit() {
                try {
                    results = modelForge.searchXRepository(new XRepositorySearchQuery(state.getQuery(), 0, 20))
                        .stream().map(Hit::from).toList();
                    if (results.isEmpty()) {
                        info("No hits.");
                    }
                } catch (RuntimeException e) {
                    error("Search failed: " + e.getMessage());
                }
            }
        };
        searchForm.add(new TextField<>("query", new PropertyModel<>(state, "query")));
        add(searchForm);

        // A separate form (not searchForm) so the checkbox's model is synced by the SAME submit
        // that triggers an import — a plain Link would not pick up a just-changed checkbox value.
        Form<Void> importForm = new Form<>("importForm");
        importForm.add(new CheckBox("preserveUpstreamVersion", new PropertyModel<>(this, "preserveUpstreamVersion")));
        add(importForm);

        WebMarkupContainer resultsContainer = new WebMarkupContainer("resultsContainer");
        resultsContainer.setOutputMarkupId(true);
        importForm.add(resultsContainer);

        resultsContainer.add(new ListView<Hit>("rows", new PropertyModel<List<Hit>>(this, "results")) {
            @Override
            protected void populateItem(ListItem<Hit> item) {
                Hit hit = item.getModelObject();
                item.add(new Label("identifier", hit.identifier()));
                item.add(new Label("name", hit.name() == null ? "" : hit.name()));
                item.add(new Label("version", hit.version() == null ? "" : hit.version()));
                item.add(new Label("kind", hit.kind() == null ? "" : hit.kind()));
                item.add(new Label("status", hit.status() == null ? "" : hit.status()));
                item.add(importLink("importJson", hit, false));
                item.add(importLink("importXsd", hit, true));
            }
        });
    }

    /** A submit link (not a plain Link) so clicking it first syncs the checkbox's current value
     *  into {@link #preserveUpstreamVersion} via the enclosing import form. */
    private SubmitLink importLink(String id, Hit hit, boolean asXsd) {
        return new SubmitLink(id) {
            @Override
            public void onSubmit() {
                try {
                    ImportResult imported = modelForge.importFromXRepository(new ImportXRepositoryCommand(
                        hit.identifier(), hit.version(), "standard", "xoev", asXsd, preserveUpstreamVersion));
                    setResponsePage(ArtifactViewPage.class,
                        new PageParameters().add("urn", imported.rootArtifactId().value()));
                } catch (RuntimeException e) {
                    // Reported on the page, not this anonymous SubmitLink: the message must still
                    // be there for the page render that follows this same click, and the page is
                    // the stable, intended owner of that feedback — not a SubmitLink instance
                    // whose relationship to that later render is incidental.
                    XRepositoryImportPage.this.error("Import failed: " + e.getMessage());
                }
            }
        };
    }

    public List<Hit> getResults() {
        return List.copyOf(results);
    }

    /** Serializable row projection of {@link XRepositoryHit} for Wicket page state. */
    public record Hit(String identifier, String name, String version, String kind, String status)
        implements Serializable {

        static Hit from(XRepositoryHit hit) {
            return new Hit(hit.identifier(), hit.name(), hit.version(), hit.kind(), hit.status());
        }
    }

    /** Mutable holder for the search form's bound field. */
    public static class SearchState implements Serializable {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
