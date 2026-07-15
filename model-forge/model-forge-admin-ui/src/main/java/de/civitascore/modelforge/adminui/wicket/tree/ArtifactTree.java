package de.civitascore.modelforge.adminui.wicket.tree;

import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.tree.NestedTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.Node;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 * The sidebar's artifact tree: a plain {@link NestedTree} over {@link ArtifactTreeProvider}, styled
 * from scratch (no bundled Wicket tree theme — {@code AbstractTree} ships none) via
 * {@code ArtifactTree.css} and {@link ArtifactTreeNodePanel}. Reuses no markup from {@link
 * NestedTree} itself beyond its default {@code <div wicket:id="subtree">} panel structure, which
 * already implements the lazy expand/collapse (only {@link ArtifactTreeProvider#getChildren} for a
 * node the user actually expands is ever called) and the AJAX branch refresh on toggle.
 */
public class ArtifactTree extends NestedTree<ArtifactTreeNode> {

    private static final PackageResourceReference STYLESHEET =
        new PackageResourceReference(ArtifactTree.class, "ArtifactTree.css");

    private final String currentUrn;

    public ArtifactTree(String id, ArtifactTreeProvider provider, String currentUrn) {
        super(id, provider);
        this.currentUrn = currentUrn;
        setOutputMarkupId(true);
    }

    /**
     * Supplies our own junction CSS classes instead of the resource-bundle keys {@link Node}
     * defaults to (which resolve to nothing without one of Wicket's bundled tree themes) — same
     * approach as {@link NestedTree#newNodeComponent}, plus the three style-class overrides.
     */
    @Override
    public Component newNodeComponent(String id, final IModel<ArtifactTreeNode> model) {
        Node<ArtifactTreeNode> node = new Node<>(id, this, model) {
            @Override
            protected Component createContent(String contentId, IModel<ArtifactTreeNode> contentModel) {
                return ArtifactTree.this.newContentComponent(contentId, contentModel);
            }

            @Override
            protected String getExpandedStyleClass(ArtifactTreeNode t) {
                return "mf-tree-junction mf-tree-junction--expanded";
            }

            @Override
            protected String getCollapsedStyleClass() {
                return "mf-tree-junction mf-tree-junction--collapsed";
            }

            @Override
            protected String getOtherStyleClass() {
                return "mf-tree-junction mf-tree-junction--leaf";
            }
        };
        node.setOutputMarkupId(true);
        return node;
    }

    @Override
    protected Component newContentComponent(String id, IModel<ArtifactTreeNode> model) {
        return new ArtifactTreeNodePanel(id, model, currentUrn);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(STYLESHEET));
    }
}
