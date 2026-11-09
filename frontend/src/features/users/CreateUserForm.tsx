import { useEffect, useState, type SyntheticEvent } from 'react'
import { errorText, fieldMessage } from '../../api/client'
import type { Role } from '../../api/types'
import { t } from '../../i18n/t'
import { useUserMutations } from './api'

export default function CreateUserForm() {
  const { create } = useUserMutations()
  const [username, setUsername] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [role, setRole] = useState<Role>('USER')

  const error = create.error
  const userMsg = fieldMessage(error, 'username')
  const passMsg = fieldMessage(error, 'temporaryPassword')
  const general = errorText(error, ['username', 'temporaryPassword'])

  // Focus the first invalid field once its error is rendered, so it is read out.
  const firstInvalid = userMsg ? 'username' : passMsg ? 'temporaryPassword' : ''
  useEffect(() => {
    if (firstInvalid) document.getElementById(firstInvalid)?.focus()
  }, [firstInvalid, error])

  async function submit(e: SyntheticEvent) {
    e.preventDefault()
    try {
      await create.mutateAsync({ username, temporaryPassword, role })
      setUsername('')
      setTemporaryPassword('')
      setRole('USER')
    } catch {
      // shown through create.error
    }
  }

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor="username">{t('auth.username')}</label>
        <input
          id="username"
          required
          autoComplete="off"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          aria-invalid={userMsg ? true : undefined}
          aria-describedby={userMsg ? 'username-error' : undefined}
        />
        {userMsg && (
          <p id="username-error" className="field-help">
            {userMsg}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="temporaryPassword">
          {t('users.temporaryPassword')}
        </label>
        <input
          id="temporaryPassword"
          type="password"
          required
          autoComplete="new-password"
          value={temporaryPassword}
          onChange={(e) => setTemporaryPassword(e.target.value)}
          aria-invalid={passMsg ? true : undefined}
          aria-describedby={
            passMsg ? 'temporaryPassword-error' : 'temporaryPassword-help'
          }
        />
        <p id="temporaryPassword-help" className="field-help muted">
          {t('users.temporaryPasswordHelp')}
        </p>
        {passMsg && (
          <p id="temporaryPassword-error" className="field-help">
            {passMsg}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="role">{t('users.role')}</label>
        <select
          id="role"
          value={role}
          onChange={(e) => setRole(e.target.value as Role)}
        >
          {(['USER', 'ADMIN'] as const).map((r) => (
            <option key={r} value={r}>
              {t(`role.${r}`)}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" disabled={create.isPending}>
        {t('users.create')}
      </button>
    </form>
  )
}
