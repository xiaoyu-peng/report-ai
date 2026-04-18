import MarkdownIt from 'markdown-it'

/**
 * Shared markdown-it instance for report content rendering.
 *
 * - `html: false` — backend is trusted but we still refuse raw HTML to avoid
 *   accidental injection of angle-bracket tokens as markup.
 * - `linkify` + `breaks` tuned for Chinese report text where soft line breaks
 *   should be preserved as paragraph separators.
 */
export const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false
})

/**
 * Render markdown, then post-process `[n]` citation markers into `<sup>` tags
 * carrying `data-idx`. We do the substitution AFTER markdown-it runs so that
 * markdown-it doesn't accidentally swallow them (e.g. mistake `[1]` for a
 * reference-style link).
 *
 * The regex intentionally skips markers already wrapped in an attribute value
 * by looking for a preceding `"` — belt-and-suspenders against double wrapping.
 */
export function renderReportMarkdown(raw: string): string {
  if (!raw) return ''
  const html = md.render(raw)
  // Only replace [n] patterns that appear in text content, not inside tags.
  // Simple approach: walk character by character tracking angle-bracket depth.
  let depth = 0
  let out = ''
  for (let i = 0; i < html.length; i++) {
    const ch = html[i]
    if (ch === '<') {
      depth++
      out += ch
      continue
    }
    if (ch === '>') {
      depth = Math.max(0, depth - 1)
      out += ch
      continue
    }
    if (depth === 0 && ch === '[') {
      // Try to match [digits]
      const m = html.slice(i).match(/^\[(\d+)\]/)
      if (m) {
        const idx = m[1]
        out += `<sup class="cite" data-idx="${idx}">[${idx}]</sup>`
        i += m[0].length - 1
        continue
      }
    }
    out += ch
  }
  return out
}
