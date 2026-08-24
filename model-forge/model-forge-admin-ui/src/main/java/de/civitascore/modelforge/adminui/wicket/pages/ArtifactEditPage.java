package de.civitascore.modelforge.adminui.wicket.pages;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.adminui.wicket.SchemaCatalog;
import de.civitascore.modelforge.adminui.wicket.components.CodeEditorPanel;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.urn.UrnParser;
import java.io.Serializable;
import java.util.Arrays;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Create-or-edit page. With a {@code urn} page parameter it pre-loads the artifact's content and
 * infers its {@link ArtifactKind} from the URN's artifact-type segment; without one it starts
 * blank for a brand-new artifact. An Element may be JSON Schema or XSD: the {@code xsd} flag marks
 * the XSD representation format (kind stays {@code ELEMENT}) and is pre-set for a textual — as
 * opposed to JSON-object — stored representation.
 */
public class ArtifactEditPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    private final ObjectMapper mapper = new ObjectMapper();

    /** The artifact being edited, or {@code null} when creating — see {@link #currentUrn()}. */
    private final String editedUrn;

    public ArtifactEditPage() {
        this(new PageParameters());
    }

    public ArtifactEditPage(PageParameters parameters) {
        String urn = parameters.get("urn").toString(null);
        boolean editing = urn != null && !urn.isBlank();
        this.editedUrn = editing ? urn : null;

        EditState state = new EditState();
        String editorMode = CodeEditorPanel.MODE_JSON;

        if (editing) {
            state.setUrn(urn);
            state.setKind(inferKind(urn));
            var existing = modelForge.getArtifact(new ArtifactId(urn));
            if (existing.isPresent()) {
                JsonNode content = existing.get().content();
                if (content.isTextual()) {
                    // Textual stored representation → an XSD-backed Element (kind stays ELEMENT).
                    state.setXsd(true);
                    state.setContent(content.asText());
                    editorMode = CodeEditorPanel.MODE_XML;
                } else {
                    state.setContent(content.toPrettyString());
                }
            } else {
                state.setContent("");
            }
        } else {
            state.setKind(ArtifactKind.ELEMENT);
            state.setContent("{\n  \n}");
        }
        state.setVersionBump(VersionBump.PATCH);

        String finalEditorMode = editorMode;
        Form<Void> form = new Form<>("editForm") {
            @Override
            protected void onSubmit() {
                try {
                    // Always save against the logical (version-free) URN: the loaded content's
                    // own $id/urn is whatever pinned version was open in the editor, and passing
                    // that straight through would confuse the version bump (the registry would
                    // mint a new version while $id still claimed the old one). logicalUrn() is a
                    // no-op for an already-unversioned URN, so this is safe for create too.
                    ArtifactId artifactId = new ArtifactId(UrnParser.logicalUrn(state.getUrn()));
                    JsonNode content = state.isXsd()
                        ? StringNode.valueOf(state.getContent())
                        : mapper.readTree(state.getContent());
                    ArtifactId saved = modelForge.saveArtifact(
                        new SaveArtifactCommand(artifactId, state.getKind(), content, state.getVersionBump()))
                        .artifactId();
                    setResponsePage(ArtifactViewPage.class, new PageParameters().add("urn", saved.value()));
                } catch (JacksonException e) {
                    error("Invalid JSON: " + e.getOriginalMessage());
                } catch (RuntimeException e) {
                    error("Save failed: " + e.getMessage());
                }
            }
        };

        TextField<String> urnField = new TextField<>("urn", new PropertyModel<>(state, "urn"));
        urnField.setEnabled(!editing);
        urnField.setRequired(true);
        form.add(urnField);

        form.add(new DropDownChoice<>("kind", new PropertyModel<>(state, "kind"), Arrays.asList(ArtifactKind.values())));
        form.add(new CheckBox("xsd", new PropertyModel<>(state, "xsd")));
        form.add(new DropDownChoice<>("versionBump", new PropertyModel<>(state, "versionBump"), Arrays.asList(VersionBump.values())));
        // XSD Elements are edited as plain XML; JSON documents get schema-driven completion —
        // the meta-schema for Elements, the type schema for the manifest kinds.
        String schemaJson = state.isXsd() ? null : SchemaCatalog.schemaForKind(state.getKind()).orElse(null);
        form.add(new CodeEditorPanel("content", new PropertyModel<>(state, "content"), finalEditorMode, schemaJson, false));

        add(form);
    }

    @Override
    protected String currentUrn() {
        return editedUrn;
    }

    private static ArtifactKind inferKind(String urn) {
        String type = UrnParser.artifactTypeFromUrn(urn);
        if (type == null) {
            return ArtifactKind.ELEMENT;
        }
        return switch (type) {
            case "datastructure" -> ArtifactKind.DATA_STRUCTURE;
            case "mapping" -> ArtifactKind.MAPPING;
            case "pipeline" -> ArtifactKind.PIPELINE;
            case "datasource" -> ArtifactKind.DATA_SOURCE;
            case "datasink" -> ArtifactKind.DATA_SINK;
            case "dataset" -> ArtifactKind.DATA_SET;
            default -> ArtifactKind.ELEMENT;
        };
    }

    /** Mutable holder for the edit form's bound fields. */
    public static class EditState implements Serializable {
        private String urn;
        private ArtifactKind kind;
        private boolean xsd;
        private VersionBump versionBump;
        private String content;

        public boolean isXsd() {
            return xsd;
        }

        public void setXsd(boolean xsd) {
            this.xsd = xsd;
        }

        public String getUrn() {
            return urn;
        }

        public void setUrn(String urn) {
            this.urn = urn;
        }

        public ArtifactKind getKind() {
            return kind;
        }

        public void setKind(ArtifactKind kind) {
            this.kind = kind;
        }

        public VersionBump getVersionBump() {
            return versionBump;
        }

        public void setVersionBump(VersionBump versionBump) {
            this.versionBump = versionBump;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
