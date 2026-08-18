import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  purchases, type PurchaseListSummary, type PurchaseStats,
} from '../api/client'
import { formatCents, formatPeriod } from '../format'
import Layout from '../components/Layout'

/** Index of the purchase lists, newest first. */
export default function Purchases() {
  const [lists, setLists] = useState<PurchaseListSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [adding, setAdding] = useState(false)
  const [stats, setStats] = useState<PurchaseStats | null>(null)

  useEffect(() => {
    purchases.listAll()
      .then(setLists)
      .catch(() => setError('Non riesco a caricare le liste.'))
      .finally(() => setLoading(false))
    purchases.stats().then(setStats).catch(() => undefined)
  }, [])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    try {
      // A new list defaults to the current month: that is what it is for,
      // and correcting it is one field away.
      const now = new Date()
      const created = await purchases.create({
        name,
        periodYear: now.getFullYear(),
        periodMonth: now.getMonth() + 1,
      })
      setLists((current) => [
        {
          id: created.id,
          name: created.name,
          periodYear: created.periodYear,
          periodMonth: created.periodMonth,
          paidAt: null,
          itemCount: 0,
          reservedCount: 0,
          purchasedCount: 0,
          totalChfCents: 0,
        },
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
                <div className="name">
                  {list.name}
                  {list.paidAt && <span className="paid-badge">pagata</span>}
                </div>
                <div className="muted" style={{ fontSize: 14 }}>
                  {[
                    formatPeriod(list.periodYear, list.periodMonth),
                    `${list.itemCount} volumi`,
                    list.reservedCount > 0 && !list.paidAt
                      ? `${list.reservedCount} riservati`
                      : null,
                    list.purchasedCount > 0 && list.purchasedCount < list.itemCount
                      ? `${list.purchasedCount} acquistati`
                      : null,
                    `CHF ${formatCents(list.totalChfCents)}`,
                  ].filter(Boolean).join(' · ')}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {stats && stats.years.length > 0 && <Stats stats={stats} />}
    </Layout>
  )
}

/**
 * What the lists have cost, by year.
 *
 * <p>Full and discounted side by side rather than one or the other: the gap
 * between them is what the shop's discount is worth over a year, which is
 * the figure a spreadsheet of monthly lists never shows.
 */
function Stats({ stats }: { stats: PurchaseStats }) {
  return (
    <section style={{ marginTop: 48 }}>
      <p className="eyebrow">Statistiche</p>

      <table className="purchase-table stats-table">
        <thead>
          <tr>
            <th>Anno</th>
            <th className="num">Liste</th>
            <th className="num">Volumi</th>
            <th className="num">Pieno</th>
            <th className="num">Sconto</th>
            <th className="num">Speso</th>
            <th className="num">Medio pieno</th>
            <th className="num">Medio scontato</th>
          </tr>
        </thead>
        <tbody>
          {stats.years.map((year) => (
            <tr key={year.year}>
              <td>{year.year}</td>
              <td className="num">{year.listCount}</td>
              <td className="num">{year.volumeCount}</td>
              <td className="num">{formatCents(year.fullChfCents)}</td>
              <td className="num">
                {year.discountChfCents > 0 ? `−${formatCents(year.discountChfCents)}` : ''}
              </td>
              <td className="num">{formatCents(year.netChfCents)}</td>
              <td className="num">{formatCents(year.averageFullChfCents)}</td>
              <td className="num">{formatCents(year.averageNetChfCents)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="grand-total">
            <td>Totale</td>
            <td className="num">{stats.listCount}</td>
            <td className="num">{stats.volumeCount}</td>
            <td className="num">{formatCents(stats.fullChfCents)}</td>
            <td className="num">
              {stats.discountChfCents > 0 ? `−${formatCents(stats.discountChfCents)}` : ''}
            </td>
            <td className="num">{formatCents(stats.netChfCents)}</td>
            <td className="num">{formatCents(stats.averageFullChfCents)}</td>
            <td className="num">{formatCents(stats.averageNetChfCents)}</td>
          </tr>
        </tfoot>
      </table>

      <p className="muted" style={{ fontSize: 13 }}>
        Importi in franchi. L'anno è quello del periodo della lista, o quello
        di creazione se non è stato indicato. Le medie considerano solo i
        volumi con un prezzo.
      </p>
    </section>
  )
}
