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
  // [label, amount, column]: the total and average span both columns.
  const foot: [string, number, 'spent' | 'upcoming' | 'both'][] = [
    [t('summary.total'), totals.totalCents, 'both'],
    [t('summary.average'), totals.averageCents, 'both'],
    [t('summary.totalSpent'), totals.spentCents, 'spent'],
    [t('summary.totalUpcoming'), totals.upcomingCents, 'upcoming'],
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
            <th scope="row">
              {r.fullLabel}
              {r.aboveAverage && (
                <>
                  {' '}
                  <span aria-hidden="true">▲</span>
                  <span className="sr-only">{t('summary.aboveAverage')}</span>
                </>
              )}
            </th>
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
        {foot.map(([label, cents, col], i) => (
          <tr key={label} className={i === 0 ? 'foot-first' : undefined}>
            <th scope="row">{label}</th>
            {col === 'both' ? (
              <td className="num" colSpan={2}>
                {formatChfGrouped(cents)}
              </td>
            ) : (
              <>
                <td className="num">
                  {col === 'spent' && formatChfGrouped(cents)}
                </td>
                <td className="num">
                  {col === 'upcoming' && formatChfGrouped(cents)}
                </td>
              </>
            )}
          </tr>
        ))}
      </tfoot>
    </table>
  )
}
