package de.civitascore.modelforge.adminui.wicket.pages;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.adminui.wicket.components.CodeEditorPanel;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Read-only view of a single artifact: breadcrumbs + type badge, the authored content in a
 * syntax-highlighted (read-only) editor, its relations (dependencies, dependents, and both
 * directions of mapping usage) as clickable chips, and actions (edit, view comparison, graph,
 * delete). Editing happens on {@link ArtifactEditPage}.
 */
public class ArtifactViewPage extends BasePage {

    /** Mounted path (see {@code AdminWicketApplication}) — the single source of truth for it,
     * so anything building a URL by hand (e.g. GraphPage's vis-network click handler) can't drift
     * from the actual mount registration. */
    public static final String MOUNT_PATH = "/artifacts/view";

    private static final Map<String, String> KIND_LABELS = Map.of(
        "element", "Element",
        "datastructure", "Data Structure",
        "dataset", "Data Set",
        "mapping", "Mapping",
        "pipeline", "Pipeline",
        "datasource", "Data Source",
        "datasink", "Data Sink"
    );

    @SpringBean
    private ModelForge modelForge;

    // This admin console runs as a SEPARATE process from portal-backend, which writes the shared
    // model_forge schema. The dependency graph is a per-process in-memory index (rebuilt at startup,
    // updated only on writes through THIS process), so references portal-backend created after the
    // console started are absent from ours. Rebuild it from the durable artifact_reference rows before
    // reading relations so cross-process writes show up (fine for a dev registry; see GraphPage).
    @SpringBean
    private DependencyGraphService dependencyGraph;

    private final String urn;

    public ArtifactViewPage(PageParameters parameters) {
        this.urn = parameters.get("urn").toString("");
        ArtifactId artifactId = new ArtifactId(urn.isBlank() ? "urn:core:x:x:x:x:x:x" : urn);

        String type = UrnParser.artifactTypeFromUrn(urn);
        // KIND_LABELS is an immutable Map.of(...) — getOrDefault(null, ...) throws NPE on a null
        // key regardless of the fallback value, so a blank/unparseable urn (type == null, e.g. a
        // missing "urn" page parameter) must short-circuit before ever calling it.
        String typeLabel = type == null ? "Artifact" : KIND_LABELS.getOrDefault(type, type);
        String name = UrnParser.nameFromUrn(urn);
        String version = UrnParser.versionFromUrn(urn);

        // Breadcrumbs + header
        add(new BookmarkablePageLink<>("browseLink", ArtifactListPage.class));
        add(new Label("crumbType", typeLabel));
        add(new Label("crumbName", name));
        add(new Label("heading", name));
        Label typeBadge = new Label("typeBadge", typeLabel);
        typeBadge.add(new AttributeModifier("class", "mf-badge mf-badge--" + (type == null ? "other" : type)));
        add(typeBadge);
        add(new Label("urn", urn));
        Label versionLabel = new Label("version", version == null ? "current" : version);
        add(versionLabel);

        Optional<ArtifactView> view = urn.isBlank() ? Optional.empty() : safeGet(artifactId);
        boolean exists = view.isPresent();

        WebMarkupContainer copyUrnButton = new WebMarkupContainer("copyUrnButton");
        copyUrnButton.add(new AttributeModifier("data-urn", urn));
        copyUrnButton.setVisible(exists);
        add(copyUrnButton);

        // Content (read-only, highlighted). XSD-backed Elements store textual content → XML mode.
        WebMarkupContainer contentBox = new WebMarkupContainer("contentBox");
        contentBox.setVisible(exists);
        add(contentBox);
        WebMarkupContainer notFound = new WebMarkupContainer("notFound");
        notFound.setVisible(!exists);
        add(notFound);
        if (exists) {
            JsonNode content = view.get().content();
            String mode = content.isTextual() ? CodeEditorPanel.MODE_XML : CodeEditorPanel.MODE_JSON;
            String text = content.isTextual() ? content.asText() : content.toPrettyString();
            contentBox.add(new CodeEditorPanel("content", Model.of(text), mode, null, true));
        } else {
            contentBox.add(new CodeEditorPanel("content", Model.of(""), CodeEditorPanel.MODE_JSON, null, true));
        }

        // Actions
        add(new BookmarkablePageLink<>("editLink", ArtifactEditPage.class, new PageParameters().add("urn", urn))
            .setVisible(exists));
        add(new BookmarkablePageLink<>("viewsLink", SchemaViewComparisonPage.class, new PageParameters().add("urn", urn))
            .setVisible(exists));
        add(new BookmarkablePageLink<>("graphLink", GraphPage.class).setVisible(exists));
        Link<Void> deleteLink = new Link<>("deleteLink") {
            @Override
            public void onClick() {
                modelForge.deleteArtifact(new ArtifactId(UrnParser.logicalUrn(urn)));
                setResponsePage(ArtifactListPage.class);
            }
        };
        deleteLink.setVisible(exists);
        add(deleteLink);

        // Relations as chips. Refresh the per-process graph first so references created by
        // portal-backend after this console started are reflected (see the dependencyGraph field).
        if (exists) {
            dependencyGraph.rebuild();
        }
        addRelations("dependencies", exists ? modelForge.dependencies(new DependencyQuery(artifactId)) : null, artifactId);
        addRelations("dependents", exists ? modelForge.dependents(new DependencyQuery(artifactId)) : null, artifactId);
        addRelations("mapsTo", exists ? modelForge.mapsTo(new DependencyQuery(artifactId)) : null, artifactId);
        addRelations("mappedFrom", exists ? modelForge.mappedFrom(new DependencyQuery(artifactId)) : null, artifactId);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(OnDomReadyHeaderItem.forScript(COPY_URN_JS));
    }

