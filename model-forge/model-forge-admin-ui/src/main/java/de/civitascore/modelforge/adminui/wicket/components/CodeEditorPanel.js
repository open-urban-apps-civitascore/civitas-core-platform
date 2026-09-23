/*
 * Self-contained JSON/XML syntax highlighter for CodeEditorPanel. No external dependencies — served
 * same-origin as a Wicket package resource so it works offline and under the admin-ui's strict CSP
 * (default-src 'none', no connect-src to any CDN). Exposes window.__mfHL with json()/xml() that turn
 * a source string into HTML with <span class="mf-tok-*"> tokens. Both re-escape HTML themselves, so
 * callers must pass RAW source (e.g. an element's textContent), never pre-escaped markup.
 */
(function () {
  if (window.__mfHL) return;

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // Classic single-pass JSON tokenizer: strings (a trailing ':' marks an object key), literals and
  // numbers. Runs on the escaped text; quotes are left literal so the string pattern still matches.
  function json(src) {
    return esc(src).replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
      function (m) {
        var cls = 'mf-tok-num';
        if (/^"/.test(m)) {
          cls = /:$/.test(m) ? 'mf-tok-key' : 'mf-tok-str';
        } else if (/true|false/.test(m)) {
          cls = 'mf-tok-bool';
        } else if (/null/.test(m)) {
          cls = 'mf-tok-null';
        }
        return '<span class="' + cls + '">' + m + '</span>';
      },
    );
  }

  // Lightweight XML pass (for XSD-backed elements): comments, then each tag's name + attributes.
  // Operates on the escaped text, so tag delimiters appear as &lt;/&gt;.
  function xml(src) {
    var s = esc(src);
    s = s.replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="mf-tok-comment">$1</span>');
    s = s.replace(/(&lt;\/?)([\w:.\-]+)([\s\S]*?)(\/?&gt;)/g, function (_all, open, name, body, close) {
      var attrs = body.replace(
        /([\w:.\-]+)=("[^"]*")/g,
        '<span class="mf-tok-attr">$1</span>=<span class="mf-tok-str">$2</span>',
      );
      return open + '<span class="mf-tok-tag">' + name + '</span>' + attrs + close;
    });
    return s;
  }

  window.__mfHL = { esc: esc, json: json, xml: xml };
})();
