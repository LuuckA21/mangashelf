import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, catalog, type Manga, type Series } from '../api/client'
import { useSession } from '../api/session'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import MangaForm from '../components/MangaForm'
import { useI18n } from '../i18n'

/** One work and the editions it has been published in. */
export default function MangaDetail() {
  const { user } = useSession()
  const { t } = useI18n()
  const isAdmin = user?.role === 'ADMIN'

  const { id } = useParams()
  const mangaId = Number(id)
  const navigate = useNavigate()

  const [manga, setManga] = useState<Manga | null>(null)
  const [series, setSeries] = useState<Series[]>([])
  const [error, setError] = useState<string | null>(null)

  const [adding, setAdding] = useState(false)
  const [publisher, setPublisher] = useState('')
  const [name, setName] = useState('')

  const [editingManga, setEditingManga] = useState(false)
  const [editingSeriesId, setEditingSeriesId] = useState<number | null>(null)

  const loadSeries = useCallback(() => {
    catalog.listSeries(mangaId)
      .then(setSeries)
      .catch(() => setError(t('manga.loadEditionsFailed')))
  }, [mangaId, t])

  useEffect(() => {
    catalog.getManga(mangaId)
      .then(setManga)
      .catch(() => setError(t('manga.notFound')))
    loadSeries()
  }, [mangaId, loadSeries, t])

  function describe(e: unknown, fallback: string) {
    const messages: Record<string, string> = {
      manga_has_owned_volumes: t('manga.deleteOwned'),
      series_has_owned_volumes: t('manga.deleteSeriesOwned'),
      manga_in_purchase_list: t('manga.deletePurchase'),
      series_in_purchase_list: t('manga.deleteSeriesPurchase'),
      admin_required: t('manga.adminRequired'),
    }
    return e instanceof ApiError ? (messages[e.code] ?? fallback) : fallback
  }

  async function handleAddSeries(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const created = await catalog.createSeries(mangaId, {
        publisher, name, language: 'it', completed: false,
      })
      setSeries((current) => [...current, created])
      setPublisher('')
      setName('')
      setAdding(false)
    } catch (e) {
      setError(describe(e, t('manga.addEditionFailed')))
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow"><Link to="/">{t('nav.catalog')}</Link></p>
        <div className="row" style={{ alignItems: 'flex-start', gap: 20 }}>
          {manga?.coverUrl && <img src={manga.coverUrl} alt="" className="detail-cover" />}
          <div style={{ flex: 1, minWidth: 240 }}>
            <h1>{manga?.displayTitle ?? '…'}</h1>
            {manga?.authors && (
              <p className="muted" style={{ margin: '4px 0' }}>{manga.authors}</p>
            )}
            {manga?.genres && manga.genres.length > 0 && (
              <p className="eyebrow">{manga.genres.slice(0, 5).join(' · ')}</p>
            )}
            {isAdmin && manga && (
              <div className="inline-actions" style={{ marginTop: 12 }}>
                <button className="quiet" onClick={() => setEditingManga(!editingManga)}>
                  {editingManga ? t('common.close') : t('common.edit')}
                </button>
                <ConfirmDelete
                  what={`“${manga.displayTitle}” ${t('manga.deleteWorkWhat')}`}
                  onConfirm={async () => {
                    try {
                      await catalog.deleteManga(mangaId)
                      navigate('/')
                    } catch (e) {
                      setError(describe(e, t('common.deleteFailed')))
                    }
                  }}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {editingManga && manga && (
        <MangaForm
          manga={manga}
          onSaved={setManga}
          onCancel={() => setEditingManga(false)}
          onError={setError}
        />
      )}

      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ fontSize: 20 }}>{t('manga.editions')}</h2>
        {isAdmin && (
          <button onClick={() => setAdding(!adding)}>
            {adding ? t('common.cancel') : t('manga.addEdition')}
          </button>
        )}
      </div>

      {adding && (
        <form className="panel" onSubmit={handleAddSeries} style={{ marginBottom: 24 }}>
          <div className="grid-2">
            <div className="field">
              <label htmlFor="publisher">{t('manga.publisher')}</label>
              <input id="publisher" placeholder="Star Comics" value={publisher} required
                     onChange={(e) => setPublisher(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="name">{t('manga.editionName')}</label>
              <input id="name" placeholder="New Edition" value={name} required
                     onChange={(e) => setName(e.target.value)} />
            </div>
          </div>
          <button type="submit">{t('common.save')}</button>
        </form>
      )}

      {series.length === 0 ? (
        <div className="empty">
          {isAdmin
            ? t('manga.emptyEditionsAdmin')
            : t('manga.emptyEditionsUser')}
        </div>
      ) : (
        <ul className="edition-list">
          {series.map((s) => (
            <li key={s.id}>
              {editingSeriesId === s.id ? (
                <SeriesForm
                  series={s}
                  onSaved={() => { setEditingSeriesId(null); loadSeries() }}
                  onCancel={() => setEditingSeriesId(null)}
                  onError={(message) => setError(message)}
                />
              ) : (
                <div className="edition-row">
                  <Link to={`/edition/${s.id}`} className="grow">
                    <div className="name">{s.name}</div>
                    <div className="muted" style={{ fontSize: 14 }}>
                      {s.publisher}
                      {s.totalVolumes != null && ` · ${s.totalVolumes} ${t('common.volumes')}`}
                      {s.completed && ` · ${t('collection.completed')}`}
                    </div>
                  </Link>
                  {isAdmin && (
                    <div className="inline-actions">
                      <button className="quiet" onClick={() => setEditingSeriesId(s.id)}>
                        {t('common.edit')}
                      </button>
                      <ConfirmDelete
                        what={`${t('manga.deleteEditionWhat')} “${s.name}”`}
                        onConfirm={async () => {
                          try {
                            await catalog.deleteSeries(s.id)
                            loadSeries()
                          } catch (e) {
                            setError(describe(e, t('common.deleteFailed')))
                          }
                        }}
                      />
                    </div>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </Layout>
  )
}

function SeriesForm({ series, onSaved, onCancel, onError }: {
  series: Series
  onSaved: () => void
  onCancel: () => void
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [publisher, setPublisher] = useState(series.publisher)
  const [name, setName] = useState(series.name)
  const [language, setLanguage] = useState(series.language)
  const [totalVolumes, setTotalVolumes] = useState(
    series.totalVolumes === null ? '' : String(series.totalVolumes))
  const [completed, setCompleted] = useState(series.completed)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      await catalog.updateSeries(series.id, {
        publisher,
        name,
        language,
        totalVolumes: totalVolumes === '' ? null : Number(totalVolumes),
        completed,
      })
      onSaved()
    } catch (e) {
      onError(e instanceof ApiError && e.code === 'validation_failed'
        ? t('manga.seriesValidation')
        : t('manga.seriesSaveFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit}>
      <div className="grid-2">
        <div className="field">
          <label htmlFor={`pub-${series.id}`}>{t('manga.publisher')}</label>
          <input id={`pub-${series.id}`} value={publisher} required
                 onChange={(e) => setPublisher(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor={`name-${series.id}`}>{t('manga.editionName')}</label>
          <input id={`name-${series.id}`} value={name} required
                 placeholder="Normale, New Edition, Gazzetta…"
                 onChange={(e) => setName(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor={`lang-${series.id}`}>{t('manga.language')}</label>
          <select id={`lang-${series.id}`} value={language}
                  onChange={(e) => setLanguage(e.target.value)}>
            <option value="it">{t('manga.languageIt')}</option>
            <option value="ja">{t('manga.languageJa')}</option>
            <option value="en">{t('manga.languageEn')}</option>
            <option value="fr">{t('manga.languageFr')}</option>
            <option value="de">{t('manga.languageDe')}</option>
            <option value="es">{t('manga.languageEs')}</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor={`tot-${series.id}`}>{t('manga.totalVolumes')}</label>
          <input id={`tot-${series.id}`} type="number" min={0} max={999} value={totalVolumes}
                 placeholder={t('mangaForm.blankIfOngoing')}
                 onChange={(e) => setTotalVolumes(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor={`done-${series.id}`}>{t('mangaForm.status')}</label>
        <select id={`done-${series.id}`} value={completed ? 'yes' : 'no'}
                onChange={(e) => setCompleted(e.target.value === 'yes')}>
          <option value="no">{t('status.releasing')}</option>
          <option value="yes">{t('status.finished')}</option>
        </select>
      </div>

      <div className="inline-actions">
        <button type="submit" disabled={busy}>
          {busy ? t('common.saving') : t('common.save')}
        </button>
        <button type="button" className="quiet" onClick={onCancel}>
          {t('common.cancel')}
        </button>
      </div>
    </form>
  )
}
