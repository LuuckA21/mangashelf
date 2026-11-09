import { useState, type SyntheticEvent } from 'react'
import { errorText, fieldMessage } from '../../api/client'
import type { ListDetail, PurchaseSettings } from '../../api/types'
import { t } from '../../i18n/t'
import { useListChange } from './api'
import { amountText, formatChf, parseAmount } from './money'

function AmountEditor(props: {
  id: string
  label: string
  saveLabel: string
  initial: number
  field: 'discountCents' | 'pointsDiscountCents'
  listId: string
  onDone?: () => void
  onCancel?: () => void
}) {
  const change = useListChange(props.listId)
  const [text, setText] = useState(
    props.initial ? amountText(props.initial) : '',
  )
  const [bad, setBad] = useState(false)
  const error =
    (bad && t('purchases.badAmount')) || fieldMessage(change.error, props.field)
  const general = errorText(change.error, [props.field])

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    const cents = parseAmount(text === '' ? '0' : text)
    setBad(cents === null)
    if (cents === null) return
    try {
      await change.mutateAsync({
        method: 'PATCH',
        body: { [props.field]: cents },
      })
      props.onDone?.()
    } catch {
      // shown from change.error
    }
  }

  return (
    <form className="inline-actions" onSubmit={submit}>
      <div className="field">
        <label htmlFor={props.id}>{props.label}</label>
        <input
          id={props.id}
          inputMode="decimal"
          value={text}
          onChange={(e) => setText(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${props.id}-error` : undefined}
        />
        {error && (
          <p id={`${props.id}-error`} className="field-help">
            {error}
          </p>
        )}
        {general && (
          <p className="error" role="alert">
            {general}
          </p>
        )}
      </div>
      <button type="submit" disabled={change.isPending}>
        {props.saveLabel}
      </button>
      {props.onCancel && (
        <button type="button" className="quiet" onClick={props.onCancel}>
          {t('common.cancel')}
        </button>
      )}
    </form>
  )
}

export default function Totals({
  list,
  settings,
}: {
  list: ListDetail
  settings: PurchaseSettings
}) {
  const [editing, setEditing] = useState(false)
  const reset = useListChange(list.id)
  return (
    <section className="panel totals" aria-label={t('purchases.total')}>
      <dl>
        <div>
          <dt>{t('purchases.subtotal')}</dt>
          <dd className="num">{formatChf(list.subtotalCents)}</dd>
        </div>
        <div>
          <dt>{t('purchases.discount')}</dt>
          <dd className="num">{formatChf(list.discountCents)}</dd>
        </div>
        <div>
          <dt>{t('purchases.points')}</dt>
          <dd className="num">{formatChf(list.pointsDiscountCents)}</dd>
        </div>
        <div className="grand">
          <dt>{t('purchases.total')}</dt>
          <dd className="num">{formatChf(list.totalCents)}</dd>
        </div>
      </dl>
      {editing && (
        <AmountEditor
          id="discount-amount"
          label={t('purchases.discountAmount')}
          saveLabel={t('purchases.discountSave')}
          initial={list.discountCents}
          field="discountCents"
          listId={list.id}
          onDone={() => setEditing(false)}
          onCancel={() => setEditing(false)}
        />
      )}
      <div className="inline-actions">
        {!editing && (
          <button
            type="button"
            className="quiet"
            onClick={() => setEditing(true)}
          >
            {t('purchases.discountEdit')}
          </button>
        )}
        {!list.discountAuto && (
          <button
            type="button"
            className="quiet"
            disabled={reset.isPending}
            onClick={() =>
              reset.mutate({ method: 'PATCH', body: { discountAuto: true } })
            }
          >
            {t('purchases.discountBack', {
              percent: settings.discountPercent,
            })}
          </button>
        )}
      </div>
      <AmountEditor
        key={list.pointsDiscountCents}
        id="points-amount"
        label={t('purchases.points')}
        saveLabel={t('purchases.pointsSave')}
        initial={list.pointsDiscountCents}
        field="pointsDiscountCents"
        listId={list.id}
      />
    </section>
  )
}
