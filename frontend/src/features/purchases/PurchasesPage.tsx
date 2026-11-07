import { useState, type KeyboardEvent, type SyntheticEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError, fieldMessage } from '../../api/client'
import { t } from '../../i18n/t'
import { useCreatePurchaseList, usePurchaseLists } from './api'
import { formatChf } from './money'
import { monthLabel } from './monthLabel'
import SummaryTab from './SummaryTab'

function CreateForm() {
  const navigate = useNavigate()
  const create = useCreatePurchaseList()
  const now = new Date()
  const [description, setDescription] = useState('')
  const [month, setMonth] = useState(
    `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`,
  )

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    const [year, m] = month.split('-').map(Number)
    try {
      const made = await create.mutateAsync({
        description: description.trim(),
        month: m,
        year,
      })
      navigate(`/purchases/${made.id}`)
    } catch {
      // shown from create.error
    }
  }

  const err = create.error
  const descError = fieldMessage(err, 'description')
  const monthError = fieldMessage(err, 'month') ?? fieldMessage(err, 'year')
  const general =
    err &&
    (err instanceof ApiError
      ? err.errors.length === 0 && err.detail
      : t('error.generic'))

  return (
    <form className="panel purchase-create" onSubmit={submit}>
      <h2>{t('purchases.newTitle')}</h2>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor="purchase-description">
          {t('purchases.descriptionLabel')}
        </label>
        <input
          id="purchase-description"
          maxLength={100}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          aria-invalid={descError ? true : undefined}
          aria-describedby={
            descError ? 'purchase-description-error' : undefined
          }
        />
        {descError && (
          <p id="purchase-description-error" className="field-help">
            {descError}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="purchase-month">{t('purchases.monthLabel')}</label>
        <input
          id="purchase-month"
          type="month"
          required
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          aria-invalid={monthError ? true : undefined}
        />
        {monthError && <p className="field-help">{monthError}</p>}
      </div>
      <button type="submit" disabled={create.isPending}>
        {t('purchases.create')}
      </button>
    </form>
  )
}

const TABS = [
  { id: 'liste', label: 'purchases.tabLists' },
  { id: 'riepilogo', label: 'purchases.tabSummary' },
] as const

function Tabs({ active }: { active: 'liste' | 'riepilogo' }) {
  const [, setParams] = useSearchParams()
  function select(id: string) {
    if (id === active) return
    // Liste is the bare URL: no other param belongs to it.
    setParams(id === 'riepilogo' ? { vista: 'riepilogo' } : {})
  }
  function onKeyDown(e: KeyboardEvent<HTMLElement>) {
    const i = TABS.findIndex((x) => x.id === active)
    const to =
      e.key === 'ArrowRight'
        ? (i + 1) % TABS.length
        : e.key === 'ArrowLeft'
          ? (i + TABS.length - 1) % TABS.length
          : e.key === 'Home'
            ? 0
            : e.key === 'End'
              ? TABS.length - 1
              : -1
    if (to < 0) return
    e.preventDefault()
    select(TABS[to].id)
    document.getElementById(`tab-${TABS[to].id}`)?.focus()
  }
  return (
    <div
      className="chips purchase-tabs"
      role="tablist"
      aria-label={t('purchases.tabs')}
    >
      {TABS.map((x) => (
        <button
          key={x.id}
          id={`tab-${x.id}`}
          type="button"
          role="tab"
          className={`chip${x.id === active ? ' active' : ''}`}
          aria-selected={x.id === active}
          aria-controls="purchases-panel"
          tabIndex={x.id === active ? 0 : -1}
          onClick={() => select(x.id)}
          onKeyDown={onKeyDown}
        >
          {t(x.label)}
        </button>
      ))}
    </div>
  )
}

export default function PurchasesPage() {
  const [params] = useSearchParams()
  const active = params.get('vista') === 'riepilogo' ? 'riepilogo' : 'liste'
  return (
    <>
      <div className="page-head">
        <h1>{t('purchases.title')}</h1>
      </div>
      <Tabs active={active} />
      <div
        id="purchases-panel"
        role="tabpanel"
        aria-labelledby={`tab-${active}`}
      >
        {active === 'riepilogo' ? <SummaryTab /> : <ListsTab />}
      </div>
    </>
  )
}

function ListsTab() {
  const { data, isPending, isError, refetch } = usePurchaseLists()
  return (
    <>
      <CreateForm />
      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <div className="error" role="alert">
          {t('error.generic')}{' '}
          <button className="quiet" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      )}
      {data && data.length === 0 && (
        <p className="empty">{t('purchases.empty')}</p>
      )}
      {data && data.length > 0 && (
        <ul className="edition-list">
          {data.map((l) => (
            <li key={l.id} className="edition-row purchase-row">
              <div className="grow">
                <Link className="name" to={`/purchases/${l.id}`}>
                  {t('purchases.listTitle', {
                    description: l.description,
                    month: monthLabel(l.month, l.year),
                  })}
                </Link>
                <div className="muted">
                  {t(
                    l.itemCount === 1
                      ? l.unbookedCount === 1
                        ? 'purchases.countOneOneUnbooked'
                        : 'purchases.countOne'
                      : l.unbookedCount === 1
                        ? 'purchases.countsOneUnbooked'
                        : 'purchases.counts',
                    {
                      count: l.itemCount,
                      unbooked: l.unbookedCount,
                    },
                  )}
                </div>
              </div>
              <span className={`chip${l.closed ? '' : ' active'}`}>
                {t(l.closed ? 'purchases.closed' : 'purchases.open')}
              </span>
              <span className="num">{formatChf(l.totalCents)}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
