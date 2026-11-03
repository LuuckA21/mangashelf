import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ApiError, api } from '../../api/client'
import type { MeResponse } from '../../api/types'
import { t } from '../../i18n/t'

type LoginState = { from?: string; passwordChanged?: boolean }

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const client = useQueryClient()
  const navigate = useNavigate()
  const state = (useLocation().state ?? {}) as LoginState

  const login = useMutation({
    mutationFn: () =>
      api<MeResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      }),
    onSuccess: (me) => {
      client.clear()
      client.setQueryData(['me'], me)
      navigate(
        me.mustChangePassword ? '/change-password' : (state.from ?? '/'),
        { replace: true },
      )
    },
  })

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    login.mutate()
  }

  const error =
    login.error instanceof ApiError
      ? login.error.detail
      : login.error && t('error.generic')

  return (
    <div className="auth-shell">
      <main className="auth-card">
        <h1>{t('auth.login.title')}</h1>
        {state.passwordChanged && (
          <p className="success" role="status">
            {t('auth.login.passwordChanged')}
          </p>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="username">{t('auth.username')}</label>
            <input
              id="username"
              autoComplete="username"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="password">{t('auth.password')}</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" disabled={login.isPending}>
            {t('auth.login.submit')}
          </button>
        </form>
      </main>
    </div>
  )
}
