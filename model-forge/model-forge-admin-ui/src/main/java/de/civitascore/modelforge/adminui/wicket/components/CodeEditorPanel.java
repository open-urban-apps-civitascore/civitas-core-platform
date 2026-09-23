package de.civitascore.modelforge.adminui.wicket.components;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 * A self-contained JSON/XML editor+viewer: a monospace area with syntax highlighting rendered by a
 * tiny local highlighter ({@code CodeEditorPanel.js}, a same-origin Wicket package resource). No
 * external assets are fetched, so it works offline and under the admin-ui's strict CSP
 * ({@code default-src 'none'}; no {@code connect-src} to any CDN).
 *
 * <p>Read-only instances render the content as a highlighted, non-editable {@code <pre>} block.
 * Editable instances keep a real {@code <textarea>} (so a plain, non-Ajax Wicket form submit reads
 * the value like any other field), with the highlighted {@code <pre>} painted directly behind a
 * transparent-text / visible-caret overlay and re-rendered on every keystroke.
 *
 * <p>This replaced an earlier CodeMirror-6-from-esm.sh editor: loading the CDN modules triggered
 * {@code connect-src} fetches (the module graph's source maps, and lint) that the strict CSP blocked,
 * so the editor never mounted and JSON fell back to a plain textarea. A local highlighter has no such
 * dependency. The former schema-aware completion/validation is dropped — acceptable for a debug tool.
 */
public class CodeEditorPanel extends Panel {

    public static final String MODE_JSON = "json";
    public static final String MODE_XML = "xml";

    /** The local highlighter ({@code window.__mfHL}). Contribute it to any page that wants to reuse
     *  {@link #highlightScript(String, String)} on its own {@code <pre>} blocks. */
    public static final PackageResourceReference HIGHLIGHTER_JS =
        new PackageResourceReference(CodeEditorPanel.class, "CodeEditorPanel.js");

    private final String mode;
    private final boolean readOnly;
    private final Label viewer;
    private final TextArea<String> editor;

    /** Editable editor. */
    public CodeEditorPanel(String id, IModel<String> content, String mode) {
        this(id, content, mode, null, false);
    }

    /**
     * @param mode       {@link #MODE_JSON} or {@link #MODE_XML}
     * @param schemaJson retained for source compatibility with call sites; unused (the local
     *                   highlighter offers no schema-aware completion)
     * @param readOnly   render as a non-editable, syntax-highlighted viewer
     */
    public CodeEditorPanel(String id, IModel<String> content, String mode, String schemaJson, boolean readOnly) {
        super(id);
        this.mode = MODE_XML.equals(mode) ? MODE_XML : MODE_JSON;
        this.readOnly = readOnly;

        WebMarkupContainer wrap = new WebMarkupContainer("wrap");
        wrap.add(AttributeModifier.append("class", readOnly ? "" : "editable"));
        add(wrap);

        // The highlighted layer. Read-only: it IS the display. Editable: the overlay painted behind
        // the transparent textarea. Escaped by default so its textContent is the exact source and the
        // highlighter (which re-escapes) is the sole emitter of markup — no HTML injection from content.
        this.viewer = new Label("viewer", content);
        viewer.setOutputMarkupId(true);
        wrap.add(viewer);

        this.editor = new TextArea<>("editor", content);
        editor.setOutputMarkupId(true);
        editor.setVisible(!readOnly);
        wrap.add(editor);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(HIGHLIGHTER_JS));
        response.render(OnDomReadyHeaderItem.forScript(bootstrapScript()));
    }

    /**
     * Per-instance wiring (nonce-carrying inline script). Read-only highlights the {@code <pre>} from
     * its own textContent; editable highlights from the live textarea value, re-rendering on input and
     * mirroring scroll so the coloured layer tracks the caret.
     */
    private String bootstrapScript() {
        if (readOnly) {
            return highlightScript(viewer.getMarkupId(), mode);
        }
        String v = viewer.getMarkupId();
        String render =
            "var render=function(src){v.innerHTML=('" + mode + "'==='xml'?H.xml(src):H.json(src));};";
        String e = editor.getMarkupId();
        return "(function(){var H=window.__mfHL;var v=document.getElementById('" + v + "');"
            + "var e=document.getElementById('" + e + "');"
            + "if(!H||!v||!e||v.dataset.mfHl)return;v.dataset.mfHl='1';"
            + render
            + "render(e.value);"
            + "e.addEventListener('input',function(){render(e.value);});"
            + "var sync=function(){v.scrollTop=e.scrollTop;v.scrollLeft=e.scrollLeft;};"
            + "e.addEventListener('scroll',sync);})();";
    }

    /**
     * A nonce-safe inline bootstrap that highlights, in place, the text content of the element with
     * {@code markupId} (read-only). Reusable by any page that renders JSON/XML in a {@code <pre>} and
     * has contributed {@link #HIGHLIGHTER_JS} to its head (e.g. {@code SchemaViewComparisonPage}).
     */
    public static String highlightScript(String markupId, String mode) {
        String m = MODE_XML.equals(mode) ? MODE_XML : MODE_JSON;
        return "(function(){var H=window.__mfHL;var v=document.getElementById('" + markupId + "');"
            + "if(!H||!v||v.dataset.mfHl)return;v.dataset.mfHl='1';"
            + "v.innerHTML=('" + m + "'==='xml'?H.xml(v.textContent):H.json(v.textContent));})();";
    }
}
