import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, type EditionSummary } from '../api/client'
import Layout from '../components/Layout'

type Filter = 'all' | 'gaps' | 'ongoing' | 'done'

/**
 * Two different questions, kept apart on purpose.
 *
 * "Con buchi" is answered by the owned numbers alone: 1-45 and 47 says the
 * 46 is missing. Whether more volumes exist past the last one owned cannot
 * be known from the shelf, and is what the edition's own state is for.
 */
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'Tutte' },
  { key: 'gaps', label: 'Con buchi' },
  { key: 'ongoing', label: 'In corso' },
  { key: 'done', label: 'Complete' },
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
        if (filter === 'gaps' && e.missingNumbers.length === 0) return false
        if (filter === 'ongoing' && e.completed) return false
        // Complete means the run is over and nothing is missing from it.
        if (filter === 'done' && (!e.completed || e.missingNumbers.length > 0)) return false
        if (!needle) return true
        return e.mangaTitle.toLowerCase().includes(needle)
          || e.seriesName.toLowerCase().includes(needle)
          || e.publisher.toLowerCase().includes(needle)
      })
      .sort((a, b) => a.mangaTitle.localeCompare(b.mangaTitle, 'it'))
  }, [editions, query, filter])

  const ownedTotal = editions.reduce((sum, e) => sum + e.ownedCount, 0)
  const gapTotal = editions.reduce((sum, e) => sum + e.missingNumbers.length, 0)

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {ownedTotal} volumi · {editions.length} edizioni
          {gapTotal > 0 && ` · ${gapTotal} buchi`}
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
          {filter === 'gaps'
            ? 'Nessun buco: le collane che segui non hanno numeri mancanti.'
            : filter === 'ongoing'
              ? 'Nessuna edizione in corso.'
              : filter === 'done'
                ? 'Nessuna edizione conclusa e completa, per ora.'
                : `Nessun risultato per “${query}”.`}
        </div>
      ) : (
        <ul className="edition-list">
          {shown.map((e) => {
            const percent = e.progressTotal > 0
              ? Math.round((e.ownedCount / e.progressTotal) * 100)
              : 0
            return (
              <li key={e.seriesId}>
                <Link to={`/edition/${e.seriesId}`}>
                  <div className="name">{e.mangaTitle}</div>
                  <div className="muted" style={{ fontSize: 14 }}>
                    {e.seriesName} · {e.publisher} · {e.ownedCount}
                    {e.declaredTotal != null ? ` di ${e.progressTotal}` : ''} volumi
                    {e.completed ? ' · conclusa' : ' · in corso'}
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
