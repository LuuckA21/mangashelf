import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'

export default function DeleteAccount() {
  const location = useLocation()
  const navigate = useNavigate()
  const { setUser } = useSession()
  const { t, language, setLanguage } = useI18n()
  const [token] = useState(
    () => new URLSearchParams(location.hash.slice(1)).get('token') ?? '',
  )
  const [details, setDetails] = useState<{
    username: string
    email: string
  } | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const validToken = /^[A-Za-z0-9_-]{43}$/.test(token)

  useEffect(() => {
    // Keep the bearer token out of access logs and the current history entry.
    window.history.replaceState(
      window.history.state,
      '',
      window.location.pathname,
    )
    if (!validToken) return
    let active = true
    auth
      .accountDeletionDetails(token)
      .then((result) => {
        if (active) setDetails(result)
      })
      .catch((e: unknown) => {
        if (active)
          setErrorCode(e instanceof ApiError ? e.code : 'server_unavailable')
      })
    return () => {
      active = false
    }
  }, [token, validToken])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!confirmed || !details) return
    setBusy(true)
    setErrorCode(null)
    try {
      await auth.deleteAccount(token)
      setUser(null)
      navigate('/login', { replace: true, state: { accountDeleted: true } })
    } catch (e) {
      setErrorCode(e instanceof ApiError ? e.code : 'server_unavailable')
    } finally {
      setBusy(false)
    }
  }

  const message = !validToken
    ? t('deletion.invalidToken')
    : errorCode
      ? ({
          deletion_token_invalid: t('deletion.invalidToken'),
          deletion_wrong_account: t('deletion.wrongAccount'),
          last_admin_required: t('deletion.lastAdmin'),
          too_many_attempts: t('email.tooMany'),
          server_unavailable: t('common.serverUnavailable'),
        }[errorCode] ?? t('email.failed'))
      : null

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={submit}>
        <h1>{t('deletion.title')}</h1>
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
        {message && (
          <div className="error" role="alert">
            {message}
          </div>
        )}
        {validToken && !details && !errorCode && (
          <p role="status">{t('common.loading')}</p>
        )}
        {details && (
          <>
            <p className="deletion-identity">
              <strong>{details.username}</strong>
              <br />
              {details.email}
            </p>
            <p>{t('deletion.explanation')}</p>
            <label className="deletion-check">
              <input
                type="checkbox"
                required
                checked={confirmed}
                onChange={(event) => setConfirmed(event.target.checked)}
              />
              <span>{t('deletion.confirmation')}</span>
            </label>
            <button
              type="submit"
              className="danger"
              disabled={!confirmed || busy}
            >
              {busy ? t('deletion.deleting') : t('deletion.confirm')}
            </button>
          </>
        )}
        <p className="switch">
          <Link to="/settings">{t('deletion.back')}</Link>
        </p>
      </form>
    </div>
  )
}
