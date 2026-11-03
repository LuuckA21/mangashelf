import { useState } from 'react'
import { useI18n } from '../i18n'

interface Props {
  /** Highest positive slot: the declared total, or the highest owned volume. */
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
 * Volume 0 is always offered as an optional slot: some editions have one,
 * but its absence must never be reported as a gap for editions that do not.
 */
export default function Shelf({ upTo, ownedNumbers, onToggle }: Props) {
  const { t } = useI18n()
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
  const numbers = [0, ...Array.from({ length: last }, (_, i) => i + 1)]

  if (ownedNumbers.length === 0) {
    return (
      <>
        <div className="empty" style={{ marginBottom: 16 }}>
          {t('shelf.empty')}
        </div>
        <div className="shelf">
          {numbers.map((n) => (
            <button
              key={n}
              className={`tile missing future${busy === n ? ' busy' : ''}`}
              onClick={() => toggle(n)}
              disabled={busy !== null}
              aria-pressed="false"
              aria-label={`Volume ${n} ${t('shelf.notOwnedAdd')}`}
              title={
                n === 0
                  ? t('shelf.volumeZeroAdd')
                  : `Volume ${n} — ${t('shelf.notOwnedTitle')}`
              }
            >
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
          // The same treatment keeps an unowned volume 0 optional.
          const beyond = n === 0 ? !isOwned : n > upTo
          return (
            <button
              key={n}
              className={
                `tile${isOwned ? '' : ' missing'}${beyond ? ' future' : ''}` +
                (busy === n ? ' busy' : '')
              }
              onClick={() => toggle(n)}
              disabled={busy !== null}
              aria-pressed={isOwned}
              aria-label={
                isOwned
                  ? `Volume ${n} ${t('shelf.ownedRemove')}`
                  : `Volume ${n} ${beyond ? t('shelf.notOwnedAdd') : t('shelf.missingAdd')}`
              }
              title={
                isOwned
                  ? `Volume ${n} — ${t('shelf.ownedTitle')}`
                  : n === 0
                    ? t('shelf.volumeZeroAdd')
                    : beyond
                      ? `Volume ${n} — ${t('shelf.notOwnedTitle')}`
                      : `Volume ${n} — ${t('shelf.missingTitle')}`
              }
            >
              {n}
            </button>
          )
        })}
      </div>
      <div className="shelf-legend">
        <span>
          <i className="swatch owned" /> {t('shelf.owned')}
        </span>
        <span>
          <i className="swatch missing" /> {t('shelf.missing')}
        </span>
      </div>
    </>
  )
}
