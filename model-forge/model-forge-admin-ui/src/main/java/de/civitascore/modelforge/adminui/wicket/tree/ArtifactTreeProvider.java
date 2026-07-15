package de.civitascore.modelforge.adminui.wicket.tree;

import de.civitascore.modelforge.adminui.wicket.ArtifactTypeCatalog;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeNode.Artifact;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeNode.Message;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeNode.RelationGroup;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeNode.RelationKind;
import de.civitascore.modelforge.adminui.wicket.tree.ArtifactTreeNode.TypeGroup;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * Lazy {@link ITreeProvider} for the sidebar artifact tree: root-level type groups are loaded once
 * (cheap, matches the old flat sidebar's cost), everything below an artifact — its dependencies,
 * dependents, and both directions of mapping usage — is fetched from {@link ModelForge} only when
 * that specific node is actually expanded, one direct-edge hop at a time. Repeating this for each
 * newly-revealed artifact is what gives unbounded-depth browsing along those relations without ever
 * loading more than what is currently visible.
 *
 * <p>Cycle safety: a relation edge that points back into the current branch's own ancestor chain
 * (e.g. A depends on B, B depends on A) still gets shown — hiding the edge would hide real
 * information — but the resulting {@link Artifact} node is marked {@link Artifact#cyclic()} and
 * reports no children, so a cycle in the underlying graph can never make a branch expand forever.
 *
 * <p>Instances are short-lived (one per {@code ArtifactTree} component, effectively one per page
 * render): {@link #detach()} drops the request-scoped caches so nothing stale survives across
 * requests, matching every other page's re-query-on-render approach to a development registry.
 */
public final class ArtifactTreeProvider implements ITreeProvider<ArtifactTreeNode>, Serializable {

    private final ModelForge modelForge;

    private transient Map<String, List<Artifact>> rootsByType;
    private transient Map<RelationCacheKey, DependencyGraphView> relationCache;

    public ArtifactTreeProvider(ModelForge modelForge) {
        this.modelForge = modelForge;
    }

    /** True when the registry has no artifacts at all — lets the caller show an empty-state note. */
    public boolean isEmpty() {
        return rootsByType().isEmpty();
    }

    @Override
    public Iterator<? extends ArtifactTreeNode> getRoots() {
        Map<String, List<Artifact>> byType = rootsByType();
        List<ArtifactTreeNode> groups = new ArrayList<>();
        for (String type : ArtifactTypeCatalog.TYPE_ORDER) {
            List<Artifact> rows = byType.getOrDefault(type, List.of());
            if (!rows.isEmpty()) {
                String label = ArtifactTypeCatalog.labelFor(type) + " (" + rows.size() + ")";
                groups.add(new TypeGroup(type, label));
            }
        }
        return groups.iterator();
    }

    @Override
    public boolean hasChildren(ArtifactTreeNode node) {
        // Each branch below only ever hands out nodes that satisfy this by construction: a
        // TypeGroup/RelationGroup is only ever created non-empty, an Artifact always has at least
        // the "no relations" Message as a child, and a Message is always a genuine leaf.
        return switch (node) {
            case TypeGroup ignored -> true;
            case Artifact a -> !a.cyclic();
            case RelationGroup ignored -> true;
            case Message ignored -> false;
        };
    }

    @Override
    public Iterator<? extends ArtifactTreeNode> getChildren(ArtifactTreeNode node) {
        List<ArtifactTreeNode> children = switch (node) {
            case TypeGroup g -> childrenOfTypeGroup(g);
            case Artifact a -> a.cyclic() ? List.<ArtifactTreeNode>of() : childrenOfArtifact(a);
            case RelationGroup g -> childrenOfRelationGroup(g);
            case Message ignored -> List.<ArtifactTreeNode>of();
        };
        return children.iterator();
    }

    @Override
    public IModel<ArtifactTreeNode> model(ArtifactTreeNode object) {
        return Model.of(object);
    }

    @Override
    public void detach() {
        rootsByType = null;
        relationCache = null;
    }

    private List<ArtifactTreeNode> childrenOfTypeGroup(TypeGroup group) {
        List<Artifact> rows = rootsByType().getOrDefault(group.type(), List.of());
        if (rows.isEmpty()) {
            return List.of(new Message("No artifacts"));
        }
        return List.copyOf(rows);
    }

    private List<ArtifactTreeNode> childrenOfArtifact(Artifact artifact) {
        List<ArtifactTreeNode> groups = new ArrayList<>();
        for (RelationKind kind : RelationKind.values()) {
            if (!fetchRelated(artifact.urn(), kind).isEmpty()) {
                groups.add(new RelationGroup(kind, artifact.urn(), artifact.ancestorUrns()));
            }
        }
        if (groups.isEmpty()) {
            return List.of(new Message("No relations"));
        }
        return groups;
    }

    private List<ArtifactTreeNode> childrenOfRelationGroup(RelationGroup group) {
        List<RelatedRef> related = fetchRelated(group.ownerUrn(), group.kind());
        List<String> chain = new ArrayList<>(group.ancestorUrns());
        chain.add(group.ownerUrn());
        List<String> chainFixed = List.copyOf(chain);

        List<ArtifactTreeNode> children = new ArrayList<>(related.size());
        for (RelatedRef ref : related) {
            boolean cyclic = chainFixed.contains(ref.urn());
            children.add(new Artifact(ref.urn(), ref.name(), ref.version(), ref.type(), chainFixed, cyclic));
        }
        return children;
    }

    private Map<String, List<Artifact>> rootsByType() {
        if (rootsByType == null) {
            rootsByType = loadRoots();
        }
        return rootsByType;
    }

    private Map<String, List<Artifact>> loadRoots() {
        List<ArtifactSummary> summaries;
        try {
            summaries = modelForge.search(new ArtifactSearchQuery(null, null, null, 500, 0));
        } catch (RuntimeException e) {
            // Registry unavailable (e.g. Postgres not up yet) — render an empty tree rather than
            // failing the whole page; BasePage's own empty-note explains it.
            return Map.of();
        }

        Map<String, List<Artifact>> byType = new LinkedHashMap<>();
        for (ArtifactSummary summary : summaries) {
            String type = summary.type() == null ? "other" : summary.type();
            String urn = summary.artifactId().value();
            String name = UrnParser.nameFromUrn(urn);
            String version = summary.version() != null ? summary.version() : UrnParser.versionFromUrn(urn);
            byType.computeIfAbsent(type, t -> new ArrayList<>())
                .add(new Artifact(urn, name, version, type, List.of(), false));
        }
        byType.values().forEach(rows -> rows.sort(
            Comparator.comparing(r -> r.name() == null ? "" : r.name().toLowerCase(Locale.ROOT))));
        return byType;
    }

    /** Direct-edge related artifacts for one (owner urn, relation kind) pair, self excluded. */
    private List<RelatedRef> fetchRelated(String ownerUrn, RelationKind kind) {
        DependencyGraphView graph = relationCache().computeIfAbsent(
            new RelationCacheKey(ownerUrn, kind), key -> fetchGraph(ownerUrn, kind));
        return graph.nodes().stream()
            .filter(n -> !n.artifactId().value().equals(ownerUrn))
            .map(n -> {
                String urn = n.artifactId().value();
                String type = UrnParser.artifactTypeFromUrn(urn);
                String name = n.label() != null && !n.label().isBlank() ? n.label() : UrnParser.nameFromUrn(urn);
                String version = UrnParser.versionFromUrn(urn);
                return new RelatedRef(urn, name, version, type);
            })
            .toList();
    }

    private DependencyGraphView fetchGraph(String ownerUrn, RelationKind kind) {
        DependencyQuery query = new DependencyQuery(new ArtifactId(ownerUrn));
        try {
            return switch (kind) {
                case DEPENDENCIES -> modelForge.dependencies(query);
                case DEPENDENTS -> modelForge.dependents(query);
                case MAPS_TO -> modelForge.mapsTo(query);
                case MAPPED_FROM -> modelForge.mappedFrom(query);
            };
        } catch (RuntimeException e) {
            return new DependencyGraphView(List.of(), List.of());
        }
    }

    private Map<RelationCacheKey, DependencyGraphView> relationCache() {
        if (relationCache == null) {
            relationCache = new HashMap<>();
        }
        return relationCache;
    }

    private record RelationCacheKey(String urn, RelationKind kind) implements Serializable {
    }

    private record RelatedRef(String urn, String name, String version, String type) {
    }
}
