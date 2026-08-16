import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  catalog, purchases, type Manga, type PurchaseList, type Series,
} from '../api/client'
import { formatCents, formatDate, formatPeriod, MONTHS, parseAmount } from '../format'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'

/** One purchase list: its lines, its discount and its totals. */
export default function PurchaseDetail() {
  const { id } = useParams()
  const listId = Number(id)
  const navigate = useNavigate()

  const [list, setList] = useState<PurchaseList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    purchases.get(listId)
      .then(setList)
      .catch(() => setError('Lista non trovata.'))
  }, [listId])

  if (!list) {
    return (
      <Layout>
        {error ? <div className="error">{error}</div> : <p className="muted">Carico…</p>}
      </Layout>
    )
  }

  // Rows arrive ordered by date; grouping here keeps the page reading like
  // the calendar it replaces, one block per release day.
  const byDate = new Map<string, typeof list.items>()
  for (const item of list.items) {
    const key = item.releaseDate ?? ''
    const rows = byDate.get(key) ?? []
    rows.push(item)
    byDate.set(key, rows)
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {formatPeriod(list.periodYear, list.periodMonth) || 'Acquisti'}
          {list.items.length > 0 && ` · ${list.reservedCount} di ${list.items.length} riservati`}
        </p>
        <h1>
          {list.name}
          {list.paidAt && <span className="paid-badge">pagata</span>}
        </h1>
        <div className="inline-actions" style={{ marginTop: 12 }}>
          <button
            className={list.paidAt ? 'quiet' : ''}
            onClick={async () => {
              try {
                setList(await purchases.setPaid(list.id, !list.paidAt))
              } catch {
                setError('Non sono riuscito a cambiare lo stato.')
              }
            }}
          >
            {list.paidAt ? 'Riapri la lista' : 'Segna come pagata'}
          </button>
          <button className="quiet" onClick={() => setEditing(!editing)}>
            {editing ? 'Chiudi' : 'Nome, periodo e sconto'}
          </button>
          <ConfirmDelete
            what={`la lista “${list.name}”`}
            onConfirm={async () => {
              await purchases.remove(list.id)
              navigate('/purchases')
            }}
          />
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {editing && (
        <ListSettings
          list={list}
          onSaved={(updated) => { setList(updated); setEditing(false) }}
          onError={setError}
        />
      )}

      {list.items.length === 0 ? (
        <div className="empty">Nessun volume in lista.</div>
      ) : (
        <table className="purchase-table">
          <thead>
            <tr>
              <th className="reserve-cell" title="Riservato in fumetteria">R</th>
              <th>Manga</th>
              <th>Edizione</th>
              <th className="num">Vol.</th>
              <th className="num">EUR</th>
              <th className="num">CHF</th>
              <th />
            </tr>
          </thead>
          {[...byDate.entries()].map(([date, rows]) => (
            <tbody key={date || 'senza-data'}>
              <tr className="date-row">
                <th colSpan={7}>{date ? formatDate(date) : 'Senza data'}</th>
              </tr>
              {rows.map((item) => (
                <tr key={item.id} className={item.reserved ? 'reserved' : ''}>
                  <td className="reserve-cell">
                    <button
                      className="reserve-toggle"
                      aria-pressed={item.reserved}
                      title={item.reserved
                        ? 'Riservato in fumetteria'
                        : 'Segna come riservato in fumetteria'}
                      onClick={async () => {
                        try {
                          setList(await purchases.setReserved(
                            list.id, item.id, !item.reserved))
                        } catch {
                          setError('Non sono riuscito a cambiare la prenotazione.')
                        }
                      }}
                    >
                      {item.reserved ? '✓' : ''}
                    </button>
                  </td>
                  <td>{item.mangaTitle}</td>
                  <td className="muted">{item.seriesName}</td>
                  <td className="num">{item.volumeNumber}</td>
                  <td className="num">{formatCents(item.priceEurCents)}</td>
                  <td className="num">{formatCents(item.priceChfCents)}</td>
                  <td className="num">
                    <button
                      className="link-button"
                      title="Togli dalla lista"
                      onClick={async () => {
                        try {
                          setList(await purchases.removeItem(list.id, item.id))
                        } catch {
                          setError('Non sono riuscito a togliere la riga.')
                        }
                      }}
                    >
                      ×
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          ))}
          <tfoot>
            <tr>
              <td colSpan={4}>Totale</td>
              <td className="num">{formatCents(list.totalEurCents)}</td>
              <td className="num">{formatCents(list.subtotalChfCents)}</td>
              <td />
            </tr>
            {list.discountAppliedCents > 0 && (
              <>
                <tr className="muted">
                  <td colSpan={4}>
                    Sconto{list.discountPercent ? ` ${Number(list.discountPercent)}%` : ''}
                  </td>
                  <td className="num" />
                  <td className="num">−{formatCents(list.discountAppliedCents)}</td>
                  <td />
                </tr>
                <tr className="grand-total">
                  <td colSpan={4}>Da pagare</td>
                  <td className="num" />
                  <td className="num">{formatCents(list.totalChfCents)}</td>
                  <td />
                </tr>
              </>
            )}
          </tfoot>
        </table>
      )}

      <AddItem
        listId={list.id}
        onAdded={setList}
        onError={setError}
      />
    </Layout>
  )
}

/** Name and discount. Percentage and amount are mutually exclusive. */
function ListSettings({ list, onSaved, onError }: {
  list: PurchaseList
  onSaved: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const [name, setName] = useState(list.name)
  const [year, setYear] = useState(list.periodYear ? String(list.periodYear) : '')
  const [month, setMonth] = useState(list.periodMonth ? String(list.periodMonth) : '')
  const [kind, setKind] = useState<'none' | 'percent' | 'amount'>(
    list.discountPercent != null ? 'percent'
      : list.discountCents != null ? 'amount' : 'none')
  const [percent, setPercent] = useState(
    list.discountPercent != null ? String(Number(list.discountPercent)) : '')
  const [amount, setAmount] = useState(formatCents(list.discountCents))
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      onSaved(await purchases.update(list.id, {
        name,
        // Year and month travel together: sending one alone would trip the
        // schema's constraint, so an incomplete period becomes no period.
        periodYear: year && month ? Number(year) : null,
        periodMonth: year && month ? Number(month) : null,
        discountPercent: kind === 'percent' && percent ? percent : null,
        discountCents: kind === 'amount' ? parseAmount(amount) : null,
      }))
    } catch {
      onError('Salvataggio non riuscito.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginBottom: 24 }}>
      <div className="field">
        <label htmlFor="listName">Nome</label>
        <input id="listName" value={name} required
               onChange={(e) => setName(e.target.value)} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="listMonth">Mese</label>
          <select id="listMonth" value={month} onChange={(e) => setMonth(e.target.value)}>
            <option value="">Nessuno</option>
            {MONTHS.map((label, index) => (
              <option key={label} value={index + 1}>{label}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="listYear">Anno</label>
          <input id="listYear" type="number" min={1900} max={2200} value={year}
                 placeholder={String(new Date().getFullYear())}
                 onChange={(e) => setYear(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="discountKind">Sconto</label>
          <select id="discountKind" value={kind}
                  onChange={(e) => setKind(e.target.value as typeof kind)}>
            <option value="none">Nessuno</option>
            <option value="percent">Percentuale</option>
            <option value="amount">Importo fisso</option>
          </select>
        </div>
        <div className="field">
          {kind === 'percent' && (
            <>
              <label htmlFor="discountPercent">Percentuale</label>
              <input id="discountPercent" type="number" min={0} max={100} step="0.5"
                     value={percent} onChange={(e) => setPercent(e.target.value)} />
            </>
          )}
          {kind === 'amount' && (
            <>
              <label htmlFor="discountAmount">Importo in CHF</label>
              <input id="discountAmount" inputMode="decimal" placeholder="5.00"
                     value={amount} onChange={(e) => setAmount(e.target.value)} />
            </>
          )}
        </div>
      </div>

      <p className="muted" style={{ fontSize: 13 }}>
        Lo sconto si applica al totale in franchi.
      </p>

      <button type="submit" disabled={busy}>{busy ? 'Salvo…' : 'Salva'}</button>
    </form>
  )
}

/** Adds a line: edition, volume number, date and the two prices. */
function AddItem({ listId, onAdded, onError }: {
  listId: number
  onAdded: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const [manga, setManga] = useState<Manga[]>([])
  const [series, setSeries] = useState<Series[]>([])
  const [mangaId, setMangaId] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [number, setNumber] = useState('')
  const [date, setDate] = useState('')
  const [eur, setEur] = useState('')
  const [chf, setChf] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { catalog.listManga().then(setManga).catch(() => undefined) }, [])

  useEffect(() => {
    setSeries([]); setSeriesId('')
    if (mangaId) catalog.listSeries(Number(mangaId)).then(setSeries).catch(() => undefined)
  }, [mangaId])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      onAdded(await purchases.addItem(listId, {
        seriesId: Number(seriesId),
        volumeNumber: Number(number),
        releaseDate: date || null,
        priceEurCents: parseAmount(eur),
        priceChfCents: parseAmount(chf),
      }))
      // Edition and date stay: a release day usually brings several volumes
      // of the same run, and retyping them for each line is the friction
      // that sends people back to the spreadsheet.
      setNumber('')
      setEur('')
      setChf('')
    } catch {
      onError('Non sono riuscito ad aggiungere la riga.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginTop: 32 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>Aggiungi un volume</p>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="pi-manga">Manga</label>
          <select id="pi-manga" value={mangaId} required
                  onChange={(e) => setMangaId(e.target.value)}>
            <option value="">Scegli…</option>
            {manga.map((m) => <option key={m.id} value={m.id}>{m.displayTitle}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="pi-series">Edizione</label>
          <select id="pi-series" value={seriesId} required disabled={series.length === 0}
                  onChange={(e) => setSeriesId(e.target.value)}>
            <option value="">Scegli…</option>
            {series.map((s) => (
              <option key={s.id} value={s.id}>{s.name} — {s.publisher}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="row" style={{ alignItems: 'flex-end' }}>
        <div style={{ width: 100 }}>
          <label htmlFor="pi-number">Volume</label>
          <input id="pi-number" type="number" min={0} max={999} value={number} required
                 onChange={(e) => setNumber(e.target.value)} />
        </div>
        <div style={{ width: 170 }}>
          <label htmlFor="pi-date">Uscita</label>
          <input id="pi-date" type="date" value={date}
                 onChange={(e) => setDate(e.target.value)} />
        </div>
        <div style={{ width: 110 }}>
          <label htmlFor="pi-eur">Prezzo EUR</label>
          <input id="pi-eur" inputMode="decimal" placeholder="6.90"
                 value={eur} onChange={(e) => setEur(e.target.value)} />
        </div>
        <div style={{ width: 110 }}>
          <label htmlFor="pi-chf">Prezzo CHF</label>
          <input id="pi-chf" inputMode="decimal" placeholder="8.30"
                 value={chf} onChange={(e) => setChf(e.target.value)} />
        </div>
        <button type="submit" disabled={busy || !seriesId}>
          {busy ? 'Aggiungo…' : 'Aggiungi'}
        </button>
      </div>
    </form>
  )
}
