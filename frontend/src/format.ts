/**
 * Turning stored values into text, and typed text back into stored values.
 *
 * Money is carried as integer cents everywhere and only becomes a string
 * here: parsing and formatting live together because they have to agree —
 * a value typed as "6,50" must come back as "6.50", and a total assembled
 * from twenty such values must never drift the way floating point would.
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

const MONTHS = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
  'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre']

/** "luglio 2026", or an empty string when the list covers no month. */
export function formatPeriod(year: number | null, month: number | null): string {
  if (year == null || month == null) return ''
  return `${MONTHS[month - 1]} ${year}`
}

/** "14 luglio 2026" from an ISO date. */
export function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`
}

export { MONTHS }
