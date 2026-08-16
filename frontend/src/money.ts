/**
 * Money is carried as integer cents and only turned into text at the edge.
 *
 * Parsing and formatting live together here because they have to agree: a
 * value typed as "6,50" must come back as "6.50", and a total assembled from
 * twenty such values must never drift the way floating point would.
 */

/** Cents to a display string, without the currency symbol. */
export function formatCents(cents: number | null | undefined): string {
  if (cents == null) return ''
  return (cents / 100).toFixed(2)
}

/**
 * A typed amount to cents. Accepts both decimal separators, since a keyboard
 * set to Italian produces a comma and nobody should have to think about it.
 */
export function parseAmount(text: string): number | null {
  const cleaned = text.trim().replace(',', '.')
  if (cleaned === '') return null
  const value = Number(cleaned)
  if (!Number.isFinite(value) || value < 0) return null
  return Math.round(value * 100)
}