    /**
     * Wires the "Copy" button next to the URN. The Clipboard API can reject even when present
     * (no permission granted, insecure context) — that path must fall back to the legacy
     * execCommand approach rather than silently do nothing, so the two-argument {@code then}
     * below routes rejection to {@code legacyCopy} explicitly instead of leaving it an unhandled
     * promise rejection.
     */
    private static final String COPY_URN_JS =
        "(function(){var btn=document.querySelector('.mf-copy-btn');if(!btn)return;"
        + "btn.addEventListener('click',function(){"
        + "var urn=btn.getAttribute('data-urn');var original=btn.textContent;"
        + "function flash(text){btn.textContent=text;setTimeout(function(){btn.textContent=original;},1400);}"
        + "function legacyCopy(){"
        + "try{var ta=document.createElement('textarea');ta.value=urn;"
        + "ta.style.position='fixed';ta.style.opacity='0';"
        + "document.body.appendChild(ta);ta.select();"
        + "var ok=document.execCommand('copy');document.body.removeChild(ta);"
        + "flash(ok?'Copied!':'Failed');"
        + "}catch(e){flash('Failed');}}"
        + "if(navigator.clipboard&&navigator.clipboard.writeText){"
        + "navigator.clipboard.writeText(urn).then(function(){flash('Copied!');},legacyCopy);"
        + "}else{legacyCopy();}});})();";

    @Override
    protected String currentUrn() {
        return urn;
    }

    private Optional<ArtifactView> safeGet(ArtifactId artifactId) {
        try {
            return modelForge.getArtifact(artifactId);
        } catch (RuntimeException e) {
            error("Could not load artifact: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void addRelations(String idPrefix, DependencyGraphView graph, ArtifactId self) {
        List<RelationRow> rows = graph == null
            ? List.of()
            : graph.nodes().stream()
                .filter(node -> !node.artifactId().equals(self))
                .map(node -> {
                    String relUrn = node.artifactId().value();
                    String kind = UrnParser.artifactTypeFromUrn(relUrn);
                    String label = node.label() != null && !node.label().isBlank()
                        ? node.label() : UrnParser.nameFromUrn(relUrn);
                    return new RelationRow(relUrn, label, KIND_LABELS.getOrDefault(kind, kind == null ? "" : kind));
                })
                .toList();

        WebMarkupContainer container = new WebMarkupContainer(idPrefix + "Container");
        add(container);
        WebMarkupContainer empty = new WebMarkupContainer(idPrefix + "Empty");
        empty.setVisible(rows.isEmpty());
        container.add(empty);
        WebMarkupContainer chips = new WebMarkupContainer(idPrefix + "Chips");
        chips.setVisible(!rows.isEmpty());
        container.add(chips);
        chips.add(new ListView<RelationRow>(idPrefix + "Rows", rows) {
            @Override
            protected void populateItem(ListItem<RelationRow> item) {
                RelationRow row = item.getModelObject();
                BookmarkablePageLink<Void> link = new BookmarkablePageLink<>(
                    "relLink", ArtifactViewPage.class, new PageParameters().add("urn", row.urn()));
                link.add(new Label("relLabel", row.label()));
                link.add(new Label("relKind", row.kind()));
                item.add(link);
            }
        });
    }

    private record RelationRow(String urn, String label, String kind) implements Serializable {
    }
}
