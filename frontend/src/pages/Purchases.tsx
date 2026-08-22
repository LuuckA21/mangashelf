import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  purchases,
  type PurchaseListSummary,
  type PurchaseStats,
  type YearStats,
} from '../api/client'
import { formatCents, formatPeriod, monthNames } from '../format'
import Layout from '../components/Layout'
import { useI18n } from '../i18n'

/** Index of the purchase lists, newest first. */
export default function Purchases() {
  const { locale, t } = useI18n()
  const months = monthNames(locale)
  const [lists, setLists] = useState<PurchaseListSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  // Defaults to the month being planned, which is the one being written
  // almost every time — and a wrong guess is one field away from right.
  const [month, setMonth] = useState(String(new Date().getMonth() + 1))
  const [year, setYear] = useState(String(new Date().getFullYear()))
  const [adding, setAdding] = useState(false)
  const [stats, setStats] = useState<PurchaseStats | null>(null)

  useEffect(() => {
    purchases
      .listAll()
      .then(setLists)
      .catch(() => setError(t('purchases.loadFailed')))
      .finally(() => setLoading(false))
    purchases
      .stats()
      .then(setStats)
      .catch(() => undefined)
  }, [t])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    try {
      const created = await purchases.create({
        name,
        // Sent together or not at all: the schema rejects one without the
        // other, since a month with no year identifies nothing.
        periodYear: year && month ? Number(year) : null,
        periodMonth: year && month ? Number(month) : null,
      })
      setLists((current) => [
        {
          id: created.id,
          name: created.name,
          periodYear: created.periodYear,
          periodMonth: created.periodMonth,
          paidAt: created.paidAt,
          itemCount: 0,
          reservedCount: 0,
          purchasedCount: 0,
          totalChfCents: 0,
        },
        ...current,
      ])
      setName('')
      setAdding(false)
    } catch {
      setError(t('purchases.createFailed'))
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {lists.length} {t('purchases.listCount')}
        </p>
        <h1>{t('purchases.title')}</h1>
      </div>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      <div className="row purchase-create-actions" style={{ marginBottom: 24 }}>
        <button
          className="new-purchase-list"
          onClick={() => setAdding(!adding)}
        >
          {adding ? t('common.cancel') : t('purchases.newList')}
        </button>
      </div>

      {adding && (
        <form
          className="panel"
          onSubmit={handleCreate}
          style={{ marginBottom: 24 }}
        >
          <div className="field">
            <label htmlFor="listName">{t('purchases.name')}</label>
            <input
              id="listName"
              value={name}
              required
              autoFocus
              placeholder={t('purchases.namePlaceholder')}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="grid-2">
            <div className="field">
              <label htmlFor="listMonth">{t('purchases.month')}</label>
              <select
                id="listMonth"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
              >
                <option value="">{t('common.none')}</option>
                {months.map((label, index) => (
                  <option key={label} value={index + 1}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="listYear">{t('purchases.year')}</label>
              <input
                id="listYear"
                type="number"
                min={1900}
                max={2200}
                value={year}
                onChange={(e) => setYear(e.target.value)}
              />
            </div>
          </div>
          <button type="submit">{t('common.create')}</button>
        </form>
      )}

      {loading ? (
        <p className="muted">{t('common.loading')}</p>
      ) : lists.length === 0 ? (
        <div className="empty">{t('purchases.empty')}</div>
      ) : (
        <ul className="edition-list">
          {lists.map((list) => (
            <li key={list.id}>
              <Link to={`/purchases/${list.id}`}>
                <div className="name">
                  {list.name}
                  {list.paidAt && (
                    <span className="paid-badge">{t('purchases.paid')}</span>
                  )}
                </div>
                <div className="muted" style={{ fontSize: 14 }}>
                  {[
                    formatPeriod(list.periodYear, list.periodMonth, locale),
                    `${list.itemCount} ${
                      list.itemCount === 1
                        ? t('common.volume')
                        : t('common.volumes')
                    }`,
                    list.reservedCount > 0 && !list.paidAt
                      ? `${list.reservedCount} ${t('purchases.reserved')}`
                      : null,
                    list.purchasedCount > 0 &&
                    list.purchasedCount < list.itemCount
                      ? `${list.purchasedCount} ${t('purchases.purchased')}`
                      : null,
                    `CHF ${formatCents(list.totalChfCents, locale)}`,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {stats && stats.years.length > 0 && <Stats stats={stats} />}
    </Layout>
  )
}

/**
 * What the lists have cost, by year.
 *
 * <p>Full and discounted side by side rather than one or the other: the gap
 * between them is what the shop's discount is worth over a year, which is
 * the figure a spreadsheet of monthly lists never shows.
 */
function Stats({ stats }: { stats: PurchaseStats }) {
  const { locale, t } = useI18n()
  return (
    <section style={{ marginTop: 48 }}>
      <p className="eyebrow">{t('purchases.stats')}</p>

      {/* On a wide screen the years remain side by side for direct comparison.
          The matching mobile cards below show the same figures without a
          horizontal table. */}
      <div className="table-scroll stats-table-scroll">
        <table className="purchase-table stats-table">
          <thead>
            <tr>
              <th>{t('purchases.year')}</th>
              <th className="num">{t('purchases.lists')}</th>
              <th className="num">{t('common.volumes')}</th>
              <th className="num">{t('purchases.full')}</th>
              <th className="num">{t('purchases.discount')}</th>
              <th className="num">{t('purchases.spent')}</th>
              <th className="num">{t('purchases.averageFull')}</th>
              <th className="num">{t('purchases.averageDiscounted')}</th>
            </tr>
          </thead>
          <tbody>
            {stats.years.map((year) => (
              <tr key={year.year}>
                <td>{year.year}</td>
                <td className="num">{year.listCount}</td>
                <td className="num">{year.volumeCount}</td>
                <td className="num">
                  {formatCents(year.fullChfCents, locale)}
                </td>
                <td className="num">
                  {year.discountChfCents > 0
                    ? `−${formatCents(year.discountChfCents, locale)}`
                    : ''}
                </td>
                <td className="num">{formatCents(year.netChfCents, locale)}</td>
                <td className="num">
                  {formatCents(year.averageFullChfCents, locale)}
                </td>
                <td className="num">
                  {formatCents(year.averageNetChfCents, locale)}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="grand-total">
              <td>{t('common.total')}</td>
              <td className="num">{stats.listCount}</td>
              <td className="num">{stats.volumeCount}</td>
              <td className="num">{formatCents(stats.fullChfCents, locale)}</td>
              <td className="num">
                {stats.discountChfCents > 0
                  ? `−${formatCents(stats.discountChfCents, locale)}`
                  : ''}
              </td>
              <td className="num">{formatCents(stats.netChfCents, locale)}</td>
              <td className="num">
                {formatCents(stats.averageFullChfCents, locale)}
              </td>
              <td className="num">
                {formatCents(stats.averageNetChfCents, locale)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      <div
        className="stats-cards"
        role="region"
        aria-label={t('purchases.statsRegion')}
      >
        {stats.years.map((year) => (
          <StatsCard key={year.year} title={String(year.year)} values={year} />
        ))}
        <StatsCard title={t('common.total')} values={stats} />
      </div>

      <p className="muted" style={{ fontSize: 13 }}>
        {t('purchases.statsHelp')}
      </p>
    </section>
  )
}

/** A compact equivalent of the comparison table, shown only on small screens. */
function StatsCard({
  title,
  values,
}: {
  title: string
  values: YearStats | PurchaseStats
}) {
  const { locale, t } = useI18n()
  return (
    <article className="stats-card">
      <h2>{title}</h2>
      <dl className="stats-values">
        <div>
          <dt>{t('purchases.lists')}</dt>
          <dd>{values.listCount}</dd>
        </div>
        <div>
          <dt>{t('common.volumes')}</dt>
          <dd>{values.volumeCount}</dd>
        </div>
        <div>
          <dt>{t('purchases.full')}</dt>
          <dd>CHF {formatCents(values.fullChfCents, locale)}</dd>
        </div>
        <div>
          <dt>{t('purchases.saving')}</dt>
          <dd>
            {values.discountChfCents > 0
              ? `−CHF ${formatCents(values.discountChfCents, locale)}`
              : '—'}
          </dd>
        </div>
        <div>
          <dt>{t('purchases.spent')}</dt>
          <dd>CHF {formatCents(values.netChfCents, locale)}</dd>
        </div>
        <div>
          <dt>{t('purchases.averageFull')}</dt>
          <dd>CHF {formatCents(values.averageFullChfCents, locale)}</dd>
        </div>
        <div>
          <dt>{t('purchases.averageDiscounted')}</dt>
          <dd>CHF {formatCents(values.averageNetChfCents, locale)}</dd>
        </div>
      </dl>
    </article>
  )
}
