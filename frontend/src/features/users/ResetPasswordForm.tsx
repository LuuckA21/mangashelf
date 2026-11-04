import { useEffect, useId, useState, type SyntheticEvent } from 'react'
import { ApiError, fieldMessage } from '../../api/client'
import { t } from '../../i18n/t'
import { useUserMutations } from './api'

/** A button that opens an inline temporary-password form for one user. */
export default function ResetPasswordForm({
  id,
  username,
}: {
  id: string
  username: string
}) {
  const { reset } = useUserMutations()
  const [open, setOpen] = useState(false)
  const [done, setDone] = useState(false)
  const [password, setPassword] = useState('')
  const inputId = useId()

  const error = reset.error
  const msg = fieldMessage(error, 'temporaryPassword')
  const general =
    error instanceof ApiError
      ? error.errors.length === 0
        ? error.detail
        : error.errors
            .filter((e) => e.field !== 'temporaryPassword')
            .map((e) => e.message)
            .join(' ')
      : error && t('error.generic')

  useEffect(() => {
    if (msg) document.getElementById(inputId)?.focus()
  }, [msg, error, inputId])

  async function submit(e: SyntheticEvent) {
    e.preventDefault()
    try {
      await reset.mutateAsync({ id, temporaryPassword: password })
      setPassword('')
      setOpen(false)
      setDone(true)
    } catch {
      // shown through reset.error
    }
  }

  if (!open)
    return (
      <>
        <button
          className="quiet"
          onClick={() => {
            setDone(false)
            reset.reset()
            setOpen(true)
          }}
        >
          {t('users.reset')}
        </button>
        {done && (
          <p className="success" role="status">
            {t('users.resetDone', { username })}
          </p>
        )}
      </>
    )
  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor={inputId}>{t('users.resetFor', { username })}</label>
        <input
          id={inputId}
          type="password"
          required
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={msg ? true : undefined}
          aria-describedby={msg ? `${inputId}-error` : undefined}
        />
        {msg && (
          <p id={`${inputId}-error`} className="field-help">
            {msg}
          </p>
        )}
      </div>
      <div className="inline-actions">
        <button type="submit" disabled={reset.isPending}>
          {t('users.resetSubmit')}
        </button>
        <button type="button" className="quiet" onClick={() => setOpen(false)}>
          {t('common.cancel')}
        </button>
      </div>
    </form>
  )
}
