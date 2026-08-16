import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { catalog, type Manga } from '../api/client'
import { useSession } from '../api/session'
import AniListSearch from '../components/AniListSearch'
import Layout from '../components/Layout'
import MangaForm from '../components/MangaForm'

/** The catalogue: every work known to this instance, shared by all users. */
export default function Library() {
  const { user } = useSession()
  const isAdmin = user?.role === 'ADMIN'

  const [manga, setManga] = useState<Manga[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function load(q?: string) {
    setLoading(true)
    try {
      setManga(await catalog.listManga(q))
    } catch {
      setError('Non riesco a caricare il catalogo.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    load(query)
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">Catalogo condiviso</p>
        <h1>Opere</h1>
      </div>

      {error && <div className="error">{error}</div>}

      <form className="row" onSubmit={handleSearch} style={{ marginBottom: 24 }}>
        <input
          placeholder="Cerca per titolo"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <button type="submit" className="quiet">Cerca</button>
        {isAdmin && (
          <>
            <button type="button" onClick={() => setImporting(!importing)}>
              {importing ? 'Chiudi' : 'Importa da AniList'}
            </button>
            <button type="button" className="quiet" onClick={() => setAdding(!adding)}>
              {adding ? 'Annulla' : 'Inserisci a mano'}
            </button>
          </>
        )}
      </form>

      {importing && (
        <AniListSearch
          onImported={(created) => {
            setManga((current) => [created, ...current.filter((m) => m.id !== created.id)])
            setImporting(false)
          }}
        />
      )}

      {adding && (
        <MangaForm
          manga={null}
          onSaved={(created) => {
            setManga((current) => [created, ...current])
            setAdding(false)
          }}
          onCancel={() => setAdding(false)}
          onError={setError}
        />
      )}

      {loading ? (
        <p className="muted">Carico…</p>
      ) : manga.length === 0 ? (
        <div className="empty">
          {isAdmin
            ? 'Il catalogo è vuoto. Aggiungi la prima opera per iniziare.'
            : 'Il catalogo è vuoto. Chiedi a un amministratore di aggiungere le opere.'}
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
    </Layout>
  )
}
