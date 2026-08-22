import { useState, type FormEvent } from 'react'
import { purchases, type PurchaseList } from '../../api/client'
import { formatCents, monthNames, parseAmount } from '../../format'
import { useI18n } from '../../i18n'

/** Name, period and mutually-exclusive discount settings. */
export default function ListSettings({
  list,
  onSaved,
  onError,
}: {
  list: PurchaseList
  onSaved: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const { locale, t } = useI18n()
  const months = monthNames(locale)
  const [name, setName] = useState(list.name)
  const [year, setYear] = useState(
    list.periodYear ? String(list.periodYear) : '',
  )
  const [month, setMonth] = useState(
    list.periodMonth ? String(list.periodMonth) : '',
  )
  const [kind, setKind] = useState<'none' | 'percent' | 'amount'>(
    list.discountPercent != null
      ? 'percent'
      : list.discountCents != null
        ? 'amount'
        : 'none',
  )
  const [percent, setPercent] = useState(
    list.discountPercent != null ? String(Number(list.discountPercent)) : '',
  )
  const [amount, setAmount] = useState(formatCents(list.discountCents, locale))
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      onSaved(
        await purchases.update(list.id, {
          name,
          periodYear: year && month ? Number(year) : null,
          periodMonth: year && month ? Number(month) : null,
          discountPercent: kind === 'percent' && percent ? percent : null,
          discountCents: kind === 'amount' ? parseAmount(amount) : null,
        }),
      )
    } catch {
      onError(t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form
      className="panel"
      onSubmit={handleSubmit}
      style={{ marginBottom: 24 }}
    >
      <div className="field">
        <label htmlFor="listName">{t('purchases.name')}</label>
        <input
          id="listName"
          value={name}
          required
          onChange={(event) => setName(event.target.value)}
        />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="listMonth">{t('purchases.month')}</label>
          <select
            id="listMonth"
            value={month}
            onChange={(event) => setMonth(event.target.value)}
          >
            <option value="">{t('common.none')}</option>
            {months.map((label, index) => (
              <option key={label} value={index + 1}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="listYear">{t('purchases.year')}</label>
          <input
            id="listYear"
            type="number"
            min={1900}
            max={2200}
            value={year}
            placeholder={String(new Date().getFullYear())}
            onChange={(event) => setYear(event.target.value)}
          />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="discountKind">{t('purchases.discount')}</label>
          <select
            id="discountKind"
            value={kind}
            onChange={(event) => setKind(event.target.value as typeof kind)}
          >
            <option value="none">{t('purchase.noDiscount')}</option>
            <option value="percent">{t('purchase.discountPercent')}</option>
            <option value="amount">{t('purchase.discountAmount')}</option>
          </select>
        </div>
        <div className="field">
          {kind === 'percent' && (
            <>
              <label htmlFor="discountPercent">
                {t('purchase.discountPercent')}
              </label>
              <input
                id="discountPercent"
                type="number"
                min={0}
                max={100}
                step="0.5"
                value={percent}
                onChange={(event) => setPercent(event.target.value)}
              />
            </>
          )}
          {kind === 'amount' && (
            <>
              <label htmlFor="discountAmount">{t('purchase.amountChf')}</label>
              <input
                id="discountAmount"
                inputMode="decimal"
                placeholder="5.00"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
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
