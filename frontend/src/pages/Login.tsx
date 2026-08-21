import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'

export default function Login() {
  const { setUser } = useSession()
  const { language, setLanguage, t } = useI18n()
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setUser(await auth.login(login, password))
    } catch (e) {
      setError(
        e instanceof ApiError
          ? ({
              invalid_credentials: t('login.invalidCredentials'),
              account_disabled: t('login.disabled'),
              too_many_attempts: t('login.blocked'),
            }[e.code] ?? t('login.failed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>MangaShelf</h1>
        <p className="subtitle">{t('login.subtitle')}</p>

        <label className="auth-language">
          <span>{t('language.label')}</span>
          <select value={language}
                  onChange={(event) => setLanguage(event.target.value as 'it' | 'en')}>
            <option value="it">{t('language.it')}</option>
            <option value="en">{t('language.en')}</option>
          </select>
        </label>

        {error && <div className="error" role="alert">{error}</div>}

        <div className="field">
          <label htmlFor="login">{t('login.identity')}</label>
          <input
            id="login"
            autoComplete="username"
            maxLength={255}
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">{t('login.password')}</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            maxLength={200}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={busy}>
          {busy ? t('login.submitting') : t('login.submit')}
        </button>

        <p className="switch">
          {t('login.noAccount')} <Link to="/register">{t('login.register')}</Link>
        </p>
      </form>
    </div>
  )
}
