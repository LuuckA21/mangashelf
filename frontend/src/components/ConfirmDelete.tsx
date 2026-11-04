import { useEffect, useId, useRef, useState } from 'react'
import { t } from '../i18n/t'

type Props = {
  /** What is about to go, named in the confirmation line. */
  what: string
  onConfirm: () => Promise<void>
  /** Text of the first button; defaults to "Elimina". */
  label?: string
}

/** A delete control that asks once, inline, naming exactly what will go. */
export default function ConfirmDelete({ what, onConfirm, label }: Props) {
  const [armed, setArmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const questionId = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const confirm = useRef<HTMLButtonElement>(null)
  const cancelled = useRef(false)

  // Focus follows the swap: into the question, and back to the trigger on cancel.
  useEffect(() => {
    if (armed) confirm.current?.focus()
    else if (cancelled.current) {
      cancelled.current = false
      trigger.current?.focus()
    }
  }, [armed])

  if (!armed)
    return (
      <button ref={trigger} className="danger" onClick={() => setArmed(true)}>
        {label ?? t('common.delete')}
      </button>
    )

  return (
    <span className="confirm" role="alertdialog" aria-labelledby={questionId}>
      <span className="confirm-text" id={questionId}>
        {t('delete.question')} {what}?
      </span>
      <button
        ref={confirm}
        onClick={async () => {
          setBusy(true)
          try {
            await onConfirm()
          } finally {
            setBusy(false)
            setArmed(false)
          }
        }}
        disabled={busy}
      >
        {busy ? t('common.deleting') : t('common.yesDelete')}
      </button>
      <button
        className="quiet"
        onClick={() => {
          cancelled.current = true
          setArmed(false)
        }}
        disabled={busy}
      >
        {t('common.cancel')}
      </button>
    </span>
  )
}
