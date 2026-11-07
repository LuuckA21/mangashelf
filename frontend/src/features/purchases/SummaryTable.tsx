import type { SpendTotals } from '../../api/types'
import { t } from '../../i18n/t'
import { formatChfGrouped } from './money'

export type SummaryRow = {
  label: string
  fullLabel: string
  spentCents: number
  upcomingCents: number
  hasLists: boolean
  aboveAverage: boolean
}

export default function SummaryTable({
  rows,
  totals,
  firstColumn,
}: {
  rows: SummaryRow[]
  totals: SpendTotals
  firstColumn: string
}) {
  const foot: [string, number][] = [
    [t('summary.total'), totals.totalCents],
    [t('summary.average'), totals.averageCents],
    [t('summary.totalSpent'), totals.spentCents],
    [t('summary.totalUpcoming'), totals.upcomingCents],
  ]
  return (
    <table className="summary-table">
      <thead>
        <tr>
          <th scope="col">{firstColumn}</th>
          <th scope="col" className="num">
            {t('summary.spent')}
          </th>
          <th scope="col" className="num">
            {t('summary.upcoming')}
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr
            key={r.fullLabel}
            className={r.aboveAverage ? 'above' : undefined}
          >
            <td>
              {r.fullLabel}
              {r.aboveAverage && (
                <>
                  {' '}
                  <span aria-hidden="true">▲</span>
                  <span className="sr-only">{t('summary.aboveAverage')}</span>
                </>
              )}
            </td>
            <td className="num">
              {r.hasLists ? formatChfGrouped(r.spentCents) : '—'}
            </td>
            <td className="num">
              {r.hasLists ? formatChfGrouped(r.upcomingCents) : '—'}
            </td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        {foot.map(([label, cents], i) => (
          <tr key={label} className={i === 0 ? 'foot-first' : undefined}>
            <td>{label}</td>
            <td className="num" colSpan={2}>
              {formatChfGrouped(cents)}
            </td>
          </tr>
        ))}
      </tfoot>
    </table>
  )
}
