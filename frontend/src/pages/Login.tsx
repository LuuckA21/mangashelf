import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'
import TwoFactorCode from '../components/TwoFactorCode'

export default function Login() {
  const { setUser } = useSession()
  const location = useLocation()
  const { language, setLanguage, t } = useI18n()
  const [emailEnabled, setEmailEnabled] = useState(false)
  useEffect(() => {
    auth
      .emailOptions()
      .then((options) => setEmailEnabled(options.enabled))
      .catch(() => {})
  }, [])
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(false)
  const [code, setCode] = useState('')

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (pending) {
        setUser(await auth.twoFactorLogin(code.trim()))
        setCode('')
      } else {
        const result = await auth.login(login, password)
        setPassword('')
        if ('twoFactorRequired' in result) setPending(true)
        else setUser(result)
      }
    } catch (e) {
      setCode('')
      if (
        e instanceof ApiError &&
        ['two_factor_challenge_expired', 'session_invalid'].includes(e.code)
      ) {
        setPending(false)
        setPassword('')
      }
      setError(
        e instanceof ApiError
          ? ({
              invalid_credentials: t('login.invalidCredentials'),
              account_disabled: t('login.disabled'),
              too_many_attempts: t('login.blocked'),
              two_factor_invalid: t('twoFactor.invalid'),
              two_factor_challenge_expired: t('twoFactor.expired'),
              session_invalid: t('twoFactor.expired'),
              two_factor_unavailable: t('twoFactor.unavailable'),
            }[e.code] ?? t('login.failed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(false)
    }
  }

  async function cancelVerification() {
    setBusy(true)
    try {
      await auth.cancelTwoFactorLogin()
      setPending(false)
      setCode('')
      setPassword('')
      setError(null)
    } catch {
      setError(t('common.serverUnavailable'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>MangaShelf</h1>
        <p className="subtitle">{t('login.subtitle')}</p>

        {(location.state as { passwordChanged?: boolean } | null)
          ?.passwordChanged && (
          <div className="success" role="status">
            {t('login.passwordChanged')}
          </div>
        )}

        {(location.state as { accountDeleted?: boolean } | null)
          ?.accountDeleted && (
          <div className="success" role="status">
            {t('deletion.completed')}
          </div>
        )}

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

        {pending ? (
          <>
            <p>{t('twoFactor.login')}</p>
            <TwoFactorCode id="login-code" value={code} onChange={setCode} />
          </>
        ) : (
          <>
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

            {emailEnabled && (
              <p className="auth-recovery">
                <Link to="/forgot-password">{t('email.forgot')}</Link>
              </p>
            )}
          </>
        )}

        <button type="submit" disabled={busy}>
          {busy ? t('login.submitting') : t('login.submit')}
        </button>
        {pending && (
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => void cancelVerification()}
          >
            {t('twoFactor.back')}
          </button>
        )}

        <p className="switch">
          {t('login.noAccount')}{' '}
          <Link to="/register">{t('login.register')}</Link>
        </p>
        {emailEnabled && (
          <div className="auth-confirmation">
            <span>{t('email.confirmationHelp')}</span>{' '}
            <Link to="/resend-verification">{t('email.resendShort')}</Link>
          </div>
        )}
      </form>
    </div>
  )
}
