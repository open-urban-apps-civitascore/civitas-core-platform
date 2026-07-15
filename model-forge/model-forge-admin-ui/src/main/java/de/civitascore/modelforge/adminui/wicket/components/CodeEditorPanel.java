package de.civitascore.modelforge.adminui.wicket.components;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * A {@code <textarea>} upgraded to a CodeMirror&nbsp;6 editor with optional JSON-Schema awareness.
 *
 * <p>When a schema is supplied and the mode is JSON, the editor gets VS-Code-style behaviour —
 * schema-driven completion, hover documentation and inline validation — via
 * {@code codemirror-json-schema}. Without a schema it is plain JSON (or XML for XSD-backed
 * Elements). A read-only instance is used to pretty-print artifact content with folding/highlighting.
 *
 * <h3>How the modules load</h3>
 * CodeMirror&nbsp;6 is ESM/bundler-first; this is a debug tool with no frontend build, so the
 * modules are pulled from esm.sh at runtime. This works under the host's strict CSP because Wicket
 * renders the bootstrap as a nonce-carrying inline script, and {@code script-src 'strict-dynamic'}
 * propagates that trust to the dynamically {@code import()}ed module graph. CodeMirror injects its
 * own CSS as {@code <style>} elements, allowed by the admin-ui's {@code style-src 'unsafe-inline'}.
 * No web workers are used, so no {@code worker-src} is required.
 *
 * <p><b>Version pinning:</b> all CodeMirror core packages ({@code @codemirror/state},
 * {@code view}, {@code language}, …) MUST resolve to a single instance — two copies break facet
 * identity and the editor silently fails. We pin them to the same major via esm.sh {@code ?deps} so
 * every top-level import shares identical dependency URLs, which esm.sh then de-duplicates.
 *
 * <p>The editor mirrors every edit back into the underlying (hidden) textarea, so a plain
 * (non-Ajax) Wicket form submit reads the edited content exactly like any other form field.
 */
public class CodeEditorPanel extends Panel {

    public static final String MODE_JSON = "json";
    public static final String MODE_XML = "xml";

    private final String mode;
    private final boolean readOnly;
    private final TextArea<String> editor;
    private final Label mount;
    private final Label schemaData;

    /** Editable editor without schema awareness. */
    public CodeEditorPanel(String id, IModel<String> content, String mode) {
        this(id, content, mode, null, false);
    }

    /**
     * @param mode       {@link #MODE_JSON} or {@link #MODE_XML}
     * @param schemaJson JSON Schema (as a JSON string) to validate/complete against, or {@code null}
     * @param readOnly   render as a non-editable, syntax-highlighted viewer
     */
    public CodeEditorPanel(String id, IModel<String> content, String mode, String schemaJson, boolean readOnly) {
        super(id);
        this.mode = mode;
        this.readOnly = readOnly;

        this.editor = new TextArea<>("editor", content);
        editor.setOutputMarkupId(true);
        add(editor);

        // The mount point CodeMirror renders into. A Label (empty body) just gives us a stable
        // markup id to hand to the bootstrap script.
        this.mount = new Label("mount", Model.of(""));
        mount.setOutputMarkupId(true);
        add(mount);

        // Non-executable data island carrying the schema; read client-side via textContent. Escaped
        // so a '</script>' inside a schema string cannot break out of the block (still valid JSON).
        this.schemaData = new Label("schemaData", Model.of(schemaJson == null ? "" : escapeForScriptBlock(schemaJson)));
        schemaData.setEscapeModelStrings(false);
        schemaData.setOutputMarkupId(true);
        schemaData.setVisible(schemaJson != null && MODE_JSON.equals(mode));
        add(schemaData);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(OnDomReadyHeaderItem.forScript(bootstrapScript()));
    }

    private String bootstrapScript() {
        String schemaId = schemaData.isVisible() ? "'" + schemaData.getMarkupId() + "'" : "null";
        // One shared import promise for the whole page (window.__mfCM6); each panel then builds its
        // own EditorView. Major-pinned ?deps keep the CodeMirror core packages a single instance.
        return "(function(){"
            + "var ta=document.getElementById('" + editor.getMarkupId() + "');"
            + "var mount=document.getElementById('" + mount.getMarkupId() + "');"
            + "if(!ta||!mount||mount.dataset.mfInit)return;"
            + "mount.dataset.mfInit='1';"
            + "window.__mfCM6=window.__mfCM6||(function(){"
            + "  var D='?deps=@codemirror/state@6,@codemirror/view@6,@codemirror/language@6,"
            + "@codemirror/commands@6,@codemirror/lint@6,@codemirror/autocomplete@6,@codemirror/search@6';"
            + "  return Promise.all(["
            + "    import('https://esm.sh/codemirror@6'+D),"
            + "    import('https://esm.sh/@codemirror/lang-json@6'+D),"
            + "    import('https://esm.sh/@codemirror/lang-xml@6'+D),"
            + "    import('https://esm.sh/codemirror-json-schema@0'+D)"
            + "  ]).then(function(m){return {cm:m[0],json:m[1],xml:m[2],js:m[3]};});"
            + "})();"
            + "window.__mfCM6.then(function(M){"
            + "  var exts=[M.cm.basicSetup];"
            + "  if('" + mode + "'==='xml'){exts.push(M.xml.xml());}"
            + "  else{"
            + "    exts.push(M.json.json());"
            + "    var sid=" + schemaId + ";"
            + "    if(sid){var sEl=document.getElementById(sid);"
            + "      if(sEl){try{exts.push(M.js.jsonSchema(JSON.parse(sEl.textContent)));}"
            + "        catch(e){console.error('model-forge: invalid editor schema',e);}}}"
            + "  }"
            + (readOnly
                ? "  exts.push(M.cm.EditorView.editable.of(false));"
                : "  exts.push(M.cm.EditorView.updateListener.of(function(u){if(u.docChanged){ta.value=u.state.doc.toString();}}));")
            + "  new M.cm.EditorView({doc:ta.value,extensions:exts,parent:mount});"
            + "  ta.classList.add('mf-editor-src');"
            + "}).catch(function(e){console.error('model-forge: CodeMirror load failed',e);});"
            + "})();";
    }

    /**
     * Escapes the HTML-significant characters as JSON {@code \\u}-escapes so the payload stays
     * valid JSON while being inert inside a {@code <script>} block (a raw {@code </script>} in a
     * schema description would otherwise close the element early). Mirrors {@code GraphPage}.
     */
    private static String escapeForScriptBlock(String json) {
        return json.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }
}
