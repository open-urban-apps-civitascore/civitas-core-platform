package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.facade.ModelForge;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Shows the bundled view ({@code $ref}s embedded under {@code $defs}) and the inlined view
 * ({@code $ref}s recursively inlined) of the same artifact side by side, so it is easy to see
 * whether — and how — they actually differ for a given schema.
 */
public class SchemaViewComparisonPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    public SchemaViewComparisonPage(PageParameters parameters) {
        // Guard against a blank/missing "urn" (e.g. bookmarking or hand-editing the URL to just
        // "/artifacts/views"): ArtifactId's constructor rejects a blank value with an
        // IllegalArgumentException, which — unguarded — used to crash page construction outright
        // instead of rendering a normal "not found" state like every other urn-driven page here.
        String urn = parameters.get("urn").toString("");
        add(new Label("urn", urn.isBlank() ? "(no artifact specified)" : urn));

        String bundled;
        String inlined;
        boolean identical;
        if (urn.isBlank()) {
            bundled = "(not found)";
            inlined = "(not found)";
            identical = true;
        } else {
            var query = new SchemaViewQuery(new ArtifactId(urn));
            bundled = modelForge.getBundledView(query)
                .map(view -> view.content().toPrettyString()).orElse("(not found)");
            inlined = modelForge.getInlinedView(query)
                .map(view -> view.content().toPrettyString()).orElse("(not found)");
            identical = bundled.equals(inlined);
        }

        add(new Label("bundled", Model.of(bundled)));
        add(new Label("inlined", Model.of(inlined)));
        add(new Label("identical", Model.of(identical ? "identical" : "different")));
    }
}
