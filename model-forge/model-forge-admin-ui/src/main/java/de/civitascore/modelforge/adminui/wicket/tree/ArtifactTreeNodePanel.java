package de.civitascore.modelforge.adminui.wicket.tree;

import de.civitascore.modelforge.adminui.wicket.pages.ArtifactViewPage;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Content of a single tree row: a type-colored dot and a label. {@link ArtifactTreeNode.Artifact}
 * rows are real links to {@link ArtifactViewPage}; every other node kind only toggles expand via
 * the tree's own junction arrow (rendered outside this panel by {@link ArtifactTree}), so its label
 * is plain, non-navigating text.
 */
class ArtifactTreeNodePanel extends Panel {

    ArtifactTreeNodePanel(String id, IModel<ArtifactTreeNode> model, String currentUrn) {
        super(id, model);
        ArtifactTreeNode node = model.getObject();
        add(dot(node));
        add(link(node, currentUrn));
    }

    private static WebMarkupContainer dot(ArtifactTreeNode node) {
        WebMarkupContainer dot = new WebMarkupContainer("dot");
        String kind = switch (node) {
            case ArtifactTreeNode.Artifact a -> a.type() == null ? "other" : a.type();
            case ArtifactTreeNode.TypeGroup ignored -> "group";
            case ArtifactTreeNode.RelationGroup ignored -> "group";
            case ArtifactTreeNode.Message ignored -> "group";
        };
        dot.add(new AttributeModifier("class", "mf-tree-dot mf-tree-dot--" + kind));
        return dot;
    }

    private static MarkupContainer link(ArtifactTreeNode node, String currentUrn) {
        return switch (node) {
            case ArtifactTreeNode.Artifact a -> artifactLink(a, currentUrn);
            case ArtifactTreeNode.TypeGroup g -> toggleLabel(g.label(), "mf-tree-label--group");
            case ArtifactTreeNode.RelationGroup g -> toggleLabel(g.kind().label(), "mf-tree-label--group");
            case ArtifactTreeNode.Message m -> toggleLabel(m.text(), "mf-tree-label--message");
        };
    }

    private static MarkupContainer artifactLink(ArtifactTreeNode.Artifact artifact, String currentUrn) {
        List<String> classes = new ArrayList<>(List.of("mf-tree-label"));
        if (artifact.cyclic()) {
            classes.add("mf-tree-label--cyclic");
        }
        if (currentUrn != null && currentUrn.equals(artifact.urn())) {
            classes.add("active");
        }

        BookmarkablePageLink<Void> link = new BookmarkablePageLink<>(
            "link", ArtifactViewPage.class, new PageParameters().add("urn", artifact.urn()));
        link.add(new AttributeModifier("class", String.join(" ", classes)));

        String name = artifact.name() != null && !artifact.name().isBlank() ? artifact.name() : artifact.urn();
        link.add(new Label("text", artifact.cyclic() ? name + " ↩" : name));

        String version = artifact.version();
        Label versionLabel = new Label("version", version == null ? "" : version);
        versionLabel.setVisible(version != null && !version.isBlank());
        link.add(versionLabel);
        return link;
    }

    private static MarkupContainer toggleLabel(String text, String modifierClass) {
        WebMarkupContainer container = new WebMarkupContainer("link");
        container.add(new AttributeModifier("class", "mf-tree-label " + modifierClass));
        container.add(new Label("text", text));
        Label versionLabel = new Label("version", "");
        versionLabel.setVisible(false);
        container.add(versionLabel);
        return container;
    }
}
