import { useMemo, useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { collection, type OwnedVolume } from '../api/client'
import Layout from '../components/Layout'

/** Everything on your shelf, grouped by edition and filterable. */
export default function MyCollection() {
  const [owned, setOwned] = useState<OwnedVolume[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  useEffect(() => {
    collection.listOwned()
      .then(setOwned)
      .catch(() => setError('Non riesco a caricare la collezione.'))
      .finally(() => setLoading(false))
  }, [])

  // Filtro e raggruppamento avvengono nel browser: listOwned() restituisce
  // già l'intera collezione in una richiesta, quindi una query per ogni
  // tasto premuto costerebbe latenza senza aggiungere nulla. Se un giorno
  // la collezione diventasse troppo grande per una singola risposta, allora
  // anche la ricerca dovrà spostarsi sul server.
  const groups = useMemo(() => {
    const needle = query.trim().toLowerCase()

    const matching = needle
      ? owned.filter((item) =>
          item.mangaTitle.toLowerCase().includes(needle) ||
          item.seriesName.toLowerCase().includes(needle) ||
          item.publisher.toLowerCase().includes(needle))
      : owned

    const byEdition = new Map<number, OwnedVolume[]>()
    for (const item of matching) {
      const list = byEdition.get(item.seriesId) ?? []
      list.push(item)
      byEdition.set(item.seriesId, list)
    }

    return [...byEdition.entries()]
      .map(([seriesId, items]) => ({
        seriesId,
        first: items[0],
        numbers: items.map((i) => i.number).sort((a, b) => a - b),
      }))
      .sort((a, b) => a.first.mangaTitle.localeCompare(b.first.mangaTitle, 'it'))
  }, [owned, query])

  const shown = groups.reduce((sum, g) => sum + g.numbers.length, 0)

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {query.trim()
            ? `${shown} di ${owned.length} volumi`
            : `${owned.length} volumi · ${groups.length} edizioni`}
        </p>
        <h1>La mia collezione</h1>
      </div>

      {error && <div className="error">{error}</div>}

      {owned.length > 0 && (
        <div className="row" style={{ marginBottom: 24 }}>
          <input
            placeholder="Filtra per opera, edizione o editore"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ flex: 1, minWidth: 200 }}
          />
          {query && (
            <button type="button" className="quiet" onClick={() => setQuery('')}>
              Pulisci
            </button>
          )}
        </div>
      )}

      {loading ? (
        <p className="muted">Carico…</p>
      ) : owned.length === 0 ? (
        <div className="empty">
          Non hai ancora segnato nessun volume. Apri un’edizione dal catalogo e
          clicca i volumi che possiedi.
        </div>
      ) : groups.length === 0 ? (
        <div className="empty">Nessun risultato per “{query}”.</div>
      ) : (
        <ul className="edition-list">
          {groups.map(({ seriesId, first, numbers }) => (
            <li key={seriesId}>
              <Link to={`/edizione/${seriesId}`}>
                <div className="name">{first.mangaTitle}</div>
                <div className="muted" style={{ fontSize: 14 }}>
                  {first.seriesName} · {first.publisher} · {numbers.length} volumi
                </div>
                <div style={{ fontFamily: 'var(--font-data)', fontSize: 13, marginTop: 6 }}>
                  {summarise(numbers)}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Layout>
  )
}

/**
 * Collassa una sequenza di numeri in intervalli: "1-12, 15, 18-20".
 *
 * Una collana lunga elencata numero per numero occupa tre righe illeggibili,
 * mentre gli intervalli mostrano a colpo d'occhio dove sono i buchi — che è
 * poi l'unica cosa che si cerca guardando la propria collezione.
 */
function summarise(numbers: number[]): string {
  if (numbers.length === 0) return ''

  const parts: string[] = []
  let start = numbers[0]
  let previous = numbers[0]

  for (const n of numbers.slice(1)) {
    if (n === previous + 1) {
      previous = n
      continue
    }
    parts.push(start === previous ? `${start}` : `${start}–${previous}`)
    start = n
    previous = n
  }
  parts.push(start === previous ? `${start}` : `${start}–${previous}`)

  return parts.join(', ')
}
