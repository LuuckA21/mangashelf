import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const css = readFileSync('src/styles.css', 'utf8')

function luminance(hex: string) {
  const channels = hex.match(/[0-9a-f]{2}/gi)?.map((part) => {
    const value = Number.parseInt(part, 16) / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  })
  if (!channels || channels.length !== 3)
    throw new Error(`Invalid colour: ${hex}`)
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

function contrast(first: string, second: string) {
  const lighter = Math.max(luminance(first), luminance(second))
  const darker = Math.min(luminance(first), luminance(second))
  return (lighter + 0.05) / (darker + 0.05)
}

describe('responsive accessibility tokens', () => {
  it('keeps muted text above WCAG AA contrast on both paper surfaces', () => {
    expect(contrast('#706b63', '#f1efe8')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#706b63', '#fbfaf7')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#c93427', '#f1efe8')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#c93427', '#ffffff')).toBeGreaterThanOrEqual(4.5)
  })

  it('keeps mobile controls at a thumb-sized 44 pixel minimum', () => {
    expect(css).toContain('@media (max-width: 700px)')
    expect(css).toMatch(/button,\s*input,\s*select\s*{\s*min-height: 44px;/)
    expect(css).toMatch(/\.reserve-toggle\s*{\s*width: 44px;\s*height: 44px;/)
    expect(css).toMatch(
      /\.link-button\s*{[\s\S]*?min-width: 44px;[\s\S]*?min-height: 44px;/,
    )
    expect(css).toMatch(/\.topbar nav\s*{[\s\S]*?flex-wrap: wrap;/)
    expect(css).toMatch(
      /\.purchase-table tr\s*{[\s\S]*?display: flex;[\s\S]*?flex-wrap: wrap;/,
    )
    expect(css).toMatch(
      /\.catalog-actions\s*{\s*display: grid;\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/,
    )
    expect(css).toMatch(
      /\.catalog-actions button,\s*\.new-purchase-list\s*{\s*width: 100%;/,
    )
    expect(css).toMatch(/\.stats-table-scroll\s*{\s*display: none;/)
    expect(css).toMatch(/\.stats-cards\s*{\s*display: grid;/)
    expect(css).toContain('content: attr(data-label)')
    expect(css).toMatch(
      /\.topbar-inner > \.account-link\s*{\s*max-width: 24vw;/,
    )
  })
})
