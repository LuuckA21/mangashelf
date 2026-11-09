import { useEffect, useState, type SyntheticEvent } from 'react'
import { ApiError, errorText } from '../../api/client'
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

  const fieldErrors = error instanceof ApiError ? error.errors : []
  const titleError = fieldErrors.find((e) => e.field === 'title')?.message
  const general = errorText(error, ['title'])

  useEffect(() => {
    if (titleError) document.getElementById('series-title')?.focus()
  }, [titleError, error])

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
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
          aria-invalid={titleError ? true : undefined}
          aria-describedby={titleError ? 'series-title-error' : undefined}
        />
        {titleError && (
          <p id="series-title-error" className="field-help">
            {titleError}
          </p>
        )}
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
