package de.civitascore.modelforge.adminui.wicket.pages;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.adminui.wicket.SchemaCatalog;
import de.civitascore.modelforge.adminui.wicket.components.CodeEditorPanel;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.facade.ModelForge;
import java.io.Serializable;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Pastes a JSON schema and imports it via {@link ModelForge#importSchema}, the same entry point
 * a real host application would use.
 */
public class ImportPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    private final ObjectMapper mapper = new ObjectMapper();

    public ImportPage() {
        ImportState state = new ImportState();
        state.setSchema("{\n  \"title\": \"Example\",\n  \"type\": \"object\",\n  \"properties\": {}\n}");

        Form<Void> form = new Form<>("importForm") {
            @Override
            protected void onSubmit() {
                try {
                    var schema = mapper.readTree(state.getSchema());
                    var result = modelForge.importSchema(new ImportSchemaCommand(schema));
                    setResponsePage(ArtifactViewPage.class,
                        new PageParameters().add("urn", result.rootArtifactId().value()));
                } catch (JacksonException e) {
                    error("Invalid JSON: " + e.getOriginalMessage());
                } catch (RuntimeException e) {
                    error("Import failed: " + e.getMessage());
                }
            }
        };
        form.add(new CodeEditorPanel("schema", new PropertyModel<>(state, "schema"),
            CodeEditorPanel.MODE_JSON, SchemaCatalog.metaSchema().orElse(null), false));
        add(form);
    }

    /** Mutable holder for the import form's bound fields. */
    public static class ImportState implements Serializable {
        private String schema;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}
