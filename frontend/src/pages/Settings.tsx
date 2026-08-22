import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useSession } from '../api/session'
import Layout from '../components/Layout'
import { useI18n, type Language, type TranslationKey } from '../i18n'

type Notice = { kind: 'success' | 'error'; message: string } | null

export default function Settings() {
  const {
    user, updateLanguage, updateProfile, changePassword, deleteAccount,
  } = useSession()
  const { t } = useI18n()
  const [notice, setNotice] = useState<Notice>(null)

  const [languageBusy, setLanguageBusy] = useState(false)
  const [username, setUsername] = useState(user?.username ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [profilePassword, setProfilePassword] = useState('')
  const [profileBusy, setProfileBusy] = useState(false)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)

  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteBusy, setDeleteBusy] = useState(false)

  function errorMessage(error: unknown): string {
    if (!(error instanceof ApiError)) return t('settings.failed')
    const keys: Partial<Record<string, TranslationKey>> = {
      current_password_invalid: 'settings.currentPasswordInvalid',
      username_taken: 'register.usernameTaken',
      email_taken: 'register.emailTaken',
      password_too_long: 'register.passwordTooLong',
      validation_failed: 'settings.validationFailed',
      admin_account_delete_forbidden: 'settings.adminDeleteForbidden',
    }
    return t(keys[error.code] ?? 'settings.failed')
  }

  async function handleLanguage(language: Language) {
    setLanguageBusy(true)
    setNotice(null)
    try {
      await updateLanguage(language)
      setNotice({ kind: 'success', message: t('settings.languageSaved') })
    } catch (error) {
      setNotice({ kind: 'error', message: errorMessage(error) })
    } finally {
      setLanguageBusy(false)
    }
  }

  async function handleProfile(event: FormEvent) {
    event.preventDefault()
    setProfileBusy(true)
    setNotice(null)
    try {
      await updateProfile(username.trim(), email.trim(), profilePassword)
      setProfilePassword('')
      setNotice({ kind: 'success', message: t('settings.profileSaved') })
    } catch (error) {
      setNotice({ kind: 'error', message: errorMessage(error) })
    } finally {
      setProfileBusy(false)
    }
  }

  async function handlePassword(event: FormEvent) {
    event.preventDefault()
    setNotice(null)
    if (newPassword !== confirmPassword) {
      setNotice({ kind: 'error', message: t('settings.passwordMismatch') })
      return
    }

    setPasswordBusy(true)
    try {
      await changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setNotice({ kind: 'success', message: t('settings.passwordSaved') })
    } catch (error) {
      setNotice({ kind: 'error', message: errorMessage(error) })
    } finally {
      setPasswordBusy(false)
    }
  }

  async function handleDelete(event: FormEvent) {
    event.preventDefault()
    setDeleteBusy(true)
    setNotice(null)
    try {
      await deleteAccount(deletePassword)
    } catch (error) {
      setNotice({ kind: 'error', message: errorMessage(error) })
      setDeleteBusy(false)
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('settings.eyebrow')}</p>
        <h1>{t('settings.title')}</h1>
      </div>

      {notice && (
        <div className={notice.kind} role={notice.kind === 'error' ? 'alert' : 'status'}>
          {notice.message}
        </div>
      )}

      <div className="settings-stack">
        <section className="panel settings-panel" aria-labelledby="language-settings">
          <h2 id="language-settings">{t('settings.languageTitle')}</h2>
          <div className="field">
            <label htmlFor="ui-language">{t('language.label')}</label>
            <select
              id="ui-language"
              value={user?.language ?? 'it'}
              disabled={languageBusy}
              onChange={(event) => void handleLanguage(event.target.value as Language)}
            >
              <option value="it">{t('language.it')}</option>
              <option value="en">{t('language.en')}</option>
            </select>
            <p className="muted field-help">{t('settings.languageHelp')}</p>
          </div>
        </section>

        <form className="panel settings-panel" onSubmit={handleProfile}>
          <h2>{t('settings.profileTitle')}</h2>
          <div className="field">
            <label htmlFor="account-username">{t('register.username')}</label>
            <input
              id="account-username"
              value={username}
              minLength={3}
              maxLength={32}
              pattern="[a-zA-Z0-9_.-]+"
              required
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="account-email">{t('register.email')}</label>
            <input
              id="account-email"
              type="email"
              value={email}
              maxLength={255}
              required
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="profile-password">{t('settings.currentPassword')}</label>
            <input
              id="profile-password"
              type="password"
              value={profilePassword}
              required
              maxLength={200}
              autoComplete="current-password"
              onChange={(event) => setProfilePassword(event.target.value)}
            />
            <p className="muted field-help">{t('settings.profileHelp')}</p>
          </div>
          <button type="submit" disabled={profileBusy}>
            {profileBusy ? t('common.saving') : t('settings.saveProfile')}
          </button>
        </form>

        <form className="panel settings-panel" onSubmit={handlePassword}>
          <h2>{t('settings.passwordTitle')}</h2>
          <div className="field">
            <label htmlFor="current-password">{t('settings.currentPassword')}</label>
            <input
              id="current-password"
              type="password"
              value={currentPassword}
              required
              maxLength={200}
              autoComplete="current-password"
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="new-password">{t('settings.newPassword')}</label>
            <input
              id="new-password"
              type="password"
              value={newPassword}
              required
              minLength={10}
              maxLength={200}
              autoComplete="new-password"
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="confirm-password">{t('settings.confirmPassword')}</label>
            <input
              id="confirm-password"
              type="password"
              value={confirmPassword}
              required
              minLength={10}
              maxLength={200}
              autoComplete="new-password"
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
          </div>
          <button type="submit" disabled={passwordBusy}>
            {passwordBusy ? t('common.saving') : t('settings.changePassword')}
          </button>
        </form>

        {user?.role === 'ADMIN' ? (
          <section className="panel settings-panel danger-zone" aria-labelledby="delete-account">
            <h2 id="delete-account">{t('settings.deleteTitle')}</h2>
            <p className="muted">{t('settings.adminDeleteHelp')}</p>
          </section>
        ) : (
          <form className="panel settings-panel danger-zone" onSubmit={handleDelete}>
            <h2>{t('settings.deleteTitle')}</h2>
            <p>{t('settings.deleteHelp')}</p>
            <div className="field">
              <label htmlFor="delete-confirmation">{t('settings.typeUsername')}</label>
              <input
                id="delete-confirmation"
                value={deleteConfirmation}
                required
                autoComplete="off"
                onChange={(event) => setDeleteConfirmation(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="delete-password">{t('settings.currentPassword')}</label>
              <input
                id="delete-password"
                type="password"
                value={deletePassword}
                required
                maxLength={200}
                autoComplete="current-password"
                onChange={(event) => setDeletePassword(event.target.value)}
              />
            </div>
            <button
              type="submit"
              className="danger"
              disabled={deleteBusy || deleteConfirmation !== user?.username}
            >
              {deleteBusy ? t('common.deleting') : t('settings.deleteAction')}
            </button>
          </form>
        )}
      </div>
    </Layout>
  )
}
