import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ApiError, catalog, purchases,
  type Manga, type PurchaseItem as PurchaseItemRow, type PurchaseList,
  type PurchaseListSummary, type PurchaseSuggestion, type Series,
} from '../api/client'
import { formatCents, formatDate, formatPeriod, monthNames, parseAmount } from '../format'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import { useI18n } from '../i18n'

/** One purchase list: its lines, its discount and its totals. */
export default function PurchaseDetail() {
  const { locale, t } = useI18n()
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
      .catch(() => setError(t('purchase.notFound')))
  }, [listId])

  if (!list) {
    return (
      <Layout>
        <p className="eyebrow"><Link to="/purchases">{t('nav.purchases')}</Link></p>
        {error
          ? <div className="error" role="alert">{error}</div>
          : <p className="muted">{t('common.loading')}</p>}
      </Layout>
    )
  }

  // A settled list is a record, not a working document: the controls that
  // would change it are taken away rather than left to fail against the
  // server, so the page says what it allows.
  const closed = list.paidAt != null

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
          <Link to="/purchases">{t('nav.purchases')}</Link>
          {formatPeriod(list.periodYear, list.periodMonth, locale)
            && ` · ${formatPeriod(list.periodYear, list.periodMonth, locale)}`}
          {list.items.length > 0 && ` · ${list.reservedCount} ${t('purchases.reserved')}`}
          {list.items.length > 0 && ` · ${list.purchasedCount} ${t('collection.of')} ${list.items.length} ${t('purchases.purchased')}`}
        </p>
        <h1>
          {list.name}
          {list.paidAt && <span className="paid-badge">{t('purchases.paid')}</span>}
        </h1>
        <div className="inline-actions" style={{ marginTop: 12 }}>
          <button
            className={list.paidAt ? 'quiet' : ''}
            onClick={async () => {
              try {
                setList(await purchases.setPaid(list.id, !list.paidAt))
              } catch {
                setError(t('purchase.changeStatusFailed'))
              }
            }}
          >
            {list.paidAt ? t('purchase.reopen') : t('purchase.markPaid')}
          </button>
          <button
            className="quiet"
            disabled={list.purchasedCount === 0}
            title={list.purchasedCount === 0
              ? t('purchase.markFirstPurchased')
              : t('purchase.addToCollection')}
            onClick={async () => {
              setTransfer(t('purchase.adding'))
              try {
                const r = await purchases.toCollection(list.id)
                setTransfer([
                  r.added > 0 ? `${r.added} ${t('purchase.addedVolumes')}` : null,
                  r.alreadyOwned > 0 ? `${r.alreadyOwned} ${t('purchase.alreadyOwned')}` : null,
                  r.notPurchased > 0 ? `${r.notPurchased} ${t('purchase.unpurchasedIgnored')}` : null,
                ].filter(Boolean).join(' · ') || t('purchase.noChanges'))
              } catch {
                setTransfer(null)
                setError(t('purchase.addCollectionFailed'))
              }
            }}
          >
            {t('purchase.addToCollectionShort')}
          </button>
          {!closed && (
            <button className="quiet" onClick={() => setEditing(!editing)}>
              {editing ? t('common.close') : t('purchase.details')}
            </button>
          )}
          <ConfirmDelete
            what={`${t('purchase.deleteNamedList')} “${list.name}”`}
            disabled={closed}
            onConfirm={async () => {
              try {
                await purchases.remove(list.id)
                navigate('/purchases')
              } catch (e) {
                setError(e instanceof ApiError && e.code === 'list_is_paid'
                  ? t('purchase.reopenBeforeDelete')
                  : t('common.deleteFailed'))
              }
            }}
          />
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}
      {transfer && <p className="muted" style={{ fontSize: 14 }}>{transfer}</p>}

      {closed && (
        <p className="muted" style={{ fontSize: 14 }}>
          {t('purchase.closedHelp')}
        </p>
      )}

      {editing && !closed && (
        <ListSettings
          list={list}
          onSaved={(updated) => { setList(updated); setEditing(false) }}
          onError={setError}
        />
      )}

      {list.items.length === 0 ? (
        <div className="empty">{t('purchase.empty')}</div>
      ) : (
        <table className="purchase-table">
          {/* Widths declared once, so a cell holding an input lines up with
              the same cell holding text: without this the columns size
              themselves to their content and shift when a row is opened. */}
          <colgroup>
            <col className="col-reserve" />
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
              <th className="reserve-cell" title={t('purchase.reservedTitle')}>R</th>
              <th className="reserve-cell" title={t('purchase.purchasedTitle')}>A</th>
              <th>Manga</th>
              <th>{t('purchase.chooseEdition')}</th>
              <th className="num">{t('common.volumeShort')}</th>
              <th className="num">EUR</th>
              <th className="num">CHF</th>
              <th />
            </tr>
          </thead>
          {[...byDate.entries()].map(([date, rows]) => (
            <tbody key={date || 'senza-data'}>
              <tr className="date-row">
                <th colSpan={8}>{date ? formatDate(date, locale) : t('purchase.noDate')}</th>
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
                    item.purchasedAt != null ? 'bought' : item.reserved ? 'reserved' : '',
                    removingItem === item.id ? 'removing' : '',
                  ].filter(Boolean).join(' ')}
                >
                  <td className="reserve-cell">
                    <button
                      className="reserve-toggle"
                      disabled={closed}
                      aria-pressed={item.reserved}
                      aria-label={`${item.mangaTitle}, volume ${item.volumeNumber}: ${
                        item.reserved
                          ? t('purchase.markUnreserved') : t('purchase.markReservedShort')}`}
                      title={item.reserved
                        ? t('purchase.reservedTitle')
                        : t('purchase.markReserved')}
                      onClick={async () => {
                        try {
                          setList(await purchases.setReserved(
                            list.id, item.id, !item.reserved))
                        } catch {
                          setError(t('purchase.reservationFailed'))
                        }
                      }}
                    >
                      {item.reserved ? '✓' : ''}
                    </button>
                  </td>
                  <td className="reserve-cell">
                    <button
                      className="reserve-toggle bought"
                      disabled={closed}
                      aria-pressed={item.purchasedAt != null}
                      aria-label={`${item.mangaTitle}, volume ${item.volumeNumber}: ${
                        item.purchasedAt != null
                          ? t('purchase.markUnpurchased')
                          : t('purchase.markPurchasedShort')}`}
                      title={item.purchasedAt != null
                        ? t('purchase.purchasedTitle')
                        : t('purchase.markPurchased')}
                      onClick={async () => {
                        try {
                          setList(await purchases.setPurchased(
                            list.id, item.id, item.purchasedAt == null))
                        } catch {
                          setError(t('purchase.changeStatusFailed'))
                        }
                      }}
                    >
                      {item.purchasedAt != null ? '✓' : ''}
                    </button>
                  </td>
                  <td>{item.mangaTitle}</td>
                  <td className="muted">{item.seriesName}</td>
                  <td className="num" data-label={t('common.volumeShort')}>{item.volumeNumber}</td>
                  <td className="num" data-label="EUR">{formatCents(item.priceEurCents, locale)}</td>
                  <td className="num" data-label="CHF">{formatCents(item.priceChfCents, locale)}</td>
                  <td className="num actions-cell">
                    {closed ? null : removingItem === item.id ? (
                      <>
                        {/* Confirm takes the first slot and cancel the
                            second, where the delete button just was: a
                            stray double click therefore cancels rather
                            than deleting. */}
                        <button
                          className="link-button danger-text"
                          aria-label={`${t('purchase.confirmDelete')} ${item.mangaTitle}, ${t('common.volume')} ${item.volumeNumber}`}
                          title={t('purchase.confirmDeleteTitle')}
                          onClick={async () => {
                            try {
                              setList(await purchases.removeItem(list.id, item.id))
                            } catch {
                              setError(t('purchase.removeRowFailed'))
                            } finally {
                              setRemovingItem(null)
                            }
                          }}
                        >
                          ✓
                        </button>
                        <button
                          className="link-button"
                          aria-label={`${t('purchase.cancelDelete')} ${item.mangaTitle}, ${t('common.volume')} ${item.volumeNumber}`}
                          title={t('common.cancel')}
                          onClick={() => setRemovingItem(null)}
                        >
                          ×
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          className="link-button"
                          aria-label={`${t('common.edit')} ${item.mangaTitle}, ${t('common.volume')} ${item.volumeNumber}`}
                          title={t('purchase.editRow')}
                          onClick={() => { setRemovingItem(null); setEditingItem(item.id) }}
                        >
                          ✎
                        </button>
                        <button
                          className="link-button"
                          aria-label={`${t('purchase.removeFromList')} ${item.mangaTitle}, ${t('common.volume')} ${item.volumeNumber}`}
                          title={t('purchase.removeFromList')}
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
              <td colSpan={5}>{t('common.total')}</td>
              <td className="num">{formatCents(list.totalEurCents, locale)}</td>
              <td className="num">{formatCents(list.subtotalChfCents, locale)}</td>
              <td />
            </tr>
            {list.discountAppliedCents > 0 && (
              <>
                <tr className="muted">
                  <td colSpan={5}>
                    {t('purchases.discount')}{list.discountPercent ? ` ${Number(list.discountPercent)}%` : ''}
                  </td>
                  <td className="num" />
                  <td className="num">−{formatCents(list.discountAppliedCents, locale)}</td>
                  <td />
                </tr>
                <tr className="grand-total">
                  <td colSpan={5}>{t('purchase.payable')}</td>
                  <td className="num" />
                  <td className="num">{formatCents(list.totalChfCents, locale)}</td>
                  <td />
                </tr>
              </>
            )}
          </tfoot>
        </table>
      )}

      {!closed && <CarryOver
        list={list}
        onMoved={async (moved) => {
          setList(await purchases.get(list.id))
          setTransfer(moved === 0
            ? t('purchase.carryNone')
            : `${moved} ${t('purchase.carriedVolumes')}`)
        }}
        onError={setError}
      />}

      {!closed && <Suggestions
        listId={list.id}
        itemCount={list.items.length}
        onAdded={setList}
        onError={setError}
      />}

      {!closed && <AddItem
        listId={list.id}
        onAdded={setList}
        onError={setError}
      />}
    </Layout>
  )
}

/** Name and discount. Percentage and amount are mutually exclusive. */
function ListSettings({ list, onSaved, onError }: {
  list: PurchaseList
  onSaved: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const { locale, t } = useI18n()
  const months = monthNames(locale)
  const [name, setName] = useState(list.name)
  const [year, setYear] = useState(list.periodYear ? String(list.periodYear) : '')
  const [month, setMonth] = useState(list.periodMonth ? String(list.periodMonth) : '')
  const [kind, setKind] = useState<'none' | 'percent' | 'amount'>(
    list.discountPercent != null ? 'percent'
      : list.discountCents != null ? 'amount' : 'none')
  const [percent, setPercent] = useState(
    list.discountPercent != null ? String(Number(list.discountPercent)) : '')
  const [amount, setAmount] = useState(formatCents(list.discountCents, locale))
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
      onError(t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginBottom: 24 }}>
      <div className="field">
        <label htmlFor="listName">{t('purchases.name')}</label>
        <input id="listName" value={name} required
               onChange={(e) => setName(e.target.value)} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="listMonth">{t('purchases.month')}</label>
          <select id="listMonth" value={month} onChange={(e) => setMonth(e.target.value)}>
            <option value="">{t('common.none')}</option>
            {months.map((label, index) => (
              <option key={label} value={index + 1}>{label}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="listYear">{t('purchases.year')}</label>
          <input id="listYear" type="number" min={1900} max={2200} value={year}
                 placeholder={String(new Date().getFullYear())}
                 onChange={(e) => setYear(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="discountKind">{t('purchases.discount')}</label>
          <select id="discountKind" value={kind}
                  onChange={(e) => setKind(e.target.value as typeof kind)}>
            <option value="none">{t('purchase.noDiscount')}</option>
            <option value="percent">{t('purchase.discountPercent')}</option>
            <option value="amount">{t('purchase.discountAmount')}</option>
          </select>
        </div>
        <div className="field">
          {kind === 'percent' && (
            <>
              <label htmlFor="discountPercent">{t('purchase.discountPercent')}</label>
              <input id="discountPercent" type="number" min={0} max={100} step="0.5"
                     value={percent} onChange={(e) => setPercent(e.target.value)} />
            </>
          )}
          {kind === 'amount' && (
            <>
              <label htmlFor="discountAmount">{t('purchase.amountChf')}</label>
              <input id="discountAmount" inputMode="decimal" placeholder="5.00"
                     value={amount} onChange={(e) => setAmount(e.target.value)} />
            </>
          )}
        </div>
      </div>

      <p className="muted" style={{ fontSize: 13 }}>
        {t('purchase.discountHelp')}
      </p>

      <button type="submit" disabled={busy}>
        {busy ? t('common.saving') : t('common.save')}
      </button>
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
  const { locale, t } = useI18n()
  const [number, setNumber] = useState(String(item.volumeNumber))
  const [date, setDate] = useState(item.releaseDate ?? '')
  const [eur, setEur] = useState(formatCents(item.priceEurCents, locale))
  const [chf, setChf] = useState(formatCents(item.priceChfCents, locale))
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
    } catch (e) {
      onError(e instanceof ApiError && e.code === 'item_already_on_list'
        ? t('purchase.itemDuplicate')
        : t('common.changeFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <tr className="editing-row">
      <td />
      <td />
      <td>{item.mangaTitle}</td>
      <td className="muted">
        {item.seriesName}
        {/* The date sits under the edition rather than in a column of its
            own: in read mode it is the heading of the group the row belongs
            to, so changing it here moves the line to another day. */}
        <input type="date" value={date} className="date-input"
               aria-label={t('purchase.releaseDate')}
               onChange={(e) => setDate(e.target.value)} />
      </td>
      <td className="num" data-label={t('common.volumeShort')}>
        <input type="number" min={0} max={999} value={number}
               aria-label={t('common.volume')}
               onChange={(e) => setNumber(e.target.value)} />
      </td>
      <td className="num" data-label="EUR">
        <input inputMode="decimal" value={eur} aria-label={t('purchase.priceEur')}
               onChange={(e) => setEur(e.target.value)} />
      </td>
      <td className="num" data-label="CHF">
        <input inputMode="decimal" value={chf} aria-label={t('purchase.priceChf')}
               onChange={(e) => setChf(e.target.value)} />
      </td>
      <td className="num actions-cell">
        <button className="link-button" aria-label={t('purchase.saveEdit')} title={t('common.save')}
                disabled={busy} onClick={save}>✓</button>
        <button className="link-button" aria-label={t('purchase.cancelEdit')} title={t('common.cancel')}
                onClick={onCancel}>×</button>
      </td>
    </tr>
  )
}

/**
 * Pulls the unbought lines of an earlier list into this one.
 *
 * <p>They move rather than copy: what stays on the old list is then what
 * that month actually cost, which is the only reading of it worth keeping.
 */
function CarryOver({ list, onMoved, onError }: {
  list: PurchaseList
  onMoved: (moved: number) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [others, setOthers] = useState<PurchaseListSummary[]>([])
  const [sourceId, setSourceId] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    purchases.listAll()
      // Closed lists included, and in fact the usual case: the month gets
      // settled, then the next list is written and the leftovers follow.
      // Only lists with something left to move are offered.
      .then((all) => setOthers(all.filter((l) =>
        l.id !== list.id && l.itemCount > l.purchasedCount)))
      .catch(() => undefined)
  }, [list.id, list.items.length])

  if (others.length === 0) return null

  return (
    <div className="panel" style={{ marginTop: 32 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>{t('purchase.carryTitle')}</p>
      <div className="row">
        <select aria-label={t('purchase.sourceList')} value={sourceId}
                onChange={(e) => setSourceId(e.target.value)}
                style={{ flex: 1, minWidth: 200 }}>
          <option value="">{t('purchase.chooseList')}</option>
          {others.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name} — {l.itemCount - l.purchasedCount} {t('purchase.unpurchased')}
            </option>
          ))}
        </select>
        <button
          disabled={!sourceId || busy}
          onClick={async () => {
            setBusy(true)
            try {
              const { moved } = await purchases.carryOver(list.id, Number(sourceId))
              await onMoved(moved)
              setSourceId('')
            } catch {
              onError(t('purchase.carryFailed'))
            } finally {
              setBusy(false)
            }
          }}
        >
          {busy ? t('purchase.carrying') : t('purchase.carry')}
        </button>
      </div>
      <p className="muted" style={{ fontSize: 13, marginBottom: 0 }}>
        {t('purchase.carryHelp')}
      </p>
    </div>
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
  const { locale, t } = useI18n()
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
          {t('purchase.continueSeries')} · {rows.length}
        </p>
        <span className="spacer" />
        <input
          aria-label={t('purchase.filterSuggestions')}
          placeholder={t('purchase.filter')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="filter-input"
        />
      </div>

      {matching.length === 0 ? (
        <p className="muted" style={{ fontSize: 14, marginBottom: 0 }}>
          {t('purchase.noSeries')} “{query}”.
        </p>
      ) : (
        <>
          <ul className="suggestion-list">
            {visible.map((row) => (
              <li key={row.seriesId}>
                <div className="grow">
                  <span className="suggestion-title">{row.mangaTitle}</span>
                  <span className="muted"> · {row.seriesName} · {t('common.volume')} {row.volumeNumber}</span>
                  <div className="muted" style={{ fontSize: 13 }}>
                    {[
                      row.priceChfCents != null ? `CHF ${formatCents(row.priceChfCents, locale)}` : null,
                      row.priceEurCents != null ? `EUR ${formatCents(row.priceEurCents, locale)}` : null,
                      `${t('purchase.likeIn')} “${row.lastBoughtIn}”`,
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
                      onError(t('purchase.addFailed'))
                    } finally {
                      setBusy(null)
                    }
                  }}
                >
                  {busy === row.seriesId
                    ? t('purchase.adding')
                    : `${t('purchase.addVolumeShort')} ${row.volumeNumber}`}
                </button>
              </li>
            ))}
          </ul>

          {hidden > 0 && (
            <button type="button" className="quiet" style={{ marginTop: 12 }}
                    onClick={() => setExpanded(true)}>
              {t('purchase.showOthers')} {hidden}
            </button>
          )}
          {expanded && !needle && (
            <button type="button" className="quiet" style={{ marginTop: 12 }}
                    onClick={() => setExpanded(false)}>
              {t('purchase.showLess')}
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
  const { t } = useI18n()
  const [manga, setManga] = useState<Manga[]>([])
  const [mangaQuery, setMangaQuery] = useState('')
  const [activeMangaQuery, setActiveMangaQuery] = useState('')
  const [mangaPage, setMangaPage] = useState(0)
  const [mangaTotalPages, setMangaTotalPages] = useState(0)
  const [mangaTotalElements, setMangaTotalElements] = useState(0)
  const [mangaLoading, setMangaLoading] = useState(true)
  const [series, setSeries] = useState<Series[]>([])
  const [mangaId, setMangaId] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [number, setNumber] = useState('')
  const [date, setDate] = useState('')
  const [eur, setEur] = useState('')
  const [chf, setChf] = useState('')
  const [busy, setBusy] = useState(false)

  async function loadManga(query = '', targetPage = 0, append = false) {
    setMangaLoading(true)
    if (!append) {
      setManga([])
      setMangaPage(0)
      setMangaTotalPages(0)
      setMangaTotalElements(0)
    }
    try {
      const result = await catalog.listManga(query, targetPage)
      setManga((current) => append
        ? [...new Map([...current, ...result.content].map((item) => [item.id, item])).values()]
        : result.content)
      setActiveMangaQuery(query.trim())
      setMangaPage(result.number)
      setMangaTotalPages(result.totalPages)
      setMangaTotalElements(result.totalElements)
    } catch {
      onError(t('purchase.catalogLoadFailed'))
    } finally {
      setMangaLoading(false)
    }
  }

  useEffect(() => { void loadManga() }, [])

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
    } catch (e) {
      onError(e instanceof ApiError && e.code === 'item_already_on_list'
        ? t('purchase.itemDuplicate')
        : t('purchase.addFailed'))
    } finally {
      setBusy(false)
    }
  }

  function searchManga() {
    setMangaId('')
    void loadManga(mangaQuery)
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginTop: 32 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>{t('purchase.addVolume')}</p>

      <div className="field" style={{ marginBottom: 16 }}>
        <label htmlFor="pi-manga-search">{t('purchase.searchCatalog')}</label>
        <div className="row">
          <input
            id="pi-manga-search"
            placeholder={t('purchase.mangaTitle')}
            value={mangaQuery}
            onChange={(e) => setMangaQuery(e.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                searchManga()
              }
            }}
            style={{ flex: 1, minWidth: 200 }}
          />
          <button type="button" className="quiet" disabled={mangaLoading}
                  onClick={searchManga}>
            {t('common.search')}
          </button>
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="pi-manga">Manga</label>
          <select id="pi-manga" value={mangaId} required disabled={mangaLoading && manga.length === 0}
                  onChange={(e) => setMangaId(e.target.value)}>
            <option value="">{t('purchase.choose')}</option>
            {manga.map((m) => <option key={m.id} value={m.id}>{m.displayTitle}</option>)}
          </select>
          <div className="row" style={{ marginTop: 8 }}>
            <span className="muted" style={{ fontSize: 13 }}>
              {mangaLoading && manga.length === 0
                ? t('common.loading')
                : mangaTotalElements === 0
                  ? t('purchase.noManga')
                  : `${manga.length} ${t('collection.of')} ${mangaTotalElements}`}
            </span>
            {mangaPage + 1 < mangaTotalPages && (
              <button
                type="button"
                className="link-button"
                disabled={mangaLoading}
                onClick={() => void loadManga(activeMangaQuery, mangaPage + 1, true)}
              >
                {mangaLoading ? t('common.loading') : t('catalog.loadMore')}
              </button>
            )}
          </div>
        </div>
        <div className="field">
          <label htmlFor="pi-series">{t('purchase.chooseEdition')}</label>
          <select id="pi-series" value={seriesId} required disabled={series.length === 0}
                  onChange={(e) => setSeriesId(e.target.value)}>
            <option value="">{t('purchase.choose')}</option>
            {series.map((s) => (
              <option key={s.id} value={s.id}>{s.name} — {s.publisher}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="row" style={{ alignItems: 'flex-end' }}>
        <div className="field-narrow">
          <label htmlFor="pi-number">{t('common.volume')}</label>
          <input id="pi-number" type="number" min={0} max={999} value={number} required
                 onChange={(e) => setNumber(e.target.value)} />
        </div>
        <div className="field-date">
          <label htmlFor="pi-date">{t('purchase.release')}</label>
          <input id="pi-date" type="date" value={date}
                 onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field-medium">
          <label htmlFor="pi-eur">{t('purchase.priceEur')}</label>
          <input id="pi-eur" inputMode="decimal" placeholder="6.90"
                 value={eur} onChange={(e) => setEur(e.target.value)} />
        </div>
        <div className="field-medium">
          <label htmlFor="pi-chf">{t('purchase.priceChf')}</label>
          <input id="pi-chf" inputMode="decimal" placeholder="8.30"
                 value={chf} onChange={(e) => setChf(e.target.value)} />
        </div>
        <button type="submit" disabled={busy || !seriesId}>
          {busy ? t('purchase.adding') : t('purchase.addSuggested')}
        </button>
      </div>
    </form>
  )
}
