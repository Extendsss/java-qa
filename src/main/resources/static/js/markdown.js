(function () {
  'use strict';
  const Q = window.Quiz;
  const escapeHtml = text => String(text == null ? '' : text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const langAliases = {mysql: 'sql', postgres: 'sql', postgresql: 'sql', tsql: 'sql', plsql: 'sql', mybatis: 'xml', 'c#': 'csharp', 'c++': 'cpp', 'js': 'javascript', 'ts': 'typescript'};
  const highlight = (code, lang) => {
    if (!window.hljs || !lang) return null;
    const name = langAliases[lang] || lang;
    if (!window.hljs.getLanguage(name)) return null;
    try { return window.hljs.highlight(code, {language: name, ignoreIllegals: true}).value; } catch (_) { return null; }
  };
  if (!window.marked || !window.DOMPurify) {
    Q.md = text => `<div class="md-plain">${escapeHtml(text)}</div>`;
    Q.mdInline = text => `<span class="md-plain">${escapeHtml(text)}</span>`;
    return;
  }
  const renderer = new marked.Renderer();
  renderer.code = (a, b) => {
    const code = typeof a === 'object' && a ? String(a.text || '') : String(a == null ? '' : a);
    const lang = (typeof a === 'object' && a ? String(a.lang || '') : String(b == null ? '' : b)).trim().split(/\s+/)[0] || '';
    const html = highlight(code, lang.toLowerCase()) || escapeHtml(code);
    const tag = lang.toLowerCase() ? ` data-lang="${escapeHtml(lang.toLowerCase())}"` : '';
    return `<pre class="code-block"${tag}><code class="${lang ? `language-${escapeHtml(lang.toLowerCase())} ` : ''}hljs">${html}</code></pre>\n`;
  };
  marked.setOptions({renderer, gfm: true, breaks: true});
  const sanitize = html => DOMPurify.sanitize(html);
  Q.md = text => (text == null || text === '' ? '' : sanitize(marked.parse(String(text))));
  Q.mdInline = text => (text == null || text === '' ? '' : sanitize(marked.parseInline(String(text))));
})();
