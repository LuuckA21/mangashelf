import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, type OwnedVolume } from '../api/client'
import Layout from '../components/Layout'

/** Everything on your shelf, grouped by edition. */
export default function MyCollection() {
  const [owned, setOwned] = useState<OwnedVolume[]>([])
  const [loading, setLoading] = useState(true)

  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    collection.listOwned()
      .then(setOwned)
      .catch(() => setError('Non riesco a caricare la collezione.'))
      .finally(() => setLoading(false))
  }, [])

  // Grouping happens here rather than server-side: the shelf of a personal
  // instance is small enough that one request beats a dedicated endpoint.
  const byEdition = new Map<number, OwnedVolume[]>()
  for (const item of owned) {
    const list = byEdition.get(item.seriesId) ?? []
    list.push(item)
    byEdition.set(item.seriesId, list)
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{owned.length} volumi</p>
        <h1>La mia collezione</h1>
      </div>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <p className="muted">Carico…</p>
      ) : owned.length === 0 ? (
        <div className="empty">
          Non hai ancora segnato nessun volume. Apri un’edizione dal catalogo e
          clicca i volumi che possiedi.
        </div>
      ) : (
        <ul className="edition-list">
          {[...byEdition.entries()].map(([seriesId, items]) => {
            const first = items[0]
            const numbers = items.map((i) => i.number).sort((a, b) => a - b)
            return (
              <li key={seriesId}>
                <Link to={`/edizione/${seriesId}`}>
                  <div className="name">{first.mangaTitle}</div>
                  <div className="muted" style={{ fontSize: 14 }}>
                    {first.seriesName} · {first.publisher}
                  </div>
                  <div style={{ fontFamily: 'var(--font-data)', fontSize: 13, marginTop: 6 }}>
                    {numbers.join(' · ')}
                  </div>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </Layout>
  )
}
