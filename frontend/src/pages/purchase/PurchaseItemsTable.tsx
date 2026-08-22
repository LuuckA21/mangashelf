import { useState } from 'react'
import { purchases, type PurchaseList } from '../../api/client'
import { formatCents, formatDate } from '../../format'
import { useI18n } from '../../i18n'
import PurchaseItemEditor from './PurchaseItemEditor'

export default function PurchaseItemsTable({
  list,
  closed,
  onUpdated,
  onError,
}: {
  list: PurchaseList
  closed: boolean
  onUpdated: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const { locale, t } = useI18n()
  const [editingItem, setEditingItem] = useState<number | null>(null)
  const [removingItem, setRemovingItem] = useState<number | null>(null)

  if (list.items.length === 0) {
    return <div className="empty">{t('purchase.empty')}</div>
  }

  const byDate = new Map<string, typeof list.items>()
  for (const item of list.items) {
    const key = item.releaseDate ?? ''
    const rows = byDate.get(key) ?? []
    rows.push(item)
    byDate.set(key, rows)
  }

  return (
    <table className="purchase-table">
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
          <th className="reserve-cell" title={t('purchase.reservedTitle')}>
            R
          </th>
          <th className="reserve-cell" title={t('purchase.purchasedTitle')}>
            A
          </th>
          <th>Manga</th>
          <th>{t('purchase.chooseEdition')}</th>
          <th className="num">{t('common.volumeShort')}</th>
          <th className="num">EUR</th>
          <th className="num">CHF</th>
          <th />
        </tr>
      </thead>
      {[...byDate.entries()].map(([date, rows]) => (
        <tbody key={date || 'no-date'}>
          <tr className="date-row">
            <th colSpan={8}>
              {date ? formatDate(date, locale) : t('purchase.noDate')}
            </th>
          </tr>
          {rows.map((item) =>
            editingItem === item.id ? (
              <PurchaseItemEditor
                key={item.id}
                listId={list.id}
                item={item}
                onSaved={(updated) => {
                  onUpdated(updated)
                  setEditingItem(null)
                }}
                onCancel={() => setEditingItem(null)}
                onError={onError}
              />
            ) : (
              <tr
                key={item.id}
                className={[
                  item.purchasedAt != null
                    ? 'bought'
                    : item.reserved
                      ? 'reserved'
                      : '',
                  removingItem === item.id ? 'removing' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
              >
                <td className="reserve-cell">
                  <button
                    className="reserve-toggle"
                    disabled={closed}
                    aria-pressed={item.reserved}
                    aria-label={`${item.mangaTitle}, volume ${item.volumeNumber}: ${
                      item.reserved
                        ? t('purchase.markUnreserved')
                        : t('purchase.markReservedShort')
                    }`}
                    title={
                      item.reserved
                        ? t('purchase.reservedTitle')
                        : t('purchase.markReserved')
                    }
                    onClick={async () => {
                      try {
                        onUpdated(
                          await purchases.setReserved(
                            list.id,
                            item.id,
                            !item.reserved,
                          ),
                        )
                      } catch {
                        onError(t('purchase.reservationFailed'))
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
                        : t('purchase.markPurchasedShort')
                    }`}
                    title={
                      item.purchasedAt != null
                        ? t('purchase.purchasedTitle')
                        : t('purchase.markPurchased')
                    }
                    onClick={async () => {
                      try {
                        onUpdated(
                          await purchases.setPurchased(
                            list.id,
                            item.id,
                            item.purchasedAt == null,
                          ),
                        )
                      } catch {
                        onError(t('purchase.changeStatusFailed'))
                      }
                    }}
                  >
                    {item.purchasedAt != null ? '✓' : ''}
                  </button>
                </td>
                <td>{item.mangaTitle}</td>
                <td className="muted">{item.seriesName}</td>
                <td className="num" data-label={t('common.volumeShort')}>
                  {item.volumeNumber}
                </td>
                <td className="num" data-label="EUR">
                  {formatCents(item.priceEurCents, locale)}
                </td>
                <td className="num" data-label="CHF">
                  {formatCents(item.priceChfCents, locale)}
                </td>
                <td className="num actions-cell">
                  {closed ? null : removingItem === item.id ? (
                    <>
                      <button
                        className="link-button danger-text"
                        aria-label={`${t('purchase.confirmDelete')} ${item.mangaTitle}, ${t('common.volume')} ${item.volumeNumber}`}
                        title={t('purchase.confirmDeleteTitle')}
                        onClick={async () => {
                          try {
                            onUpdated(
                              await purchases.removeItem(list.id, item.id),
                            )
                          } catch {
                            onError(t('purchase.removeRowFailed'))
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
                        onClick={() => {
                          setRemovingItem(null)
                          setEditingItem(item.id)
                        }}
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
            ),
          )}
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
                {t('purchases.discount')}
                {list.discountPercent
                  ? ` ${Number(list.discountPercent)}%`
                  : ''}
              </td>
              <td className="num" />
              <td className="num">
                −{formatCents(list.discountAppliedCents, locale)}
              </td>
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
  )
}
