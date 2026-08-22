import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'

export default function Register() {
  const { setUser } = useSession()
  const { language, setLanguage, t } = useI18n()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await auth.register(username, email, password, language)
      // Sign upon does not open a session, so sign in straight after.
      setUser(await auth.login(username, password))
    } catch (e) {
      setError(
        e instanceof ApiError
          ? ({
              username_taken: t('register.usernameTaken'),
              email_taken: t('register.emailTaken'),
              registration_closed: t('register.closed'),
              validation_failed: t('register.validation'),
              password_too_long: t('register.passwordTooLong'),
            }[e.code] ?? t('register.failed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>{t('register.title')}</h1>
        <p className="subtitle">{t('register.subtitle')}</p>

        <label className="auth-language">
          <span>{t('language.label')}</span>
          <select
            value={language}
            onChange={(event) => setLanguage(event.target.value as 'it' | 'en')}
          >
            <option value="it">{t('language.it')}</option>
            <option value="en">{t('language.en')}</option>
          </select>
        </label>

        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}

        <div className="field">
          <label htmlFor="username">{t('register.username')}</label>
          <input
            id="username"
            autoComplete="username"
            maxLength={32}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="email">{t('register.email')}</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            maxLength={255}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">{t('register.password')}</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={busy}>
          {busy ? t('register.submitting') : t('register.submit')}
        </button>

        <p className="switch">
          {t('register.hasAccount')}{' '}
          <Link to="/login">{t('register.login')}</Link>
        </p>
      </form>
    </div>
  )
}
