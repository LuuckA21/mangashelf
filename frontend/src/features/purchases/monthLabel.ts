const fmt = new Intl.DateTimeFormat('it-IT', { month: 'long', year: 'numeric' })

/** "ottobre 2026" */
export const monthLabel = (month: number, year: number) =>
  fmt.format(new Date(year, month - 1, 1))
