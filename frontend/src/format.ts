/**
 * Turning stored values into text, and typed text back into stored values.
 *
 * Money is carried as integer cents everywhere and only becomes a string
 * here: parsing and formatting live together because they have to agree —
 * a value typed as "6,50" must come back as "6.50", and a total assembled
 * from twenty such values must never drift the way floating point would.
 */

/** Cents to a display string, without the currency symbol. */
export function formatCents(
  cents: number | null | undefined,
  locale = 'it-CH',
): string {
  if (cents == null) return ''
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(cents / 100)
}

/**
 * A typed amount to cents. Accepts both decimal separators, since a keyboard
 * set to Italian produces a comma and nobody should have to think about it.
 */
export function parseAmount(text: string): number | null {
  // Intl uses an apostrophe as the Swiss thousands separator. Remove both
  // its straight and typographic forms (plus spaces) so a displayed amount
  // can be opened in an input and saved again unchanged.
  const cleaned = text
    .trim()
    .replace(/[\s'’]/g, '')
    .replace(',', '.')
  if (cleaned === '') return null
  const value = Number(cleaned)
  if (!Number.isFinite(value) || value < 0) return null
  return Math.round(value * 100)
}

export function monthNames(locale = 'it-CH'): string[] {
  const formatter = new Intl.DateTimeFormat(locale, {
    month: 'long',
    timeZone: 'UTC',
  })
  return Array.from({ length: 12 }, (_, month) =>
    formatter.format(new Date(Date.UTC(2020, month, 1))),
  )
}

/** "luglio 2026", or an empty string when the list covers no month. */
export function formatPeriod(
  year: number | null,
  month: number | null,
  locale = 'it-CH',
): string {
  if (year == null || month == null) return ''
  return new Intl.DateTimeFormat(locale, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, 1)))
}

/** "14 luglio 2026" from an ISO date. */
export function formatDate(iso: string, locale = 'it-CH'): string {
  const [year, month, day] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat(locale, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, day)))
}
