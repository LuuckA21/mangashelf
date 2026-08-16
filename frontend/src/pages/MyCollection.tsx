import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, type EditionSummary } from '../api/client'
import Layout from '../components/Layout'

type Filter = 'all' | 'incomplete' | 'complete'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'Tutte' },
  { key: 'incomplete', label: 'Da completare' },
  { key: 'complete', label: 'Complete' },
]

/** Your shelf, grouped by edition, searchable and filterable. */
export default function MyCollection() {
  const [editions, setEditions] = useState<EditionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('all')

  useEffect(() => {
    collection.summary()
      .then(setEditions)
      .catch(() => setError('Non riesco a caricare la collezione.'))
      .finally(() => setLoading(false))
  }, [])

  // Filtering happens in the browser: the summary endpoint already returns
  // the whole shelf in one request, so a query per keystroke would add
  // latency and nothing else. If the collection ever outgrows a single
  // response, this will have to move to the server.
  const shown = useMemo(() => {
    const needle = query.trim().toLowerCase()

    return editions
      .filter((e) => {
        if (filter === 'incomplete' && e.missingNumbers.length === 0) return false
        if (filter === 'complete' && e.missingNumbers.length > 0) return false
        if (!needle) return true
        return e.mangaTitle.toLowerCase().includes(needle)
          || e.seriesName.toLowerCase().includes(needle)
          || e.publisher.toLowerCase().includes(needle)
      })
      .sort((a, b) => a.mangaTitle.localeCompare(b.mangaTitle, 'it'))
  }, [editions, query, filter])

  const ownedTotal = editions.reduce((sum, e) => sum + e.ownedCount, 0)
  const missingTotal = editions.reduce((sum, e) => sum + e.missingNumbers.length, 0)

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {ownedTotal} volumi · {editions.length} edizioni
          {missingTotal > 0 && ` · ${missingTotal} da recuperare`}
        </p>
        <h1>La mia collezione</h1>
      </div>

      {error && <div className="error">{error}</div>}

      {editions.length > 0 && (
        <>
          <div className="row" style={{ marginBottom: 12 }}>
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

          <div className="chips" style={{ marginBottom: 24 }}>
            {FILTERS.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                className={`chip${filter === key ? ' active' : ''}`}
                aria-pressed={filter === key}
                onClick={() => setFilter(key)}
              >
                {label}
              </button>
            ))}
          </div>
        </>
      )}

      {loading ? (
        <p className="muted">Carico…</p>
      ) : editions.length === 0 ? (
        <div className="empty">
          Non hai ancora segnato nessun volume. Apri un’edizione dal
          catalogo e clicca i volumi che possiedi.
        </div>
      ) : shown.length === 0 ? (
        <div className="empty">
          {filter === 'incomplete'
            ? 'Nessuna edizione da completare: hai tutto quello che è catalogato.'
            : filter === 'complete'
              ? 'Nessuna edizione completa, per ora.'
              : `Nessun risultato per “${query}”.`}
        </div>
      ) : (
        <ul className="edition-list">
          {shown.map((e) => {
            const percent = e.totalVolumes > 0
              ? Math.round((e.ownedCount / e.totalVolumes) * 100)
              : 0
            return (
              <li key={e.seriesId}>
                <Link to={`/edition/${e.seriesId}`}>
                  <div className="name">{e.mangaTitle}</div>
                  <div className="muted" style={{ fontSize: 14 }}>
                    {e.seriesName} · {e.publisher} · {e.ownedCount} di {e.totalVolumes} volumi
                  </div>

                  <div className="progress" style={{ margin: '8px 0' }}>
                    <i style={{ width: `${percent}%` }} />
                  </div>

                  <div style={{ fontFamily: 'var(--font-data)', fontSize: 13 }}>
                    {summarise(e.ownedNumbers)}
                  </div>

                  {e.missingNumbers.length > 0 && (
                    <div className="missing-line">
                      Mancano: {summarise(e.missingNumbers)}
                    </div>
                  )}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </Layout>
  )
}

/**
 * Collapses a run of numbers into ranges: "1-12, 15, 18-20".
 *
 * A long series listed number by number fills three unreadable lines, while
 * ranges show at a glance where the gaps are — which is the only thing one
 * looks for when scanning their own collection.
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
