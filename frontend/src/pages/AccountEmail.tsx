import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'

type Mode = 'forgot' | 'resend' | 'verify' | 'reset'

// Each route has its own state, including after client-side navigation.
export default function AccountEmail({ mode }: { mode: Mode }) {
  return <EmailForm key={mode} mode={mode} />
}

function EmailForm({ mode }: { mode: Mode }) {
  const { t, language, setLanguage } = useI18n()
  const { setUser } = useSession()
  const location = useLocation()
  const navigate = useNavigate()
  const [token] = useState(
    () => new URLSearchParams(location.hash.slice(1)).get('token') ?? '',
  )
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [available, setAvailable] = useState<boolean | null>(null)
  const needsToken = mode === 'verify' || mode === 'reset'
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token)

  useEffect(() => {
    if (needsToken) {
      // Fragments never reach nginx; remove the secret from the current history entry too.
      window.history.replaceState(
        window.history.state,
        '',
        window.location.pathname,
      )
    } else {
      auth
        .emailOptions()
        .then((options) => setAvailable(options.enabled))
        .catch(() => setAvailable(false))
    }
  }, [needsToken])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (mode === 'reset' && password !== confirm) {
      setError(t('email.passwordMismatch'))
      return
    }
    setBusy(true)
    try {
      if (mode === 'forgot') await auth.requestPasswordReset(email)
      if (mode === 'resend') await auth.resendVerification(email)
      if (mode === 'verify') await auth.verifyEmail(token)
      if (mode === 'reset') {
        await auth.resetPassword(token, password)
        setUser(null)
        setPassword('')
        setConfirm('')
        navigate('/login', { replace: true, state: { passwordChanged: true } })
      }
      setDone(true)
    } catch (e) {
      setError(
        e instanceof ApiError
          ? ({
              email_token_invalid: t('email.invalidToken'),
              too_many_attempts: t('email.tooMany'),
              email_unavailable: t('email.unavailable'),
              password_too_long: t('register.passwordTooLong'),
              password_unchanged: t('email.passwordUnchanged'),
              validation_failed: t('register.validation'),
            }[e.code] ?? t('email.failed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={submit}>
        <h1>{t(`email.${mode}`)}</h1>
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
        {done ? (
          <p className="success" role="status">
            {t(
              mode === 'verify'
                ? 'email.verified'
                : mode === 'reset'
                  ? 'login.passwordChanged'
                  : 'email.requested',
            )}
          </p>
        ) : (
          <>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            {needsToken && !validToken ? (
              <p className="error" role="alert">
                {t('email.invalidToken')}
              </p>
            ) : !needsToken && available === false ? (
              <p role="status">{t('email.unavailable')}</p>
            ) : (
              <>
                {mode === 'verify' && <p>{t('email.confirmHint')}</p>}
                {!needsToken && (
                  <div className="field">
                    <label htmlFor="email">{t('register.email')}</label>
                    <input
                      id="email"
                      type="email"
                      autoComplete="email"
                      maxLength={255}
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                    />
                  </div>
                )}
                {mode === 'reset' && (
                  <>
                    <div className="field">
                      <label htmlFor="new-password">
                        {t('register.password')}
                      </label>
                      <input
                        id="new-password"
                        type="password"
                        autoComplete="new-password"
                        minLength={10}
                        maxLength={72}
                        required
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label htmlFor="confirm-password">
                        {t('email.confirmPassword')}
                      </label>
                      <input
                        id="confirm-password"
                        type="password"
                        autoComplete="new-password"
                        minLength={10}
                        maxLength={72}
                        required
                        value={confirm}
                        onChange={(e) => setConfirm(e.target.value)}
                      />
                    </div>
                  </>
                )}
                <button
                  type="submit"
                  disabled={busy || (!needsToken && available !== true)}
                >
                  {busy
                    ? t('common.saving')
                    : t(needsToken ? `email.${mode}` : 'email.send')}
                </button>
              </>
            )}
          </>
        )}
        {needsToken && !done && (
          <p className="switch">
            <Link
              to={
                mode === 'verify' ? '/resend-verification' : '/forgot-password'
              }
            >
              {t('email.newLink')}
            </Link>
          </p>
        )}
        <p className="switch">
          <Link to="/login">{t('register.login')}</Link>
        </p>
      </form>
    </div>
  )
}
