import { useId, useState, type SyntheticEvent } from 'react'
import { ApiError, fieldMessage } from '../../api/client'
import type { ItemView, PurchaseSettings } from '../../api/types'
import { t } from '../../i18n/t'
import { fetchProposal, useListChange } from './api'
import EditionPicker, { type PickedEdition } from './EditionPicker'
import { parseAmount, proposeChf } from './money'

const amountText = (c: number) =>
  `${Math.floor(c / 100)}.${String(c % 100).padStart(2, '0')}`

type Props = {
  listId: string
  settings: PurchaseSettings
  /** Present = edit that row; absent = add. */
  item?: ItemView
  onDone: () => void
}

/** Add (with proposals) or edit a row. Booked is toggled on the row, not here. */
export default function ItemForm({ listId, settings, item, onDone }: Props) {
  const change = useListChange(listId)
  const uid = useId()
  const [edition, setEdition] = useState<PickedEdition | null>(null)
  const [volume, setVolume] = useState(item ? String(item.volume) : '')
  const [date, setDate] = useState(item?.releaseDate ?? '')
  const [eur, setEur] = useState(item ? amountText(item.priceEurCents) : '')
  const [chf, setChf] = useState(item ? amountText(item.priceChfCents) : '')
  // An edited row keeps its stored CHF until the user touches EUR; an untouched add form follows EUR.
  const [chfEdited, setChfEdited] = useState(false)
  const [bad, setBad] = useState(false)

  function changeEur(text: string) {
    setEur(text)
    const cents = parseAmount(text)
    if (!chfEdited && cents !== null)
      setChf(amountText(proposeChf(cents, settings.chfRate)))
  }

  async function pick(e: PickedEdition) {
    setEdition(e)
    setChfEdited(false)
    try {
      const p = await fetchProposal(listId, e.editionId)
      if (p.volume !== null) setVolume(String(p.volume))
      if (p.releaseDate) setDate(p.releaseDate)
      if (p.priceEurCents !== null) changeEur(amountText(p.priceEurCents))
    } catch {
      // proposals are only a convenience
    }
  }

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    const eurCents = parseAmount(eur)
    const chfCents = parseAmount(chf)
    if (eurCents === null || chfCents === null || (!item && !edition)) {
      setBad(true)
      return
    }
    setBad(false)
    const fields = {
      volume: Number(volume),
      priceEurCents: eurCents,
      priceChfCents: chfCents,
    }
    try {
      if (item)
        await change.mutateAsync({
          method: 'PATCH',
          path: `/items/${item.id}`,
          body: date
            ? { ...fields, releaseDate: date }
            : { ...fields, clearReleaseDate: true },
        })
      else
        await change.mutateAsync({
          method: 'POST',
          path: '/items',
          body: {
            ...fields,
            editionId: edition!.editionId,
            releaseDate: date || null,
            booked: false,
          },
        })
      onDone()
    } catch {
      // shown from change.error
    }
  }

  const err = change.error
  const general =
    err &&
    (err instanceof ApiError
      ? err.errors.length === 0 && err.detail
      : t('error.generic'))
  const fe = (f: string) => fieldMessage(err, f)
  const field = (name: string, label: string, error?: string | false) => {
    const id = `${uid}-${name}`
    return {
      id,
      'aria-invalid': error ? true : undefined,
      'aria-describedby': error ? `${id}-error` : undefined,
      label,
      error: error || undefined,
    }
  }
  const inputs = [
    field('volume', t('purchases.volume'), fe('volume')),
    field('date', t('purchases.date'), fe('releaseDate')),
    field(
      'item-eur',
      t('purchases.priceEur'),
      (bad && parseAmount(eur) === null && t('purchases.badAmount')) ||
        fe('priceEurCents'),
    ),
    field(
      'item-chf',
      t('purchases.priceChf'),
      (bad && parseAmount(chf) === null && t('purchases.badAmount')) ||
        fe('priceChfCents'),
    ),
  ]
  const values = [volume, date, eur, chf]
  const setters = [
    setVolume,
    setDate,
    changeEur,
    (v: string) => {
      setChf(v)
      setChfEdited(true)
    },
  ]

  return (
    <form className="item-form" onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      {!item &&
        (edition ? (
          <p>
            <strong>{edition.label}</strong>{' '}
            <button
              type="button"
              className="quiet"
              onClick={() => setEdition(null)}
            >
              {t('purchases.changeEdition')}
            </button>
          </p>
        ) : (
          <EditionPicker onPick={pick} />
        ))}
      {bad && !item && !edition && (
        <p className="field-help">{t('purchases.noEditionYet')}</p>
      )}
      <div className="item-fields">
        {inputs.map((f, i) => (
          <div className="field" key={f.id}>
            <label htmlFor={f.id}>{f.label}</label>
            <input
              id={f.id}
              type={i === 0 ? 'number' : i === 1 ? 'date' : 'text'}
              min={i === 0 ? 1 : undefined}
              inputMode={i > 1 ? 'decimal' : undefined}
              required={i === 0}
              value={values[i]}
              onChange={(e) => setters[i](e.target.value)}
              aria-invalid={f['aria-invalid']}
              aria-describedby={f['aria-describedby']}
            />
            {f.error && (
              <p id={`${f.id}-error`} className="field-help">
                {f.error}
              </p>
            )}
          </div>
        ))}
      </div>
      <div className="inline-actions">
        <button type="submit" disabled={change.isPending}>
          {t(item ? 'common.save' : 'purchases.add')}
        </button>
        {item && (
          <button type="button" className="quiet" onClick={onDone}>
            {t('common.cancel')}
          </button>
        )}
      </div>
    </form>
  )
}
