import { useState, type SyntheticEvent } from 'react'
import { ApiError, fieldMessage } from '../../api/client'
import type { PurchaseSettings } from '../../api/types'
import { t } from '../../i18n/t'
import { usePurchaseSettings, useSavePurchaseSettings } from '../purchases/api'
import { parseAmount } from '../purchases/money'

function Form({ initial }: { initial: PurchaseSettings }) {
  const save = useSavePurchaseSettings()
  const [rate, setRate] = useState(initial.chfRate.toFixed(2))
  const [discount, setDiscount] = useState(String(initial.discountPercent))
  const [invalid, setInvalid] = useState(false)

  function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    const cents = parseAmount(rate)
    const percent = /^\d+$/.test(discount.trim()) ? Number(discount) : null
    setInvalid(cents === null || percent === null)
    if (cents === null || percent === null) return
    save.mutate({ chfRate: cents / 100, discountPercent: percent })
  }

  const rateError =
    fieldMessage(save.error, 'chfRate') ??
    (invalid ? t('error.generic') : undefined)
  const discountError = fieldMessage(save.error, 'discountPercent')
  const general =
    save.error &&
    (save.error instanceof ApiError
      ? save.error.errors.length === 0 && save.error.detail
      : t('error.generic'))

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor="chf-rate">{t('account.chfRate')}</label>
        <input
          id="chf-rate"
          inputMode="decimal"
          value={rate}
          onChange={(e) => setRate(e.target.value)}
          aria-invalid={rateError ? true : undefined}
          aria-describedby={rateError ? 'chf-rate-error' : undefined}
        />
        {rateError && (
          <p id="chf-rate-error" className="field-help">
            {rateError}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="discount-percent">{t('account.discountPercent')}</label>
        <input
          id="discount-percent"
          inputMode="numeric"
          value={discount}
          onChange={(e) => setDiscount(e.target.value)}
          aria-invalid={discountError ? true : undefined}
          aria-describedby={
            discountError ? 'discount-percent-error' : undefined
          }
        />
        {discountError && (
          <p id="discount-percent-error" className="field-help">
            {discountError}
          </p>
        )}
      </div>
      <div className="inline-actions">
        <button type="submit" disabled={save.isPending}>
          {t('common.save')}
        </button>
        {save.isSuccess && (
          <span role="status">{t('account.purchasesSaved')}</span>
        )}
      </div>
    </form>
  )
}

export default function PurchaseSettingsForm() {
  const { data, isError } = usePurchaseSettings()
  return (
    <section className="panel settings-panel settings-section">
      <h2>{t('account.purchasesTitle')}</h2>
      <p className="muted">{t('account.purchasesHelp')}</p>
      {isError && (
        <p className="error" role="alert">
          {t('error.generic')}
        </p>
      )}
      {data && <Form initial={data} />}
    </section>
  )
}
