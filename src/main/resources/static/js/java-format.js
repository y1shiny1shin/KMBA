(function () {
  function normalizeLines(src) {
    return String(src || '').replace(/\r\n?/g, '\n').split('\n');
  }

  function formatJavaSource(src) {
    const lines = normalizeLines(src);
    let indent = 0;
    let inBlockComment = false;
    const out = [];

    for (let raw of lines) {
      let line = raw.replace(/\t/g, '    ').trim();
      if (!line) {
        out.push('');
        continue;
      }

      const startsWithClose = /^([}\)])/.test(line);
      const startsCase = /^(case\s+.*:|default\s*:)/.test(line);
      let curIndent = indent;

      if (startsWithClose) curIndent = Math.max(0, curIndent - 1);
      if (startsCase) curIndent = Math.max(0, curIndent - 1);

      out.push('    '.repeat(Math.max(0, curIndent)) + line);

      if (!inBlockComment) {
        if (/\/\*/.test(line) && !/\*\//.test(line)) inBlockComment = true;
      } else {
        if (/\*\//.test(line)) inBlockComment = false;
        continue;
      }

      const openBraces = (line.match(/{/g) || []).length;
      const closeBraces = (line.match(/}/g) || []).length;
      indent += openBraces - closeBraces;

      if (/\b(case\s+.*:|default\s*:)\s*$/.test(line)) indent += 1;
      if (indent < 0) indent = 0;
    }

    return out.join('\n');
  }

  window.formatJavaSource = formatJavaSource;
})();
