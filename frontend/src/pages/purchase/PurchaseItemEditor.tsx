import { useState } from 'react'
import {
  ApiError,
  purchases,
  type PurchaseItem,
  type PurchaseList,
} from '../../api/client'
import { formatCents, parseAmount } from '../../format'
import { useI18n } from '../../i18n'

/** Inline editor that keeps values in the columns where they are read. */
export default function PurchaseItemEditor({
  listId,
  item,
  onSaved,
  onCancel,
  onError,
}: {
  listId: number
  item: PurchaseItem
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
      onSaved(
        await purchases.updateItem(listId, item.id, {
          seriesId: item.seriesId,
          volumeNumber: Number(number),
          releaseDate: date || null,
          priceEurCents: parseAmount(eur),
          priceChfCents: parseAmount(chf),
        }),
      )
    } catch (error) {
      onError(
        error instanceof ApiError && error.code === 'item_already_on_list'
          ? t('purchase.itemDuplicate')
          : t('common.changeFailed'),
      )
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
        <input
          type="date"
          value={date}
          className="date-input"
          aria-label={t('purchase.releaseDate')}
          onChange={(event) => setDate(event.target.value)}
        />
      </td>
      <td className="num" data-label={t('common.volumeShort')}>
        <input
          type="number"
          min={0}
          max={999}
          value={number}
          aria-label={t('common.volume')}
          onChange={(event) => setNumber(event.target.value)}
        />
      </td>
      <td className="num" data-label="EUR">
        <input
          inputMode="decimal"
          value={eur}
          aria-label={t('purchase.priceEur')}
          onChange={(event) => setEur(event.target.value)}
        />
      </td>
      <td className="num" data-label="CHF">
        <input
          inputMode="decimal"
          value={chf}
          aria-label={t('purchase.priceChf')}
          onChange={(event) => setChf(event.target.value)}
        />
      </td>
      <td className="num actions-cell">
        <button
          className="link-button"
          aria-label={t('purchase.saveEdit')}
          title={t('common.save')}
          disabled={busy}
          onClick={save}
        >
          ✓
        </button>
        <button
          className="link-button"
          aria-label={t('purchase.cancelEdit')}
          title={t('common.cancel')}
          onClick={onCancel}
        >
          ×
        </button>
      </td>
    </tr>
  )
}
