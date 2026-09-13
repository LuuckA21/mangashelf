import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, auth } from '../api/client'
import { useI18n } from '../i18n'
import { useSession } from '../api/session'
import TwoFactorCode from './TwoFactorCode'

export default function AccountDeletionRequest() {
  const { t } = useI18n()
  const { user } = useSession()
  const [code, setCode] = useState('')
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    auth
      .emailOptions()
      .then((options) => {
        if (active) setEnabled(options.enabled)
      })
      .catch(() => {
        if (active) setEnabled(false)
      })
    return () => {
      active = false
    }
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setSent(false)
    setError(null)
    try {
      if (user?.twoFactorEnabled)
        await auth.requestAccountDeletion(password, code.trim())
      else await auth.requestAccountDeletion(password)
      setPassword('')
      setSent(true)
    } catch (e) {
      setError(
        e instanceof ApiError
          ? ({
              current_password_invalid: t('settings.currentPasswordInvalid'),
              last_admin_required: t('deletion.lastAdmin'),
              deletion_cooldown: t('deletion.cooldown'),
              too_many_attempts: t('email.tooMany'),
              email_unavailable: t('email.unavailable'),
              session_invalid: t('deletion.sessionInvalid'),
              two_factor_invalid: t('twoFactor.invalid'),
              two_factor_unavailable: t('twoFactor.unavailable'),
            }[e.code] ?? t('email.failed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(false)
      setCode('')
    }
  }

  return (
    <section
      className="panel settings-panel settings-section account-danger"
      aria-labelledby="delete-account-title"
    >
      <h2 id="delete-account-title">{t('deletion.title')}</h2>
      <p className="muted field-help">{t('deletion.explanation')}</p>
      {enabled === null ? (
        <p role="status">{t('common.loading')}</p>
      ) : !enabled ? (
        <p role="status">{t('deletion.unavailable')}</p>
      ) : (
        <form onSubmit={submit}>
          <p className="muted field-help">{t('deletion.requestHelp')}</p>
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          {sent && (
            <div className="success" role="status">
              {t('deletion.sent')}
            </div>
          )}
          <div className="field">
            <label htmlFor="deletion-password">{t('deletion.password')}</label>
            <input
              id="deletion-password"
              type="password"
              autoComplete="current-password"
              maxLength={200}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {user?.twoFactorEnabled && (
            <TwoFactorCode
              id="deletion-factor"
              value={code}
              onChange={setCode}
            />
          )}
          <button type="submit" className="danger" disabled={busy}>
            {busy ? t('deletion.sending') : t('deletion.send')}
          </button>
        </form>
      )}
    </section>
  )
}
