import { useEffect, useId, useRef, useState, type SyntheticEvent } from 'react'
import { ApiError, fieldMessage } from '../../api/client'
import { t } from '../../i18n/t'
import { useListChange, usePurchaseLists } from './api'
import { monthLabel } from './monthLabel'

type Props = {
  listId: string
  itemIds: string[]
  onClose: () => void
}

const FOCUSABLE = 'select, input, button:not(:disabled)'

/** Modal panel: focus stays inside, Esc closes, focus returns to what opened it. */
export default function MoveDialog({ listId, itemIds, onClose }: Props) {
  const lists = usePurchaseLists().data
  const change = useListChange(listId)
  const [target, setTarget] = useState('')
  const [date, setDate] = useState('')
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  const targets = (lists ?? []).filter((l) => l.id !== listId)
  const chosen = target || targets[0]?.id || ''

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    panel.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus()
    function onKeyDown(ev: KeyboardEvent) {
      if (ev.key === 'Escape') onClose()
      else if (ev.key === 'Tab') {
        const all = [...panel.current!.querySelectorAll<HTMLElement>(FOCUSABLE)]
        const end = ev.shiftKey ? all[0] : all[all.length - 1]
        if (document.activeElement === end) {
          ev.preventDefault()
          ;(ev.shiftKey ? all[all.length - 1] : all[0]).focus()
        }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      // After the render that may remove the moved row: fall back to the page title.
      setTimeout(() =>
        (opener?.isConnected
          ? opener
          : document.querySelector<HTMLElement>('h1')
        )?.focus(),
      )
    }
    // onClose is a fresh closure each render; the listener only needs the latest at call time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    try {
      await change.mutateAsync({
        method: 'POST',
        path: '/items/move',
        body: {
          itemIds,
          targetListId: chosen,
          ...(date ? { releaseDate: date } : {}),
        },
      })
      onClose()
    } catch {
      // shown below from change.error
    }
  }

  const error = change.error
  const general =
    error &&
    (error instanceof ApiError
      ? error.errors.length === 0 && error.detail
      : t('error.generic'))

  return (
    <div className="modal-backdrop">
      <div
        ref={panel}
        className="panel modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <h2 id={titleId}>{t('purchases.moveTitle')}</h2>
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor={`${titleId}-t`}>{t('purchases.moveTarget')}</label>
            <select
              id={`${titleId}-t`}
              value={chosen}
              onChange={(e) => setTarget(e.target.value)}
              required
              aria-invalid={!!fieldMessage(error, 'targetListId')}
            >
              {targets.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.description} · {monthLabel(l.month, l.year)}
                </option>
              ))}
            </select>
            {lists && targets.length === 0 && (
              <p className="muted">{t('purchases.moveNoTargets')}</p>
            )}
            {fieldMessage(error, 'targetListId') && (
              <p className="error" role="alert">
                {fieldMessage(error, 'targetListId')}
              </p>
            )}
          </div>
          <div className="field">
            <label htmlFor={`${titleId}-d`}>{t('purchases.date')}</label>
            <input
              id={`${titleId}-d`}
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-describedby={`${titleId}-h`}
            />
            <p className="field-help" id={`${titleId}-h`}>
              {t('purchases.moveDateHint')}
            </p>
          </div>
          {general && (
            <p className="error" role="alert">
              {general}
            </p>
          )}
          <div className="inline-actions">
            <button type="submit" disabled={!chosen || change.isPending}>
              {change.isPending ? t('purchases.moving') : t('purchases.move')}
            </button>
            <button type="button" className="quiet" onClick={onClose}>
              {t('common.cancel')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
