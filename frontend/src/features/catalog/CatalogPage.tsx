import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { t } from '../../i18n/t'
import { useCreateSeries, useSeriesList } from './api'
import SeriesForm from './SeriesForm'

export default function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const rawPage = Number(params.get('page'))
  const page = Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 1
  const [search, setSearch] = useState(q)
  const [syncedQ, setSyncedQ] = useState(q)
  // Back/forward changes the URL, and the box follows it.
  if (q !== syncedQ) {
    setSyncedQ(q)
    setSearch(q)
  }
  const navigate = useNavigate()
  const { data, isPending, isError, refetch } = useSeriesList(q, page)
  const create = useCreateSeries()

  function go(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    if (!('page' in changes)) next.delete('page')
    for (const [k, v] of Object.entries(changes))
      if (v) next.set(k, v)
      else next.delete(k)
    setParams(next)
  }

  function submit(ev: FormEvent) {
    ev.preventDefault()
    go({ q: search.trim() || undefined })
  }

  return (
    <>
      <div className="page-head">
        <h1>{t('catalog.title')}</h1>
      </div>
      <form role="search" className="search-form" onSubmit={submit}>
        <div className="field">
          <label htmlFor="q">{t('catalog.searchLabel')}</label>
          <input
            id="q"
            type="search"
            value={search}
            onChange={(ev) => setSearch(ev.target.value)}
          />
        </div>
        <button type="submit">{t('catalog.search')}</button>
      </form>

      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <div className="error" role="alert">
          {t('error.generic')}{' '}
          <button className="quiet" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      )}
      {data && data.content.length === 0 && (
        <p className="empty">{t('catalog.empty')}</p>
      )}
      {data && data.content.length > 0 && (
        <ul className="edition-list">
          {data.content.map((s) => (
            <li key={s.id} className="edition-row">
              <div className="grow">
                <Link className="name" to={`/series/${s.id}`}>
                  {s.title}
                </Link>
              </div>
              <span className="muted">
                {s.editionCount === 1
                  ? t('catalog.editionCountOne')
                  : t('catalog.editionCount', { n: s.editionCount })}
              </span>
            </li>
          ))}
        </ul>
      )}
      {data && data.page.totalPages > 1 && (
        <nav className="inline-actions" aria-label={t('collection.pagination')}>
          <button
            className="quiet"
            disabled={page <= 1}
            onClick={() => go({ page: String(page - 1) })}
          >
            {t('collection.prev')}
          </button>
          <span>
            {t('collection.pageOf', { page, total: data.page.totalPages })}
          </span>
          <button
            className="quiet"
            disabled={page >= data.page.totalPages}
            onClick={() => go({ page: String(page + 1) })}
          >
            {t('collection.next')}
          </button>
        </nav>
      )}

      <section className="panel settings-panel">
        <h2>{t('catalog.createTitle')}</h2>
        <SeriesForm
          submitLabel={t('series.create')}
          onSubmit={async (title) => {
            const created = await create.mutateAsync(title)
            navigate(`/series/${created.id}`)
          }}
        />
      </section>
    </>
  )
}
