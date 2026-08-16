import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  catalog, purchases,
  type Manga, type PurchaseItem as PurchaseItemRow, type PurchaseList,
  type PurchaseSuggestion, type Series,
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
  const [editingItem, setEditingItem] = useState<number | null>(null)
  const [removingItem, setRemovingItem] = useState<number | null>(null)
  const [transfer, setTransfer] = useState<string | null>(null)

  useEffect(() => {
    purchases.get(listId)
      .then(setList)
      .catch(() => setError('Lista non trovata.'))
  }, [listId])

  if (!list) {
    return (
      <Layout>
        <p className="eyebrow"><Link to="/purchases">Acquisti</Link></p>
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
          <Link to="/purchases">Acquisti</Link>
          {formatPeriod(list.periodYear, list.periodMonth)
            && ` · ${formatPeriod(list.periodYear, list.periodMonth)}`}
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
          <button
            className="quiet"
            disabled={list.items.length === 0}
            onClick={async () => {
              setTransfer('Aggiungo…')
              try {
                const r = await purchases.toCollection(list.id)
                setTransfer([
                  r.added > 0 ? `${r.added} volumi aggiunti alla collezione` : null,
                  r.alreadyOwned > 0 ? `${r.alreadyOwned} già posseduti` : null,
                ].filter(Boolean).join(' · ') || 'Nessuna modifica.')
              } catch {
                setTransfer(null)
                setError('Non sono riuscito ad aggiungere i volumi.')
              }
            }}
          >
            Aggiungi alla collezione
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
      {transfer && <p className="muted" style={{ fontSize: 14 }}>{transfer}</p>}

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
          {/* Widths declared once, so a cell holding an input lines up with
              the same cell holding text: without this the columns size
              themselves to their content and shift when a row is opened. */}
          <colgroup>
            <col className="col-reserve" />
            <col />
            <col className="col-edition" />
            <col className="col-vol" />
            <col className="col-price" />
            <col className="col-price" />
            <col className="col-actions" />
          </colgroup>
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
                // Guarded against a null id as well as a mismatch: with
                // both null the loose comparison would open every new row.
                editingItem !== null && editingItem === item.id ? (
                  <ItemRow
                    key={item.id}
                    listId={list.id}
                    item={item}
                    onSaved={(updated) => { setList(updated); setEditingItem(null) }}
                    onCancel={() => setEditingItem(null)}
                    onError={setError}
                  />
                ) : (
                <tr
                  key={item.id}
                  className={[
                    item.reserved ? 'reserved' : '',
                    removingItem === item.id ? 'removing' : '',
                  ].filter(Boolean).join(' ')}
                >
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
                  <td className="num actions-cell">
                    {removingItem === item.id ? (
                      <>
                        {/* Confirm takes the first slot and cancel the
                            second, where the delete button just was: a
                            stray double click therefore cancels rather
                            than deleting. */}
                        <button
                          className="link-button danger-text"
                          title="Conferma l’eliminazione"
                          onClick={async () => {
                            try {
                              setList(await purchases.removeItem(list.id, item.id))
                            } catch {
                              setError('Non sono riuscito a togliere la riga.')
                            } finally {
                              setRemovingItem(null)
                            }
                          }}
                        >
                          ✓
                        </button>
                        <button
                          className="link-button"
                          title="Annulla"
                          onClick={() => setRemovingItem(null)}
                        >
                          ×
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          className="link-button"
                          title="Modifica la riga"
                          onClick={() => { setRemovingItem(null); setEditingItem(item.id) }}
                        >
                          ✎
                        </button>
                        <button
                          className="link-button"
                          title="Togli dalla lista"
                          onClick={() => setRemovingItem(item.id)}
                        >
                          ×
                        </button>
                      </>
                    )}
                  </td>
                </tr>
                )
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

      <Suggestions
        listId={list.id}
        itemCount={list.items.length}
        onAdded={setList}
        onError={setError}
      />

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

/**
 * One line in edit mode, kept inside the table.
 *
 * <p>Editing in place rather than in a panel below: the fields stay in the
 * columns they belong to, so a price is corrected where it was read, and
 * the surrounding rows remain visible for comparison.
 *
 * <p>The edition is not editable here — two selects would not fit a table
 * row, and a line filed under the wrong run is rare enough to be worth
 * deleting and retyping.
 */
function ItemRow({ listId, item, onSaved, onCancel, onError }: {
  listId: number
  item: PurchaseItemRow
  onSaved: (list: PurchaseList) => void
  onCancel: () => void
  onError: (message: string) => void
}) {
  const [number, setNumber] = useState(String(item.volumeNumber))
  const [date, setDate] = useState(item.releaseDate ?? '')
  const [eur, setEur] = useState(formatCents(item.priceEurCents))
  const [chf, setChf] = useState(formatCents(item.priceChfCents))
  const [busy, setBusy] = useState(false)

  async function save() {
    setBusy(true)
    try {
      onSaved(await purchases.updateItem(listId, item.id, {
        seriesId: item.seriesId,
        volumeNumber: Number(number),
        releaseDate: date || null,
        priceEurCents: parseAmount(eur),
        priceChfCents: parseAmount(chf),
      }))
    } catch {
      onError('Modifica non riuscita.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <tr className="editing-row">
      <td />
      <td>{item.mangaTitle}</td>
      <td className="muted">
        {item.seriesName}
        {/* The date sits under the edition rather than in a column of its
            own: in read mode it is the heading of the group the row belongs
            to, so changing it here moves the line to another day. */}
        <input type="date" value={date} className="date-input"
               onChange={(e) => setDate(e.target.value)} />
      </td>
      <td className="num">
        <input type="number" min={0} max={999} value={number}
               onChange={(e) => setNumber(e.target.value)} />
      </td>
      <td className="num">
        <input inputMode="decimal" value={eur} onChange={(e) => setEur(e.target.value)} />
      </td>
      <td className="num">
        <input inputMode="decimal" value={chf} onChange={(e) => setChf(e.target.value)} />
      </td>
      <td className="num actions-cell">
        <button className="link-button" title="Salva" disabled={busy} onClick={save}>✓</button>
        <button className="link-button" title="Annulla" onClick={onCancel}>×</button>
      </td>
    </tr>
  )
}

/** How many suggestions are shown before the panel has to be expanded. */
const VISIBLE = 5

/**
 * Next volumes of runs already bought, one click each.
 *
 * <p>Reloads after every addition because adding one changes the rest: the
 * volume just added stops being suggested, and the list totals move.
 */
function Suggestions({ listId, itemCount, onAdded, onError }: {
  listId: number
  itemCount: number
  onAdded: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const [rows, setRows] = useState<PurchaseSuggestion[]>([])
  const [query, setQuery] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [busy, setBusy] = useState<number | null>(null)

  useEffect(() => {
    purchases.suggestions(listId).then(setRows).catch(() => undefined)
  }, [listId, itemCount])

  if (rows.length === 0) return null

  const needle = query.trim().toLowerCase()
  const matching = needle
    ? rows.filter((row) =>
        row.mangaTitle.toLowerCase().includes(needle) ||
        row.seriesName.toLowerCase().includes(needle) ||
        row.publisher.toLowerCase().includes(needle))
    : rows

  // Collapsed to a few lines unless asked otherwise: with thirty runs in
  // progress this panel would push the list itself off the screen, and it
  // is an aid to adding rows, not the point of the page.
  const visible = expanded || needle ? matching : matching.slice(0, VISIBLE)
  const hidden = matching.length - visible.length

  return (
    <div className="panel" style={{ marginTop: 32 }}>
      <div className="row" style={{ marginBottom: 12 }}>
        <p className="eyebrow" style={{ margin: 0 }}>
          Continua una collana · {rows.length}
        </p>
        <span className="spacer" />
        <input
          placeholder="Filtra"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ width: 220 }}
        />
      </div>

      {matching.length === 0 ? (
        <p className="muted" style={{ fontSize: 14, marginBottom: 0 }}>
          Nessuna collana per “{query}”.
        </p>
      ) : (
        <>
          <ul className="suggestion-list">
            {visible.map((row) => (
              <li key={row.seriesId}>
                <div className="grow">
                  <span className="suggestion-title">{row.mangaTitle}</span>
                  <span className="muted"> · {row.seriesName} · volume {row.volumeNumber}</span>
                  <div className="muted" style={{ fontSize: 13 }}>
                    {[
                      row.priceChfCents != null ? `CHF ${formatCents(row.priceChfCents)}` : null,
                      row.priceEurCents != null ? `EUR ${formatCents(row.priceEurCents)}` : null,
                      `come in “${row.lastBoughtIn}”`,
                    ].filter(Boolean).join(' · ')}
                  </div>
                </div>
                <button
                  className="quiet"
                  disabled={busy !== null}
                  onClick={async () => {
                    setBusy(row.seriesId)
                    try {
                      onAdded(await purchases.addItem(listId, {
                        seriesId: row.seriesId,
                        volumeNumber: row.volumeNumber,
                        // Prices carry over, the date does not: a new volume
                        // comes out on a new day, and inheriting the old one
                        // would file it under the wrong week.
                        priceEurCents: row.priceEurCents,
                        priceChfCents: row.priceChfCents,
                      }))
                    } catch {
                      onError('Non sono riuscito ad aggiungere la riga.')
                    } finally {
                      setBusy(null)
                    }
                  }}
                >
                  {busy === row.seriesId ? 'Aggiungo…' : `Aggiungi vol. ${row.volumeNumber}`}
                </button>
              </li>
            ))}
          </ul>

          {hidden > 0 && (
            <button type="button" className="quiet" style={{ marginTop: 12 }}
                    onClick={() => setExpanded(true)}>
              Mostra le altre {hidden}
            </button>
          )}
          {expanded && !needle && (
            <button type="button" className="quiet" style={{ marginTop: 12 }}
                    onClick={() => setExpanded(false)}>
              Mostra meno
            </button>
          )}
        </>
      )}
    </div>
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
