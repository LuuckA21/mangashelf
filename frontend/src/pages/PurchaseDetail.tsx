import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, purchases, type PurchaseList } from '../api/client'
import { formatCents, formatDate, formatPeriod } from '../format'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import { PurchaseAddItem } from '../components/purchase/PurchaseAddItem'
import {
  PurchaseItemEditor, PurchaseListSettings,
} from '../components/purchase/PurchaseEditors'
import {
  PurchaseCarryOver, PurchaseSuggestions,
} from '../components/purchase/PurchaseHelpers'
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
  }, [listId, t])

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
        <PurchaseListSettings
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
                  <PurchaseItemEditor
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

      {!closed && <PurchaseCarryOver
        list={list}
        onMoved={async (moved) => {
          setList(await purchases.get(list.id))
          setTransfer(moved === 0
            ? t('purchase.carryNone')
            : `${moved} ${t('purchase.carriedVolumes')}`)
        }}
        onError={setError}
      />}

      {!closed && <PurchaseSuggestions
        listId={list.id}
        itemCount={list.items.length}
        onAdded={setList}
        onError={setError}
      />}

      {!closed && <PurchaseAddItem
        listId={list.id}
        onAdded={setList}
        onError={setError}
      />}
    </Layout>
  )
}
