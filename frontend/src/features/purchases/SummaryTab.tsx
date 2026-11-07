import { useSearchParams } from 'react-router'
import { t } from '../../i18n/t'
import { usePurchaseSummary } from './api'
import ColumnChart from './ColumnChart'
import SummaryTable from './SummaryTable'

const monthName = new Intl.DateTimeFormat('it-IT', { month: 'long' })
const capitalised = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

export default function SummaryTab() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('anno')
  const anno = raw ? Number(raw) : NaN
  // The server answers 400 outside 2000-2100, so anything else is ignored.
  const requested =
    Number.isInteger(anno) && anno >= 2000 && anno <= 2100
      ? anno
      : new Date().getFullYear()
  const { data, isPending, isError, refetch } = usePurchaseSummary(requested)

  if (isPending) return <p className="muted">{t('common.loading')}</p>
  if (isError)
    return (
      <div className="error" role="alert">
        {t('error.generic')}{' '}
        <button className="quiet" onClick={() => refetch()}>
          {t('common.retry')}
        </button>
      </div>
    )
  if (data.year === null) return <p className="empty">{t('summary.none')}</p>

  return (
    <>
      <div className="field summary-year">
        <label htmlFor="summary-year">{t('summary.year')}</label>
        <select
          id="summary-year"
          value={data.year}
          onChange={(e) => {
            const next = new URLSearchParams(params)
            next.set('anno', e.target.value)
            setParams(next)
          }}
        >
          {data.years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </div>
      <section className="summary-block">
        <h2>{t('summary.months')}</h2>
        <ColumnChart
          label={t('summary.chartMonths', { year: data.year })}
          averageCents={data.monthTotals.averageCents}
          columns={data.months.map((m) => {
            const full = capitalised(
              monthName.format(new Date(2000, m.month - 1, 1)),
            )
            return { ...m, short: full.charAt(0), full }
          })}
        />
        <SummaryTable
          firstColumn={t('summary.month')}
          totals={data.monthTotals}
          rows={data.months.map((m) => {
            const full = capitalised(
              monthName.format(new Date(2000, m.month - 1, 1)),
            )
            return { ...m, label: full.charAt(0), fullLabel: full }
          })}
        />
      </section>
      <section className="summary-block">
        <h2>{t('summary.years')}</h2>
        <ColumnChart
          label={t('summary.chartYears')}
          averageCents={data.yearTotals.averageCents}
          columns={data.byYear.map((y) => ({
            ...y,
            short: String(y.year),
            full: String(y.year),
          }))}
        />
        <SummaryTable
          firstColumn={t('summary.year')}
          totals={data.yearTotals}
          rows={data.byYear.map((y) => ({
            ...y,
            label: String(y.year),
            fullLabel: String(y.year),
            hasLists: true,
          }))}
        />
      </section>
    </>
  )
}
