import { useState } from 'react'
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

  if (!armed)
    return (
      <button className="danger" onClick={() => setArmed(true)}>
        {label ?? t('common.delete')}
      </button>
    )

  return (
    <span className="confirm">
      <span className="confirm-text">
        {t('delete.question')} {what}?
      </span>
      <button
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
      <button className="quiet" onClick={() => setArmed(false)} disabled={busy}>
        {t('common.cancel')}
      </button>
    </span>
  )
}
