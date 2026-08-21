import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { catalog, type Manga } from '../api/client'
import { useSession } from '../api/session'
import AniListSearch from '../components/AniListSearch'
import Layout from '../components/Layout'
import MangaForm from '../components/MangaForm'
import { useI18n } from '../i18n'

/** The catalogue: every work known to this instance, shared by all users. */
export default function Library() {
  const { user } = useSession()
  const { t } = useI18n()
  const isAdmin = user?.role === 'ADMIN'

  const [manga, setManga] = useState<Manga[]>([])
  const [query, setQuery] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function load(q = '', targetPage = 0, append = false) {
    setLoading(true)
    setError(null)
    if (!append) setManga([])
    try {
      const result = await catalog.listManga(q, targetPage)
      setManga((current) => append
        ? [...new Map([...current, ...result.content].map((item) => [item.id, item])).values()]
        : result.content)
      setActiveQuery(q.trim())
      setPage(result.number)
      setTotalPages(result.totalPages)
    } catch {
      setError(t('catalog.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    void load(query)
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('catalog.eyebrow')}</p>
        <h1>{t('catalog.title')}</h1>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="catalog-controls">
        <form className="catalog-search" onSubmit={handleSearch}>
          <input
            aria-label={t('catalog.searchLabel')}
            placeholder={t('catalog.searchPlaceholder')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button type="submit" className="quiet">{t('common.search')}</button>
        </form>
        {isAdmin && (
          <div className="catalog-actions">
            <button type="button" onClick={() => setImporting(!importing)}>
              {importing ? t('common.close') : t('catalog.importAniList')}
            </button>
            <button type="button" className="quiet" onClick={() => setAdding(!adding)}>
              {adding ? t('common.cancel') : t('catalog.manual')}
            </button>
          </div>
        )}
      </div>

      {importing && (
        <AniListSearch
          onImported={() => {
            setImporting(false)
            void load(activeQuery)
          }}
        />
      )}

      {adding && (
        <MangaForm
          manga={null}
          onSaved={() => {
            setAdding(false)
            void load(activeQuery)
          }}
          onCancel={() => setAdding(false)}
          onError={setError}
        />
      )}

      {loading && manga.length === 0 ? (
        <p className="muted">{t('common.loading')}</p>
      ) : manga.length === 0 ? (
        <div className="empty">
          {activeQuery
            ? `${t('catalog.noResults')} “${activeQuery}”.`
            : isAdmin
              ? t('catalog.emptyAdmin')
              : t('catalog.emptyUser')}
        </div>
      ) : (
        <ul className="manga-list">
          {manga.map((m) => (
            <li key={m.id}>
              <Link to={`/manga/${m.id}`}>
                {m.coverUrl && <img src={m.coverUrl} alt="" className="cover" loading="lazy" />}
                <span className="title">{m.displayTitle}</span>
                <span className="meta">
                  {[m.authors, m.startYear].filter(Boolean).join(' · ')}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {manga.length > 0 && page + 1 < totalPages && (
        <div style={{ marginTop: 24, textAlign: 'center' }}>
          <button
            type="button"
            className="quiet"
            disabled={loading}
            onClick={() => void load(activeQuery, page + 1, true)}
          >
            {loading ? t('common.loading') : t('catalog.loadMore')}
          </button>
        </div>
      )}
    </Layout>
  )
}
