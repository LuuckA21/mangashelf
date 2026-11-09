import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type SyntheticEvent } from 'react'
import { api, ApiError, errorText } from '../../api/client'
import { t } from '../../i18n/t'
import { endSession } from './endSession'

// The backend closes every session on success, so the user is gone for good:
// the caller decides where to send them.
export default function ChangePasswordForm({
  onSuccess,
}: {
  onSuccess: () => void
}) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const client = useQueryClient()

  const change = useMutation({
    mutationFn: () =>
      api('/auth/password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      }),
    onSuccess: () => {
      // Navigate first, so clearing the session cannot trigger a redirect that wins.
      onSuccess()
      endSession(client)
    },
  })

  const err = change.error
  const fieldErrors = err instanceof ApiError ? err.errors : []
  const message = (field: string) =>
    fieldErrors.find((e) => e.field === field)?.message
  const general = errorText(err, ['currentPassword', 'newPassword'])

  // Focus the first invalid field once its error is rendered, so it is read out.
  const firstInvalid = (['currentPassword', 'newPassword'] as const).find(
    message,
  )
  useEffect(() => {
    if (firstInvalid) document.getElementById(firstInvalid)?.focus()
  }, [firstInvalid, err])

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    change.mutate()
  }

  function field(id: 'currentPassword' | 'newPassword', label: string) {
    const msg = message(id)
    const value = id === 'currentPassword' ? currentPassword : newPassword
    const set = id === 'currentPassword' ? setCurrentPassword : setNewPassword
    return (
      <div className="field">
        <label htmlFor={id}>{label}</label>
        <input
          id={id}
          type="password"
          required
          autoComplete={
            id === 'currentPassword' ? 'current-password' : 'new-password'
          }
          value={value}
          onChange={(e) => set(e.target.value)}
          aria-invalid={msg ? true : undefined}
          aria-describedby={msg ? `${id}-error` : undefined}
        />
        {msg && (
          <p id={`${id}-error`} className="field-help">
            {msg}
          </p>
        )}
      </div>
    )
  }

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      {field('currentPassword', t('auth.currentPassword'))}
      {field('newPassword', t('auth.newPassword'))}
      <button type="submit" disabled={change.isPending}>
        {t('auth.changePassword.submit')}
      </button>
    </form>
  )
}
