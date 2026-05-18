(function () {
  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function protect(src, reg, kind, bag) {
    return src.replace(reg, function (m) {
      var key = '__TK_' + bag.length + '__';
      bag.push({ key: key, text: '<span class="' + kind + '">' + escapeHtml(m) + '</span>' });
      return key;
    });
  }

  function applyJavaHighlight(code) {
    var s = String(code || '').replace(/\r\n?/g, '\n');
    var bag = [];

    // 先保护注释和字符串，避免后续关键字替换污染
    s = protect(s, /\/\*[\s\S]*?\*\//g, 'hl-c', bag);
    s = protect(s, /\/\/[^\n]*/g, 'hl-c', bag);
    s = protect(s, /"(?:\\.|[^"\\])*"/g, 'hl-s', bag);
    s = protect(s, /'(?:\\.|[^'\\])'/g, 'hl-s', bag);

    s = escapeHtml(s);

    // 注解
    s = s.replace(/(^|[^\w])(@[A-Za-z_][A-Za-z0-9_]*)/g, '$1<span class="hl-a">$2</span>');

    // 数字
    s = s.replace(/\b(0x[0-9a-fA-F]+|\d+(?:\.\d+)?[dDfFlL]?)\b/g, '<span class="hl-n">$1</span>');

    // Java 关键字
    var kw = 'abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|var|record|sealed|permits|non-sealed';
    s = s.replace(new RegExp('\\b(' + kw + ')\\b', 'g'), '<span class="hl-k">$1</span>');

    // 常见类型
    s = s.replace(/\b(String|Object|Integer|Long|Double|Float|Boolean|Character|Byte|Short|List|Map|Set|HashMap|ArrayList|LinkedList|Thread|Class|Exception|RuntimeException)\b/g, '<span class="hl-t">$1</span>');

    // 恢复注释/字符串
    for (var i = 0; i < bag.length; i++) {
      var t = bag[i];
      s = s.replace(t.key, t.text);
    }

    return s;
  }

  window.renderJavaHighlighted = function (codeEl, code) {
    codeEl.innerHTML = applyJavaHighlight(code);
  };
})();
