const fmt = new Intl.DateTimeFormat('it-IT', { month: 'long', year: 'numeric' })

/** "ottobre 2026" */
export const monthLabel = (month: number, year: number) =>
  fmt.format(new Date(year, month - 1, 1))

const short = new Intl.DateTimeFormat('it-IT', { month: 'short' })

/** "OTT" */
export const monthShort = (month: number) =>
  short
    .format(new Date(2000, month - 1, 1))
    .replace('.', '')
    .toUpperCase()

const long = new Intl.DateTimeFormat('it-IT', { month: 'long' })

/** "Ottobre" */
export const monthName = (month: number) => {
  const name = long.format(new Date(2000, month - 1, 1))
  return name.charAt(0).toUpperCase() + name.slice(1)
}
