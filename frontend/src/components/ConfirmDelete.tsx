import { useState } from 'react'

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
  const [armed, setArmed] = useState(false)
  const [busy, setBusy] = useState(false)

  if (!armed) {
    return (
      <button className="danger" onClick={() => setArmed(true)} disabled={disabled}>
        Elimina
      </button>
    )
  }

  return (
    <span className="confirm">
      <span className="confirm-text">Eliminare {what}?</span>
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
        {busy ? 'Elimino…' : 'Sì, elimina'}
      </button>
      <button className="quiet" onClick={() => setArmed(false)} disabled={busy}>
        Annulla
      </button>
    </span>
  )
}
