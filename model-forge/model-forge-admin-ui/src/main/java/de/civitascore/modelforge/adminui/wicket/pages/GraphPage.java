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
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.urn.UrnParser;
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

    // Per-process in-memory graph — rebuild from the durable artifact_reference rows before rendering
    // so edges portal-backend wrote after this (separate) console started are included. See the same
    // note on ArtifactViewPage. Fine for a development registry, per this page's class comment.
    @SpringBean
    private DependencyGraphService dependencyGraph;

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
            // Filtering: each type checkbox toggles a whole artifact kind; the search box matches a
            // node's label or URN (case-insensitive substring). Nodes render through a DataView whose
            // filter reads the live state, edges through a DataView that hides any edge with a
            // filtered-out endpoint (else vis-network draws it dangling). xsd_element shares the
            // Element toggle; a group with no checkbox stays visible (it can't be filtered).
            + "var mfTypeState = {};"
            + "var mfSearch = '';"
            + "function mfGroup(n){ return n.group === 'xsd_element' ? 'element' : n.group; }"
            + "function mfNodeVisible(n){"
            + "  if (mfTypeState[mfGroup(n)] === false) return false;"
            + "  if (mfSearch) { var hay = ((n.label || '') + ' ' + n.id).toLowerCase(); if (hay.indexOf(mfSearch) < 0) return false; }"
            + "  return true;"
            + "}"
            + "var mfVisibleIds = {};"
            + "function mfRecompute(){ mfVisibleIds = {}; nodes.get().forEach(function(n){ if (mfNodeVisible(n)) mfVisibleIds[n.id] = true; }); }"
            + "mfRecompute();"
            + "var nodesView = new vis.DataView(nodes, {filter: mfNodeVisible});"
            + "var edgesView = new vis.DataView(edges, {filter: function(e){ return mfVisibleIds[e.from] && mfVisibleIds[e.to]; }});"
            + "var container = document.getElementById('modelforge-graph');"
            + "var network = new vis.Network(container, {nodes: nodesView, edges: edgesView}, {"
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
            + "    dataset: {color: {background: '#b07aa1', border: '#6e4568'}},"
            // A reference target that is not a registered artifact: the edge survives its target's
            // removal by design, so the node is real but must not read as an existing artifact.
            + "    unresolved: {shape: 'diamond', color: {background: mfDark ? '#3a3f44' : '#d9dde1',"
            + "      border: mfDark ? '#6b7681' : '#9aa5b1'}, font: {color: mfDark ? '#9aa5b1' : '#6b7681'}}"
            + "  }"
            + "});"
            // Re-evaluate both DataViews whenever a control changes: recompute the visible-id set
            // first (the edge filter reads it), then refresh nodes and edges.
            + "function mfRefresh(){ mfRecompute(); nodesView.refresh(); edgesView.refresh(); }"
            + "Array.prototype.forEach.call(document.querySelectorAll('.mf-graph-type input[type=checkbox]'), function(cb){"
            + "  var g = cb.getAttribute('data-group');"
            + "  mfTypeState[g] = cb.checked;"
            + "  cb.addEventListener('change', function(){ mfTypeState[g] = cb.checked; mfRefresh(); });"
            + "});"
            + "var mfSearchEl = document.getElementById('mf-graph-search');"
            + "if (mfSearchEl) { mfSearchEl.addEventListener('input', function(){ mfSearch = mfSearchEl.value.trim().toLowerCase(); mfRefresh(); }); }"
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

    /**
     * The vis-network group (colour) for a node, derived from the URN's type segment first and only
     * falling back to the registry's {@code artifact_type} (then {@code element}). The URN is the more
     * reliable signal: an artifact whose stored {@code artifact_type} disagrees with its URN — e.g. a
     * root Element mistakenly persisted under a {@code :datastructure:} URN — would otherwise be
     * mis-coloured as an Element. Unknown/blank segments fall back so every node still gets a group.
     */
    /** vis-network group for a reference target that no registered artifact backs. */
    private static final String GROUP_UNRESOLVED = "unresolved";

    private static String groupForUrn(String urn, String fallbackType) {
        String type = UrnParser.artifactTypeFromUrn(urn);
        if (type != null && !type.isBlank()) {
            return type;
        }
        return fallbackType == null || fallbackType.isBlank() ? "element" : fallbackType;
    }

    private String buildGraphJson() {
        // Refresh the per-process graph from the DB so cross-process (portal-backend) writes appear.
        dependencyGraph.rebuild();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode nodesArray = root.putArray("nodes");
        ArrayNode edgesArray = root.putArray("edges");

        Map<String, String> nodeGroups = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Set<String> edgeKeys = new LinkedHashSet<>();

        var artifacts = modelForge.search(new ArtifactSearchQuery(null, null, null, 300, 0));
        for (ArtifactSummary artifact : artifacts) {
            // Key nodes by the logical (version-less) URN so they match the normalised edge endpoints
            // (see collectEdges) — one node per artifact, edges resolve against it.
            String urn = UrnParser.logicalUrn(artifact.artifactId().value());
            nodeGroups.put(urn, groupForUrn(urn, artifact.type()));
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
            String nodeUrn = UrnParser.logicalUrn(node.artifactId().value());
            // Registered artifacts are seeded into nodeGroups above, so a URN still absent here is
            // a reference target the registry does not hold — deleted, or never imported. The edge
            // is kept deliberately; the node is grouped so it does not read as an existing artifact.
            nodeGroups.putIfAbsent(nodeUrn, GROUP_UNRESOLVED);
            nodeLabels.putIfAbsent(nodeUrn, node.label());
        }
        for (var edge : graph.edges()) {
            // The dependency view keys nodes and edges by VERSIONED URNs, but the graph's nodes are
            // keyed by their logical (version-less) URN (from search). Normalise both endpoints to the
            // logical URN so they match a node id — vis-network drops any edge whose endpoints are not
            // visible nodes, which is why versioned edge endpoints previously rendered no edges at all.
            String from = UrnParser.logicalUrn(edge.source().value());
            String to = UrnParser.logicalUrn(edge.target().value());
            String key = from + "->" + to + ":" + edge.relation();
            if (!edgeKeys.add(key)) continue;
            ObjectNode edgeNode = mapper.createObjectNode();
            edgeNode.put("from", from);
            edgeNode.put("to", to);
            edgeNode.put("label", edge.relation());
            edgesArray.add(edgeNode);
        }
    }
}
