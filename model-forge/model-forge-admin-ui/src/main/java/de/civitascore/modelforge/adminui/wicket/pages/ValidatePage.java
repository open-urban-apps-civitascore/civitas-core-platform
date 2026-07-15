package de.civitascore.modelforge.adminui.wicket.pages;

import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.adminui.wicket.SchemaCatalog;
import de.civitascore.modelforge.adminui.wicket.components.CodeEditorPanel;
import de.civitascore.modelforge.contract.ValidateInstanceCommand;
import de.civitascore.modelforge.contract.ValidateSchemaCommand;
import de.civitascore.modelforge.contract.ValidationResult;
import de.civitascore.modelforge.facade.ModelForge;
import java.io.Serializable;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Ad-hoc validation sandbox: validate a pasted schema on its own, or an instance against it,
 * without persisting anything.
 */
public class ValidatePage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    private final ObjectMapper mapper = new ObjectMapper();

    public ValidatePage() {
        ValidateState state = new ValidateState();
        state.setSchema("{\n  \"type\": \"object\"\n}");
        state.setInstance("{}");

        Form<Void> form = new Form<>("validateForm");
        // The schema editor gets JSON-Schema-keyword completion; the instance is validated against
        // whatever schema was pasted (dynamic), so it stays a plain JSON editor.
        form.add(new CodeEditorPanel("schema", new PropertyModel<>(state, "schema"),
            CodeEditorPanel.MODE_JSON, SchemaCatalog.metaSchema().orElse(null), false));
        form.add(new CodeEditorPanel("instance", new PropertyModel<>(state, "instance"), CodeEditorPanel.MODE_JSON));

        form.add(new Button("validateSchema") {
            @Override
            public void onSubmit() {
                try {
                    var schema = mapper.readTree(state.getSchema());
                    report(modelForge.validateSchema(new ValidateSchemaCommand(schema)));
                } catch (Exception e) {
                    error("Invalid JSON: " + e.getMessage());
                }
            }
        });
        form.add(new Button("validateInstance") {
            @Override
            public void onSubmit() {
                try {
                    var schema = mapper.readTree(state.getSchema());
                    var instance = mapper.readTree(state.getInstance());
                    report(modelForge.validateInstance(new ValidateInstanceCommand(schema, instance)));
                } catch (Exception e) {
                    error("Invalid JSON: " + e.getMessage());
                }
            }
        });
        add(form);
    }

    private void report(ValidationResult result) {
        if (result.valid()) {
            success("Valid — no diagnostics.");
            return;
        }
        for (var diagnostic : result.diagnostics()) {
            String text = "[" + diagnostic.code() + "] " + diagnostic.message()
                + (diagnostic.path() == null || diagnostic.path().isBlank() ? "" : " (" + diagnostic.path() + ")");
            switch (diagnostic.severity()) {
                case ERROR -> error(text);
                case WARNING -> warn(text);
            }
        }
    }

    /** Mutable holder for the validate form's bound fields. */
    public static class ValidateState implements Serializable {
        private String schema;
        private String instance;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }
    }
}
