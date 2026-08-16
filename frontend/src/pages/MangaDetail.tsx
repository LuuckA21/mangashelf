import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, catalog, type Manga, type Series } from '../api/client'
import { useSession } from '../api/session'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import MangaForm from '../components/MangaForm'

const DELETE_ERRORS: Record<string, string> = {
  manga_has_owned_volumes:
    'Non posso eliminare: qualcuno possiede volumi di quest’opera.',
  series_has_owned_volumes:
    'Non posso eliminare: qualcuno possiede volumi di questa edizione.',
  manga_in_purchase_list:
    'Non posso eliminare: quest’opera compare in una lista d’acquisto.',
  series_in_purchase_list:
    'Non posso eliminare: questa edizione compare in una lista d’acquisto.',
  admin_required: 'Serve un account amministratore.',
}

/** One work and the editions it has been published in. */
export default function MangaDetail() {
  const { user } = useSession()
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
      .catch(() => setError('Non riesco a caricare le edizioni.'))
  }, [mangaId])

  useEffect(() => {
    catalog.getManga(mangaId)
      .then(setManga)
      .catch(() => setError('Opera non trovata.'))
    loadSeries()
  }, [mangaId, loadSeries])

  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? (DELETE_ERRORS[e.code] ?? fallback) : fallback
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
      setError(describe(e, 'Non sono riuscito ad aggiungere l’edizione. Forse esiste già.'))
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow"><Link to="/">Catalogo</Link></p>
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
                  {editingManga ? 'Chiudi' : 'Modifica'}
                </button>
                <ConfirmDelete
                  what={`“${manga.displayTitle}” e tutte le sue edizioni`}
                  onConfirm={async () => {
                    try {
                      await catalog.deleteManga(mangaId)
                      navigate('/')
                    } catch (e) {
                      setError(describe(e, 'Eliminazione non riuscita.'))
                    }
                  }}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {editingManga && manga && (
        <MangaForm
          manga={manga}
          onSaved={setManga}
          onCancel={() => setEditingManga(false)}
          onError={setError}
        />
      )}

      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ fontSize: 20 }}>Edizioni</h2>
        {isAdmin && (
          <button onClick={() => setAdding(!adding)}>
            {adding ? 'Annulla' : 'Aggiungi edizione'}
          </button>
        )}
      </div>

      {adding && (
        <form className="panel" onSubmit={handleAddSeries} style={{ marginBottom: 24 }}>
          <div className="grid-2">
            <div className="field">
              <label htmlFor="publisher">Editore</label>
              <input id="publisher" placeholder="Star Comics" value={publisher} required
                     onChange={(e) => setPublisher(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="name">Edizione</label>
              <input id="name" placeholder="New Edition" value={name} required
                     onChange={(e) => setName(e.target.value)} />
            </div>
          </div>
          <button type="submit">Salva</button>
        </form>
      )}

      {series.length === 0 ? (
        <div className="empty">
          {isAdmin
            ? 'Nessuna edizione. Aggiungi Normale, New Edition, Gazzetta…'
            : 'Nessuna edizione catalogata per quest’opera.'}
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
                      {s.totalVolumes != null && ` · ${s.totalVolumes} volumi`}
                      {s.completed && ' · conclusa'}
                    </div>
                  </Link>
                  {isAdmin && (
                    <div className="inline-actions">
                      <button className="quiet" onClick={() => setEditingSeriesId(s.id)}>
                        Modifica
                      </button>
                      <ConfirmDelete
                        what={`l’edizione “${s.name}”`}
                        onConfirm={async () => {
                          try {
                            await catalog.deleteSeries(s.id)
                            loadSeries()
                          } catch (e) {
                            setError(describe(e, 'Eliminazione non riuscita.'))
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
        ? 'Controlla i campi: la lingua vuole due lettere minuscole, come “it”.'
        : 'Salvataggio non riuscito. Forse un’altra edizione ha già questo nome.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit}>
      <div className="grid-2">
        <div className="field">
          <label htmlFor={`pub-${series.id}`}>Editore</label>
          <input id={`pub-${series.id}`} value={publisher} required
                 onChange={(e) => setPublisher(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor={`name-${series.id}`}>Nome edizione</label>
          <input id={`name-${series.id}`} value={name} required
                 placeholder="Normale, New Edition, Gazzetta…"
                 onChange={(e) => setName(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor={`lang-${series.id}`}>Lingua</label>
          <select id={`lang-${series.id}`} value={language}
                  onChange={(e) => setLanguage(e.target.value)}>
            <option value="it">Italiano</option>
            <option value="ja">Giapponese</option>
            <option value="en">Inglese</option>
            <option value="fr">Francese</option>
            <option value="de">Tedesco</option>
            <option value="es">Spagnolo</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor={`tot-${series.id}`}>Volumi totali</label>
          <input id={`tot-${series.id}`} type="number" min={0} value={totalVolumes}
                 placeholder="vuoto se in corso"
                 onChange={(e) => setTotalVolumes(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor={`done-${series.id}`}>Stato</label>
        <select id={`done-${series.id}`} value={completed ? 'yes' : 'no'}
                onChange={(e) => setCompleted(e.target.value === 'yes')}>
          <option value="no">In corso</option>
          <option value="yes">Conclusa</option>
        </select>
      </div>

      <div className="inline-actions">
        <button type="submit" disabled={busy}>{busy ? 'Salvo…' : 'Salva'}</button>
        <button type="button" className="quiet" onClick={onCancel}>Annulla</button>
      </div>
    </form>
  )
}
