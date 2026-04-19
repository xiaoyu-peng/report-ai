import { Mark, mergeAttributes } from '@tiptap/core'

/**
 * Tiptap Mark：把正文里的 [n] 包成 <sup data-citation="n" class="citation-marker">[n]</sup>。
 * 渲染后前端通过监听 click [data-citation] 来弹 popover。
 */
export const CitationMark = Mark.create({
  name: 'citation',
  inclusive: false,
  excludes: '',
  parseHTML() {
    return [{ tag: 'sup[data-citation]' }]
  },
  addAttributes() {
    return {
      marker: {
        default: null,
        parseHTML: el => (el as HTMLElement).getAttribute('data-citation'),
        renderHTML: attrs => ({ 'data-citation': attrs.marker, class: 'citation-marker' }),
      },
    }
  },
  renderHTML({ HTMLAttributes }) {
    return ['sup', mergeAttributes(HTMLAttributes), 0]
  },
})
