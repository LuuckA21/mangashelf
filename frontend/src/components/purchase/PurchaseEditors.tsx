import { useState, type FormEvent } from 'react'
import { ApiError, purchases, type PurchaseItem, type PurchaseList } from '../../api/client'
import { formatCents, monthNames, parseAmount } from '../../format'
import { useI18n } from '../../i18n'

/** Name, period and discount. Percentage and amount are mutually exclusive. */
export function PurchaseListSettings({ list, onSaved, onError }: {
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
               onChange={(event) => setName(event.target.value)} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="listMonth">{t('purchases.month')}</label>
          <select id="listMonth" value={month}
                  onChange={(event) => setMonth(event.target.value)}>
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
                 onChange={(event) => setYear(event.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="discountKind">{t('purchases.discount')}</label>
          <select id="discountKind" value={kind}
                  onChange={(event) => setKind(event.target.value as typeof kind)}>
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
                     value={percent} onChange={(event) => setPercent(event.target.value)} />
            </>
          )}
          {kind === 'amount' && (
            <>
              <label htmlFor="discountAmount">{t('purchase.amountChf')}</label>
              <input id="discountAmount" inputMode="decimal" placeholder="5.00"
                     value={amount} onChange={(event) => setAmount(event.target.value)} />
            </>
          )}
        </div>
      </div>

      <p className="muted" style={{ fontSize: 13 }}>{t('purchase.discountHelp')}</p>
      <button type="submit" disabled={busy}>
        {busy ? t('common.saving') : t('common.save')}
      </button>
    </form>
  )
}

/** One purchase line in edit mode, kept inside its table columns. */
export function PurchaseItemEditor({ listId, item, onSaved, onCancel, onError }: {
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
      onSaved(await purchases.updateItem(listId, item.id, {
        seriesId: item.seriesId,
        volumeNumber: Number(number),
        releaseDate: date || null,
        priceEurCents: parseAmount(eur),
        priceChfCents: parseAmount(chf),
      }))
    } catch (error) {
      onError(error instanceof ApiError && error.code === 'item_already_on_list'
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
        <input type="date" value={date} className="date-input"
               aria-label={t('purchase.releaseDate')}
               onChange={(event) => setDate(event.target.value)} />
      </td>
      <td className="num" data-label={t('common.volumeShort')}>
        <input type="number" min={0} max={999} value={number}
               aria-label={t('common.volume')}
               onChange={(event) => setNumber(event.target.value)} />
      </td>
      <td className="num" data-label="EUR">
        <input inputMode="decimal" value={eur} aria-label={t('purchase.priceEur')}
               onChange={(event) => setEur(event.target.value)} />
      </td>
      <td className="num" data-label="CHF">
        <input inputMode="decimal" value={chf} aria-label={t('purchase.priceChf')}
               onChange={(event) => setChf(event.target.value)} />
      </td>
      <td className="num actions-cell">
        <button className="link-button" aria-label={t('purchase.saveEdit')}
                title={t('common.save')} disabled={busy} onClick={save}>✓</button>
        <button className="link-button" aria-label={t('purchase.cancelEdit')}
                title={t('common.cancel')} onClick={onCancel}>×</button>
      </td>
    </tr>
  )
}
