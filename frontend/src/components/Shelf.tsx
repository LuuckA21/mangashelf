import { useState } from 'react'

interface Props {
  /** Where the shelf stops: the declared total, or the highest owned volume. */
  upTo: number
  ownedNumbers: number[]
  onToggle: (number: number, owned: boolean) => Promise<void>
}

/** How many empty slots follow the last volume, so the next one is a click away. */
const LOOKAHEAD = 3

/**
 * The volumes of an edition, as a numbered grid.
 *
 * <p>Nothing anywhere records which volumes an edition contains, because
 * nobody knows what a publisher has released. The grid is therefore drawn
 * from what is owned: numbers up to the highest one held, so a gap in the
 * middle shows itself, plus a few slots past the end for what comes next.
 */
export default function Shelf({ upTo, ownedNumbers, onToggle }: Props) {
  const owned = new Set(ownedNumbers)
  const [busy, setBusy] = useState<number | null>(null)

  async function toggle(number: number) {
    setBusy(number)
    try {
      await onToggle(number, owned.has(number))
    } finally {
      setBusy(null)
    }
  }

  const last = upTo + LOOKAHEAD
  const numbers = Array.from({ length: last }, (_, i) => i + 1)

  if (upTo === 0) {
    return (
      <>
        <div className="empty" style={{ marginBottom: 16 }}>
          Nessun volume segnato. Clicca il primo qui sotto, o usa l’intervallo.
        </div>
        <div className="shelf">
          {numbers.map((n) => (
            <button key={n} className="tile missing future" onClick={() => toggle(n)}>
              {n}
            </button>
          ))}
        </div>
      </>
    )
  }

  return (
    <>
      <div className="shelf">
        {numbers.map((n) => {
          const isOwned = owned.has(n)
          // Past the end the slot is drawn fainter: those numbers may not
          // exist yet, and showing them as gaps would invent a shortfall.
          const beyond = n > upTo
          return (
            <button
              key={n}
              className={`tile${isOwned ? '' : ' missing'}${beyond ? ' future' : ''}`
                + (busy === n ? ' busy' : '')}
              onClick={() => toggle(n)}
              aria-pressed={isOwned}
              title={
                isOwned
                  ? `Volume ${n} — ce l'hai. Clicca per toglierlo.`
                  : beyond
                    ? `Volume ${n} — clicca se l'hai preso`
                    : `Volume ${n} — ti manca. Clicca per aggiungerlo.`
              }
            >
              {n}
            </button>
          )
        })}
      </div>
      <div className="shelf-legend">
        <span><i className="swatch owned" /> Posseduto</span>
        <span><i className="swatch missing" /> Mancante</span>
      </div>
    </>
  )
}
