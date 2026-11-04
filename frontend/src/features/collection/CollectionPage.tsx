import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import type {
  CollectionEntry,
  EditionStatus,
  EditionType,
} from '../../api/types'
import { t } from '../../i18n/t'
import { useCollection } from './api'
import { formatMissing } from './formatMissing'

const TYPES: EditionType[] = ['REGULAR', 'VARIANT', 'JAPANESE']
const STATUSES: EditionStatus[] = ['ONGOING', 'COMPLETED']

function Entry({ e }: { e: CollectionEntry }) {
  const missing = formatMissing(e.owned, e.volumeCount)
  return (
    <li className="edition-row">
      <div className="grow">
        <Link className="name" to={`/editions/${e.editionId}`}>
          {e.editionName}
        </Link>
        <div className="muted">
          {[e.publisher, t(`type.${e.type}`), t(`status.${e.status}`)]
            .filter(Boolean)
            .join(' · ')}
        </div>
        {missing && (
          <div className="missing-line">
            {t('shelf.missingSummary', { list: missing })}
          </div>
        )}
      </div>
      <div>
        <span className="muted">{t('collection.ownedOf')} </span>
        <span className="num">
          {e.owned.length}/{e.volumeCount}
        </span>
      </div>
    </li>
  )
}

export default function CollectionPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const type = TYPES.find((x) => x === params.get('type'))
  const status = STATUSES.find((x) => x === params.get('status'))
  const incomplete = params.get('incomplete') === 'true'
  const rawPage = Number(params.get('page'))
  const page = Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 1
  const [search, setSearch] = useState(q)
  const [syncedQ, setSyncedQ] = useState(q)
  // Back/forward changes the URL, and the box follows it.
  if (q !== syncedQ) {
    setSyncedQ(q)
    setSearch(q)
  }

  const { data, isPending, isError, refetch } = useCollection({
    q,
    type,
    status,
    incomplete,
    page,
  })

  // Any change except paging goes back to the first page.
  function set(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    if (!('page' in changes)) next.delete('page')
    for (const [k, v] of Object.entries(changes))
      if (v) next.set(k, v)
      else next.delete(k)
    setParams(next)
  }

  function submit(ev: FormEvent) {
    ev.preventDefault()
    set({ q: search.trim() || undefined })
  }

  const chip = (label: string, active: boolean, onClick: () => void) => (
    <button
      key={label}
      type="button"
      className={`chip${active ? ' active' : ''}`}
      aria-pressed={active}
      onClick={onClick}
    >
      {label}
    </button>
  )

  const groups: { id: string; title: string; entries: CollectionEntry[] }[] = []
  for (const e of data?.content ?? []) {
    const last = groups.at(-1)
    if (last?.id === e.seriesId) last.entries.push(e)
    else groups.push({ id: e.seriesId, title: e.seriesTitle, entries: [e] })
  }

  return (
    <>
      <div className="page-head">
        <h1>{t('collection.title')}</h1>
      </div>
      <form role="search" className="row" onSubmit={submit}>
        <div className="field">
          <label htmlFor="q">{t('collection.searchLabel')}</label>
          <input
            id="q"
            type="search"
            value={search}
            onChange={(ev) => setSearch(ev.target.value)}
          />
        </div>
        <button type="submit">{t('collection.search')}</button>
      </form>
      <div
        className="chips"
        role="group"
        aria-label={t('collection.filterType')}
      >
        {TYPES.map((x) =>
          chip(t(`type.${x}`), type === x, () =>
            set({ type: type === x ? undefined : x }),
          ),
        )}
      </div>
      <div
        className="chips"
        role="group"
        aria-label={t('collection.filterStatus')}
      >
        {STATUSES.map((x) =>
          chip(t(`status.${x}`), status === x, () =>
            set({ status: status === x ? undefined : x }),
          ),
        )}
        {chip(t('collection.incompleteOnly'), incomplete, () =>
          set({ incomplete: incomplete ? undefined : 'true' }),
        )}
      </div>

      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <div className="error" role="alert">
          {t('error.generic')}{' '}
          <button className="quiet" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      )}
      {data && groups.length === 0 && (
        <p className="empty">{t('collection.empty')}</p>
      )}
      {groups.map((g) => (
        <section key={g.id}>
          <h2>{g.title}</h2>
          <ul className="edition-list">
            {g.entries.map((e) => (
              <Entry key={e.editionId} e={e} />
            ))}
          </ul>
        </section>
      ))}
      {data && data.page.totalPages > 1 && (
        <nav className="inline-actions" aria-label={t('collection.pagination')}>
          <button
            className="quiet"
            disabled={page <= 1}
            onClick={() => set({ page: String(page - 1) })}
          >
            {t('collection.prev')}
          </button>
          <span>
            {t('collection.pageOf', { page, total: data.page.totalPages })}
          </span>
          <button
            className="quiet"
            disabled={page >= data.page.totalPages}
            onClick={() => set({ page: String(page + 1) })}
          >
            {t('collection.next')}
          </button>
        </nav>
      )}
    </>
  )
}
