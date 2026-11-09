/** "7.90": cents as the text an amount field shows. */
export const amountText = (c: number) =>
  `${Math.floor(c / 100)}.${String(c % 100).padStart(2, '0')}`

export const formatChf = (cents: number) => `CHF ${amountText(cents)}`
/** Summary amounts: thousands with an apostrophe, "CHF 16'062.10". */
export const formatChfGrouped = (cents: number) =>
  `CHF ${amountText(cents).replace(/\B(?=(\d{3})+\.)/g, "'")}`
export const formatEur = (cents: number) => `EUR ${amountText(cents)}`

/** "7.90", "7,90" or "7" to cents; null when not a non-negative amount. */
export function parseAmount(text: string): number | null {
  const m = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(text.trim())
  return m ? Number(m[1]) * 100 + Number((m[2] ?? '').padEnd(2, '0')) : null
}

/** EUR cents times the rate, rounded to the nearest 10 cents (halves up), in integers. */
export function proposeChf(eurCents: number, rate: number): number {
  const scaled = eurCents * Math.round(rate * 100) // cents * 100
  return Math.floor((scaled + 500) / 1000) * 10
}
