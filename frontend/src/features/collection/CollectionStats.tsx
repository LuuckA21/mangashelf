import type { StatBar } from '../../api/types'
import { t, type TranslationKey } from '../../i18n/t'
import { useCollectionStats } from './api'

function Chart({
  title,
  bars,
  label,
}: {
  title: string
  bars: StatBar[]
  label: (key: string | null) => string
}) {
  if (bars.length === 0) return null
  const max = Math.max(...bars.map((b) => b.volumes))
  return (
    <>
      <h3>{title}</h3>
      <ul className="stat-bars">
        {bars.map((b) => {
          const name = label(b.key)
          return (
            <li key={b.key ?? ''}>
              <span className="sr-only">
                {b.volumes === 1
                  ? t('stats.barOne', { label: name })
                  : t('stats.bar', { label: name, n: b.volumes })}
              </span>
              <span className="stat-row" aria-hidden="true">
                <span className="stat-name">{name}</span>
                <span className="stat-n">{b.volumes}</span>
                <span className="stat-track">
                  <span
                    className="stat-bar"
                    style={{ width: `${(b.volumes / max) * 100}%` }}
                  />
                </span>
              </span>
            </li>
          )
        })}
      </ul>
    </>
  )
}

/** Numbers and charts for the user's whole collection; hidden while loading or empty. */
export function CollectionStats() {
  const { data, isPending, isError, refetch } = useCollectionStats()
  if (isError)
    return (
      <section className="panel stats-panel">
        <div className="error" role="status">
          {t('stats.unavailable')}{' '}
          <button className="quiet" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      </section>
    )
  if (isPending || data.editions === 0) return null

  const numbers: [string, string | number][] = [
    [t('stats.volumes'), data.ownedVolumes],
    [t('stats.editions'), data.editions],
    [t('stats.series'), data.series],
    [t('stats.completed'), data.completedEditions],
    [t('stats.missing'), data.totalVolumes - data.ownedVolumes],
    // Rounded down (100% only when nothing is missing); multiply first so it stays exact.
    [
      t('stats.completion'),
      `${Math.floor((data.ownedVolumes * 100) / data.totalVolumes)}%`,
    ],
  ]
  return (
    <section
      className="panel stats-panel"
      aria-labelledby="stats-title"
      // Scrolls inside itself on short windows: focusable so the keyboard can scroll it.
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
    >
      <h2 id="stats-title">{t('stats.title')}</h2>
      <dl className="stat-numbers">
        {numbers.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <Chart
        title={t('stats.byPublisher')}
        bars={data.byPublisher}
        label={(k) => k ?? t('stats.others')}
      />
      <Chart
        title={t('stats.byType')}
        bars={data.byType}
        label={(k) => t(`type.${k}` as TranslationKey)}
      />
      <Chart
        title={t('stats.byStatus')}
        bars={data.byStatus}
        label={(k) => t(`stats.status.${k}` as TranslationKey)}
      />
    </section>
  )
}
