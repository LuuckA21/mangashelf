import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { purchases, type PurchaseListSummary } from '../api/client'
import { formatCents } from '../money'
import Layout from '../components/Layout'

/** Index of the purchase lists, newest first. */
export default function Purchases() {
  const [lists, setLists] = useState<PurchaseListSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    purchases.listAll()
      .then(setLists)
      .catch(() => setError('Non riesco a caricare le liste.'))
      .finally(() => setLoading(false))
  }, [])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    try {
      const created = await purchases.create({ name })
      setLists((current) => [
        { id: created.id, name: created.name, itemCount: 0, totalChfCents: 0 },
        ...current,
      ])
      setName('')
      setAdding(false)
    } catch {
      setError('Non sono riuscito a creare la lista.')
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{lists.length} liste</p>
        <h1>Acquisti</h1>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="row" style={{ marginBottom: 24 }}>
        <button onClick={() => setAdding(!adding)}>
          {adding ? 'Annulla' : 'Nuova lista'}
        </button>
      </div>

      {adding && (
        <form className="panel" onSubmit={handleCreate} style={{ marginBottom: 24 }}>
          <div className="field">
            <label htmlFor="listName">Nome</label>
            <input id="listName" value={name} required autoFocus
                   placeholder="Manga luglio 2026"
                   onChange={(e) => setName(e.target.value)} />
          </div>
          <button type="submit">Crea</button>
        </form>
      )}

      {loading ? (
        <p className="muted">Carico…</p>
      ) : lists.length === 0 ? (
        <div className="empty">
          Nessuna lista. Creane una per pianificare gli acquisti del mese.
        </div>
      ) : (
        <ul className="edition-list">
          {lists.map((list) => (
            <li key={list.id}>
              <Link to={`/purchases/${list.id}`}>
                <div className="name">{list.name}</div>
                <div className="muted" style={{ fontSize: 14 }}>
                  {list.itemCount} volumi · CHF {formatCents(list.totalChfCents)}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Layout>
  )
}
