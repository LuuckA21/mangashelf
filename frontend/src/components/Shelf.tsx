import { useState } from 'react'
import { t } from '../i18n/t'

type Props = {
  volumeCount: number
  owned: number[]
  onChange: (owned: number[]) => void
  /** A save is in flight: the tile just clicked is drawn faded. */
  busy?: boolean
}

/** The volumes 1..volumeCount of an edition as numbered toggle tiles. */
export default function Shelf({ volumeCount, owned, onChange, busy }: Props) {
  const [last, setLast] = useState<number | null>(null)
  const have = new Set(owned)
  const numbers = Array.from({ length: volumeCount }, (_, i) => i + 1)

  function toggle(n: number) {
    setLast(n)
    onChange(
      have.has(n)
        ? owned.filter((o) => o !== n)
        : [...owned, n].sort((a, b) => a - b),
    )
  }

  return (
    <>
      <div className="inline-actions">
        <button className="quiet" onClick={() => onChange(numbers)}>
          {t('shelf.markAll')}
        </button>
        <button className="quiet" onClick={() => onChange([])}>
          {t('shelf.markNone')}
        </button>
      </div>
      <div
        className="progress"
        role="progressbar"
        aria-label={t('shelf.progressLabel')}
        aria-valuemin={0}
        aria-valuemax={volumeCount}
        aria-valuenow={owned.length}
      >
        <i
          style={{
            width: `${volumeCount ? (owned.length / volumeCount) * 100 : 0}%`,
          }}
        />
      </div>
      <div className="shelf">
        {numbers.map((n) => {
          const isOwned = have.has(n)
          return (
            <button
              key={n}
              className={`tile${isOwned ? '' : ' missing'}${busy && last === n ? ' busy' : ''}`}
              aria-pressed={isOwned}
              aria-label={t(
                isOwned ? 'shelf.volumeOwned' : 'shelf.volumeMissing',
                { n },
              )}
              onClick={() => toggle(n)}
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
