package de.civitascore.modelforge.adminui.wicket.pages;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Whole-repository dependency graph: every artifact known to {@link ModelForge#search} as a
 * node (colored by kind), with {@code depends-on} and mapping-usage edges layered in — the same
 * relations {@link ArtifactViewPage} shows per-artifact, aggregated here across all of them.
 *
 * <p>Rendered client-side with vis-network. Vendored locally (not loaded from the unpkg CDN):
 * the minified build ships a trailing {@code //# sourceMappingURL=vis-network.min.js.map}
 * comment, and the browser's source-map fetch is a plain {@code connect-src} request — unlike
 * the {@code <script>} tag itself, it carries no CSP nonce, so the CDN host is blocked by
 * {@code connect-src 'self'}. The vendored copy has that comment stripped.
 *
 * <p>This is the only admin-ui page that issues more than a handful of facade calls per render
 * (one {@code dependencies}/{@code mapsTo} pair per artifact); fine for exploring a development
 * registry, not meant for a large production one.
 */
public class GraphPage extends BasePage {

    private static final PackageResourceReference VIS_NETWORK_JS =
        new PackageResourceReference(GraphPage.class, "vis-network.min.js");

    @SpringBean
    private ModelForge modelForge;

    private final String graphDataJson;

    public GraphPage() {
        graphDataJson = buildGraphJson();
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(VIS_NETWORK_JS));
        String script = "var modelForgeGraphData = " + escapeForInlineScript(graphDataJson) + ";"
            // vis-network's default label/edge colors are tuned for a light canvas — read the
            // already-applied [data-theme] (set page-wide before this script runs, see
            // BasePage's THEME_INIT_JS) so labels stay readable on a dark canvas too. Toggling
            // the theme while already on this page does not re-color the canvas — the graph is
            // rebuilt fresh on every page load anyway, so this only needs to be right at load time.
            + "var mfDark = document.documentElement.getAttribute('data-theme') === 'dark';"
            + "var nodes = new vis.DataSet(modelForgeGraphData.nodes);"
            + "var edges = new vis.DataSet(modelForgeGraphData.edges);"
            + "var container = document.getElementById('modelforge-graph');"
            + "var network = new vis.Network(container, {nodes: nodes, edges: edges}, {"
            + "  layout: {improvedLayout: true},"
            + "  physics: {stabilization: true, barnesHut: {springLength: 140}},"
            + "  interaction: {hover: true},"
            + "  edges: {arrows: 'to', font: {align: 'middle', size: 10, color: mfDark ? '#c7d0d8' : '#343434', strokeWidth: 0},"
            + "    color: {color: mfDark ? '#4a5560' : '#aaa'}},"
            + "  nodes: {shape: 'dot', size: 14, font: {size: 12, color: mfDark ? '#e4e9ed' : '#343434'}},"
            + "  groups: {"
            + "    element: {color: {background: '#4e79a7', border: '#2f4b6e'}},"
            + "    xsd_element: {color: {background: '#4e79a7', border: '#2f4b6e'}},"
            + "    datastructure: {color: {background: '#f28e2b', border: '#a4590f'}},"
            + "    mapping: {color: {background: '#e15759', border: '#8f2325'}},"
            + "    pipeline: {color: {background: '#76b7b2', border: '#3f7a75'}},"
            + "    datasource: {color: {background: '#59a14f', border: '#2f5c29'}},"
            + "    datasink: {color: {background: '#edc948', border: '#a88a12'}},"
            + "    dataset: {color: {background: '#b07aa1', border: '#6e4568'}}"
            + "  }"
            + "});"
            // Nodes are keyed by URN (see buildGraphJson) — clicking one opens its detail page;
            // hover feedback makes that discoverable without a legend entry for it.
            + "network.on('click', function(params) {"
            + "  if (params.nodes && params.nodes.length > 0) {"
            + "    window.location.href = '" + ArtifactViewPage.MOUNT_PATH + "?urn=' + encodeURIComponent(params.nodes[0]);"
            + "  }"
            + "});"
            + "network.on('hoverNode', function() { container.style.cursor = 'pointer'; });"
            + "network.on('blurNode', function() { container.style.cursor = 'default'; });";
        response.render(OnDomReadyHeaderItem.forScript(script));
    }

    /**
     * JSON is not inline-script-safe by itself: artifact titles/URNs are user-controlled, and a
     * string value containing {@code </script>} would close the surrounding script block.
     * Escaping the HTML-significant characters as JSON {@code \\u}-escapes keeps the payload
     * semantically identical while making it inert inside {@code <script>}.
     */
    private static String escapeForInlineScript(String json) {
        return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    private String buildGraphJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode nodesArray = root.putArray("nodes");
        ArrayNode edgesArray = root.putArray("edges");

        Map<String, String> nodeGroups = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Set<String> edgeKeys = new LinkedHashSet<>();

        var artifacts = modelForge.search(new ArtifactSearchQuery(null, null, null, 300, 0));
        for (ArtifactSummary artifact : artifacts) {
            String urn = artifact.artifactId().value();
            nodeGroups.put(urn, artifact.type() == null ? "element" : artifact.type());
            nodeLabels.put(urn, artifact.title() == null ? urn : artifact.title());
        }

        for (ArtifactSummary artifact : artifacts) {
            var query = new DependencyQuery(artifact.artifactId());
            collectEdges(modelForge.dependencies(query), nodeGroups, nodeLabels, edgesArray, edgeKeys);
            collectEdges(modelForge.mapsTo(query), nodeGroups, nodeLabels, edgesArray, edgeKeys);
        }

        nodeGroups.forEach((urn, group) -> {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", urn);
            node.put("label", nodeLabels.getOrDefault(urn, urn));
            node.put("title", urn);
            node.put("group", group);
            nodesArray.add(node);
        });

        return root.toString();
    }

    private void collectEdges(
        DependencyGraphView graph,
        Map<String, String> nodeGroups,
        Map<String, String> nodeLabels,
        ArrayNode edgesArray,
        Set<String> edgeKeys
    ) {
        ObjectMapper mapper = new ObjectMapper();
        for (var node : graph.nodes()) {
            nodeGroups.putIfAbsent(node.artifactId().value(), "element");
            nodeLabels.putIfAbsent(node.artifactId().value(), node.label());
        }
        for (var edge : graph.edges()) {
            String key = edge.source().value() + "->" + edge.target().value() + ":" + edge.relation();
            if (!edgeKeys.add(key)) continue;
            ObjectNode edgeNode = mapper.createObjectNode();
            edgeNode.put("from", edge.source().value());
            edgeNode.put("to", edge.target().value());
            edgeNode.put("label", edge.relation());
            edgesArray.add(edgeNode);
        }
    }
}
