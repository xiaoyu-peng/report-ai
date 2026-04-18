import MarkdownIt from 'markdown-it'

export const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false
})

export function renderReportMarkdown(raw: string, sectionEditable = false): string {
  if (!raw) return ''
  const html = md.render(raw)
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

  if (!sectionEditable) return out

  let sectionIndex = 0
  let inSection = false
  const result: string[] = []
  const sectionBtns = `<div class="section-actions"><button class="section-action-btn" data-mode="rewrite">改写</button><button class="section-action-btn" data-mode="expand">扩写</button><button class="section-action-btn" data-mode="condense">精简</button></div>`

  const parts = out.split(/(<h[23][^>]*>.*?<\/h[23]>)/gs)

  for (const part of parts) {
    if (/<h[23][^>]*>/.test(part)) {
      if (inSection) {
        result.push(sectionBtns)
        result.push('</div>')
      }
      result.push(`<div class="section-block" data-section-id="${sectionIndex++}">`)
      result.push(part)
      inSection = true
    } else {
      if (!inSection && part.trim()) {
        result.push(`<div class="section-block" data-section-id="${sectionIndex++}">`)
        result.push(part)
        result.push(sectionBtns)
        result.push('</div>')
      } else {
        result.push(part)
      }
    }
  }
  if (inSection) {
    result.push(sectionBtns)
    result.push('</div>')
  }

  return result.join('')
}
