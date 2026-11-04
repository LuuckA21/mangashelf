import { useState, type SyntheticEvent } from 'react'
import { ApiError } from '../../api/client'
import { t } from '../../i18n/t'

/** One title field; used to create a series and to rename it. */
export default function SeriesForm({
  initial = '',
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: string
  submitLabel: string
  onSubmit: (title: string) => Promise<unknown>
  onCancel?: () => void
}) {
  const [title, setTitle] = useState(initial)
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await onSubmit(title.trim())
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      {error !== undefined && (
        <p className="error" role="alert">
          {error instanceof ApiError ? error.detail : t('error.generic')}
        </p>
      )}
      <div className="field">
        <label htmlFor="series-title">{t('series.titleLabel')}</label>
        <input
          id="series-title"
          required
          maxLength={200}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </div>
      <div className="inline-actions">
        <button type="submit" disabled={busy}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="quiet" onClick={onCancel}>
            {t('common.cancel')}
          </button>
        )}
      </div>
    </form>
  )
}
