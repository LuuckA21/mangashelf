import { useState } from 'react'
import Layout from '../components/Layout'
import { useSession } from '../api/session'
import { useI18n, type Language } from '../i18n'

export default function Settings() {
  const { user, updateLanguage } = useSession()
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [failed, setFailed] = useState(false)

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

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('settings.eyebrow')}</p>
        <h1>{t('settings.title')}</h1>
      </div>

      {failed && <div className="error" role="alert">{t('settings.failed')}</div>}
      {saved && <div className="success" role="status">{t('settings.saved')}</div>}

      <section className="panel settings-panel">
        <div className="field">
          <label htmlFor="ui-language">{t('language.label')}</label>
          <select id="ui-language" value={user?.language ?? 'it'} disabled={busy}
                  onChange={(event) => void handleLanguage(event.target.value as Language)}>
            <option value="it">{t('language.it')}</option>
            <option value="en">{t('language.en')}</option>
          </select>
          <p className="muted field-help">{t('settings.languageHelp')}</p>
        </div>
      </section>
    </Layout>
  )
}
