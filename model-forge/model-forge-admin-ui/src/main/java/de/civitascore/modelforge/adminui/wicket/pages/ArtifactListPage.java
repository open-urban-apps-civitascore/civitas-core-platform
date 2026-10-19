package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.wicket.ArtifactTypeCatalog;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.facade.ModelForge;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Browse/search entry point: lists {@link ArtifactSummary} rows and links to
 * {@link ArtifactViewPage} for each. This is the only place the admin UI reads more than one
 * artifact at a time; everything else operates on a single URN.
 *
 * <p>Search hits are copied into the page-local {@link ResultRow} rather than kept as
 * {@link ArtifactSummary} directly: contract records are not declared {@code Serializable}
 * (the library is JSON/JDBC-first, not Java-serialization-first), but Wicket persists page
 * state across requests via serialization, so anything a page holds across requests needs to be.
 */
public class ArtifactListPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    private List<ResultRow> results = new ArrayList<>();

    public ArtifactListPage() {
        buildStatsStrip();

        var searchState = new SearchState();

        var form = new Form<>("searchForm", new CompoundPropertyModel<>(searchState)) {
            @Override
            protected void onSubmit() {
                results = modelForge.search(new ArtifactSearchQuery(
                    blankToNull(searchState.text),
                    blankToNull(searchState.type),
                    blankToNull(searchState.format),
                    50,
                    0
                )).stream().map(ResultRow::from).toList();
            }
        };
        form.add(new TextField<>("text"));
        form.add(new TextField<>("type"));
        form.add(new TextField<>("format"));
        form.add(new BookmarkablePageLink<>("navEdit", ArtifactEditPage.class));
        add(form);

        var resultsContainer = new WebMarkupContainer("resultsContainer");
        resultsContainer.setOutputMarkupId(true);
        add(resultsContainer);

        resultsContainer.add(new ListView<ResultRow>("rows", new PropertyModel<List<ResultRow>>(this, "results")) {
            @Override
            protected void populateItem(ListItem<ResultRow> item) {
                ResultRow row = item.getModelObject();
                item.add(new Label("type", row.type()));
                item.add(new Label("title", row.title()));
                item.add(new Label("version", row.version()));
                item.add(new Label("format", row.format()));
                Link<Void> viewLink = new Link<>("viewLink") {
                    @Override
                    public void onClick() {
                        setResponsePage(ArtifactViewPage.class, new PageParameters().add("urn", row.urn()));
                    }
                };
                viewLink.add(new Label("urn", row.urn()));
                item.add(viewLink);
            }
        });
    }

    public List<ResultRow> getResults() {
        return List.copyOf(results);
    }

    /**
     * "At a glance" counts by type, above the search form. Reuses the same unfiltered,
     * 500-row-capped {@code search} call the sidebar tree already makes for its own type
     * grouping — cheap enough for a development registry, consistent with every other
     * re-query-on-render view in this module.
     */
    private void buildStatsStrip() {
        List<ArtifactSummary> summaries;
        try {
            summaries = modelForge.search(new ArtifactSearchQuery(null, null, null, 500, 0));
        } catch (RuntimeException e) {
            summaries = List.of();
        }

        Map<String, Integer> countsByType = new LinkedHashMap<>();
        for (ArtifactSummary summary : summaries) {
            String type = summary.type() == null ? "other" : summary.type();
            countsByType.merge(type, 1, Integer::sum);
        }

        List<StatRow> statRows = new ArrayList<>();
        for (String type : ArtifactTypeCatalog.TYPE_ORDER) {
            Integer count = countsByType.get(type);
            if (count != null && count > 0) {
                statRows.add(new StatRow(type, ArtifactTypeCatalog.labelFor(type), count));
            }
        }

        WebMarkupContainer statsStrip = new WebMarkupContainer("statsStrip");
        statsStrip.setVisible(!summaries.isEmpty());
        add(statsStrip);

        statsStrip.add(new Label("totalCount", String.valueOf(summaries.size())));
        statsStrip.add(new ListView<StatRow>("statRows", statRows) {
            @Override
            protected void populateItem(ListItem<StatRow> item) {
                StatRow row = item.getModelObject();
                WebMarkupContainer dot = new WebMarkupContainer("statDot");
                dot.add(new AttributeModifier("class", "mf-stat-dot mf-tree-dot--" + row.type()));
                item.add(dot);
                item.add(new Label("statCount", String.valueOf(row.count())));
                item.add(new Label("statLabel", row.label()));
            }
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Serializable row projection of {@link ArtifactSummary} for Wicket page state. */
    public record ResultRow(String urn, String type, String title, String version, String format)
        implements Serializable {

        static ResultRow from(ArtifactSummary summary) {
            return new ResultRow(
                summary.artifactId().value(),
                summary.type(),
                summary.title(),
                summary.version(),
                summary.format()
            );
        }
    }

    /** One type's count in the stats strip. */
    private record StatRow(String type, String label, int count) implements Serializable {
    }

    /** Mutable holder for the search form's bound fields (Wicket property models need setters). */
    public static class SearchState implements Serializable {
        private String text;
        private String type;
        private String format;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }
}
