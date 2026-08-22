import { useState } from 'react'
import { useI18n } from '../i18n'

interface Props {
  /** What is about to be deleted, named in the confirmation line. */
  what: string
  onConfirm: () => Promise<void>
  disabled?: boolean
}

/**
 * A delete control that asks once before acting.
 *
 * Deletion cascades through the catalogue, so the second click is not
 * ceremony: it is the only thing standing between a misclick and an
 * edition's whole run disappearing. The confirmation is inline rather than
 * a native dialog so it names exactly what will go.
 */
export default function ConfirmDelete({ what, onConfirm, disabled }: Props) {
  const { t } = useI18n()
  const [armed, setArmed] = useState(false)
  const [busy, setBusy] = useState(false)

  if (!armed) {
    return (
      <button
        className="danger"
        onClick={() => setArmed(true)}
        disabled={disabled}
      >
        {t('common.delete')}
      </button>
    )
  }

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
