import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import Layout from '../components/Layout'
import { useSession } from '../api/session'
import { ApiError, auth } from '../api/client'
import { useI18n, type Language } from '../i18n'

export default function Settings() {
  const { user, setUser, updateLanguage } = useSession()
  const { t } = useI18n()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [failed, setFailed] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  async function handleLanguage(language: Language) {
    setBusy(true)
    setSaved(false)
    setFailed(false)
    try {
      await updateLanguage(language)
      setSaved(true)
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  async function handlePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordError(null)
    if (newPassword !== confirmPassword) {
      setPasswordError(t('settings.passwordMismatch'))
      return
    }

    setPasswordBusy(true)
    try {
      await auth.updatePassword(currentPassword, newPassword)
      setUser(null)
      navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (error) {
      setPasswordError(
        error instanceof ApiError
          ? ({
              current_password_invalid: t('settings.currentPasswordInvalid'),
              password_unchanged: t('settings.passwordUnchanged'),
              password_too_long: t('settings.passwordTooLong'),
              validation_failed: t('settings.passwordValidation'),
            }[error.code] ?? t('settings.passwordFailed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setPasswordBusy(false)
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('settings.eyebrow')}</p>
        <h1>{t('settings.title')}</h1>
      </div>

      {failed && (
        <div className="error" role="alert">
          {t('settings.failed')}
        </div>
      )}
      {saved && (
        <div className="success" role="status">
          {t('settings.saved')}
        </div>
      )}

      <section className="panel settings-panel settings-section">
        <h2>{t('settings.languageTitle')}</h2>
        <div className="field">
          <label htmlFor="ui-language">{t('language.label')}</label>
          <select
            id="ui-language"
            value={user?.language ?? 'it'}
            disabled={busy}
            onChange={(event) =>
              void handleLanguage(event.target.value as Language)
            }
          >
            <option value="it">{t('language.it')}</option>
            <option value="en">{t('language.en')}</option>
          </select>
          <p className="muted field-help">{t('settings.languageHelp')}</p>
        </div>
      </section>

      <form
        className="panel settings-panel settings-section"
        onSubmit={handlePassword}
      >
        <h2>{t('settings.passwordTitle')}</h2>
        <p className="muted field-help">{t('settings.passwordHelp')}</p>

        {passwordError && (
          <div className="error" role="alert">
            {passwordError}
          </div>
        )}

        <div className="field">
          <label htmlFor="current-password">
            {t('settings.currentPassword')}
          </label>
          <input
            id="current-password"
            type="password"
            autoComplete="current-password"
            maxLength={200}
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="new-password">{t('settings.newPassword')}</label>
          <input
            id="new-password"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={200}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="confirm-password">
            {t('settings.confirmPassword')}
          </label>
          <input
            id="confirm-password"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={200}
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            required
          />
        </div>
        <button type="submit" disabled={passwordBusy}>
          {passwordBusy
            ? t('settings.changingPassword')
            : t('settings.changePassword')}
        </button>
      </form>
    </Layout>
  )
}
