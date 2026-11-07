import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, fieldMessage } from '../../api/client'
import ConfirmDelete from '../../components/ConfirmDelete'
import { t } from '../../i18n/t'
import { endSession } from '../auth/endSession'
import { useDeleteAccount } from './api'

export default function DeleteAccount() {
  const [password, setPassword] = useState('')
  const del = useDeleteAccount()
  const client = useQueryClient()
  const navigate = useNavigate()

  const error = del.error
  const msg = fieldMessage(error, 'password')
  const general =
    error instanceof ApiError
      ? error.errors.length === 0
        ? error.detail
        : error.errors
            .filter((e) => e.field !== 'password')
            .map((e) => e.message)
            .join(' ')
      : error && t('error.generic')

  useEffect(() => {
    if (msg) document.getElementById('deletePassword')?.focus()
  }, [msg, error])

  return (
    <section className="panel settings-panel settings-section account-danger">
      <h2>{t('account.deleteTitle')}</h2>
      <p className="muted">{t('account.deleteHelp')}</p>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor="deletePassword">{t('account.deletePassword')}</label>
        <input
          id="deletePassword"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={msg ? true : undefined}
          aria-describedby={msg ? 'deletePassword-error' : undefined}
        />
        {msg && (
          <p id="deletePassword-error" className="field-help">
            {msg}
          </p>
        )}
      </div>
      <ConfirmDelete
        label={t('account.delete')}
        what={t('account.deleteWhat')}
        onConfirm={async () => {
          try {
            await del.mutateAsync(password)
          } catch {
            return // shown through del.error
          }
          // Navigate first, so clearing the session cannot trigger a redirect that wins.
          navigate('/login', { replace: true })
          endSession(client)
        }}
      />
    </section>
  )
}
